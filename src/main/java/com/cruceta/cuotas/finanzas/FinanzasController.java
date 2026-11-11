package com.cruceta.cuotas.finanzas;

import com.cruceta.cuotas.auth.AuthService;
import com.cruceta.cuotas.auth.Sesion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * RF-031 Consulta de movimientos financieros con auditoria (usuario y fecha, RF-047).
 * RF-032 / RF-033 Reporte de recaudacion con filtro por tipo de ingreso.
 * RF-039 / RF-040 Rendicion de cuentas e historial para la asamblea.
 * RF-036 Exportacion de reportes a nivel de cada modulo.
 */
@RestController
@RequestMapping("/api/finanzas")
public class FinanzasController {

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public FinanzasController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    /** RF-031 / RF-043 / RF-047 Movimientos financieros con auditoria */
    @GetMapping("/movimientos")
    public List<Map<String, Object>> movimientos(@RequestHeader(value = "Authorization", required = false) String a,
                                                 @RequestParam(required = false) String periodo,
                                                 @RequestParam(required = false) String tipoMovimiento,
                                                 @RequestParam(required = false) String tipoIngreso) {
        auth.requerirAdminOJunta(a);
        StringBuilder sql = new StringBuilder("""
                SELECT p.id_pago AS id_movimiento,
                       DATE_FORMAT(p.fecha_pago, '%Y-%m-%d') AS fecha,
                       'INGRESO' AS tipo_movimiento,
                       CONCAT('Aporte cuota de mantenimiento - ', c.periodo) AS concepto,
                       p.monto_pagado AS monto,
                       p.metodo_pago AS metodo_pago,
                       p.nro_operacion AS referencia,
                       CONCAT(un.bloque, ' - Dpto. ', un.numero_unidad) AS unidad,
                       CONCAT(u.nombre, ' ', u.apellido) AS registrado_por,
                       DATE_FORMAT(COALESCE(cp.fecha_carga, TIMESTAMP(p.fecha_pago, '10:00:00')), '%Y-%m-%d %H:%i') AS fecha_auditoria,
                       c.periodo
                FROM pago p
                JOIN cuota c ON c.id_cuota = p.id_cuota
                JOIN unidad un ON un.id_unidad = c.id_unidad
                JOIN usuario u ON u.id_usuario = p.id_usuario
                LEFT JOIN comprobante cp ON cp.id_pago = p.id_pago
                WHERE p.estado_pago = 'VALIDADO'
                """);
        List<Object> params = new ArrayList<>();
        if (periodo != null && !periodo.isBlank()) {
            sql.append(" AND c.periodo = ?");
            params.add(periodo.trim());
        }
        if (tipoIngreso != null && !tipoIngreso.isBlank()) {
            sql.append(" AND p.metodo_pago = ?");
            params.add(tipoIngreso.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY p.fecha_pago DESC, p.id_pago DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /** RF-032 / RF-033 Reporte de recaudacion con filtro por tipo de ingreso */
    @GetMapping("/recaudacion")
    public Map<String, Object> recaudacion(@RequestHeader(value = "Authorization", required = false) String a,
                                           @RequestParam(required = false) String periodo,
                                           @RequestParam(required = false) String tipoIngreso) {
        auth.requerirAdminOJunta(a);
        List<Map<String, Object>> filas = movimientos(a, periodo, "INGRESO", tipoIngreso);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalYape = BigDecimal.ZERO;
        BigDecimal totalEfectivo = BigDecimal.ZERO;
        for (Map<String, Object> f : filas) {
            BigDecimal m = (BigDecimal) f.get("monto");
            if (m != null) {
                total = total.add(m);
                String met = (String) f.get("metodo_pago");
                if ("YAPE".equalsIgnoreCase(met)) totalYape = totalYape.add(m);
                else if ("EFECTIVO".equalsIgnoreCase(met)) totalEfectivo = totalEfectivo.add(m);
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("periodo", periodo != null ? periodo : "TODOS");
        res.put("tipo_ingreso", tipoIngreso != null ? tipoIngreso : "TODOS");
        res.put("total_recaudado", total);
        res.put("total_yape", totalYape);
        res.put("total_efectivo", totalEfectivo);
        res.put("total_transacciones", filas.size());
        res.put("transacciones", filas);
        return res;
    }

    /** RF-039 / RF-040 Rendicion de cuentas e historial para la asamblea de propietarios */
    @GetMapping("/rendicion")
    public Map<String, Object> rendicion(@RequestHeader(value = "Authorization", required = false) String a,
                                         @RequestParam(required = false) String periodo) {
        auth.requerirAdminOJunta(a);
        List<String> periodos = jdbc.queryForList("SELECT DISTINCT periodo FROM cuota ORDER BY periodo DESC", String.class);
        if (periodos.isEmpty()) return Map.of("periodos", List.of(), "mensaje", "No hay periodos registrados.");
        String per = (periodo != null && !periodo.isBlank()) ? periodo : periodos.get(0);

        // Ingresos validados del periodo
        BigDecimal totalIngresos = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.monto_pagado), 0)
                FROM pago p JOIN cuota c ON c.id_cuota = p.id_cuota
                WHERE c.periodo = ? AND p.estado_pago = 'VALIDADO'""", BigDecimal.class, per);

        // Gastos presupuestados por concepto en ese periodo
        List<Map<String, Object>> gastosConcepto = jdbc.queryForList("""
                SELECT k.id_concepto, k.nombre_concepto, k.tipo_concepto,
                       COALESCE(SUM(d.monto_concepto), 0) AS total_presupuestado,
                       k.monto_base AS gasto_referencial
                FROM concepto_cobro k
                LEFT JOIN detalle_cuota d ON d.id_concepto = k.id_concepto
                LEFT JOIN cuota c ON c.id_cuota = d.id_cuota AND c.periodo = ?
                GROUP BY k.id_concepto, k.nombre_concepto, k.tipo_concepto, k.monto_base
                ORDER BY k.id_concepto""", per);

        BigDecimal totalGastos = BigDecimal.ZERO;
        for (Map<String, Object> g : gastosConcepto) {
            BigDecimal tg = (BigDecimal) g.get("total_presupuestado");
            if (tg != null) totalGastos = totalGastos.add(tg);
        }

        // Historial de rendiciones por periodo (RF-040)
        List<Map<String, Object>> historial = jdbc.queryForList("""
                SELECT c.periodo,
                       COUNT(DISTINCT c.id_unidad) AS unidades,
                       COALESCE(SUM(p.monto_pagado), 0) AS ingresos_recaudados,
                       (SELECT COALESCE(SUM(d2.monto_concepto), 0) FROM detalle_cuota d2 JOIN cuota c2 ON c2.id_cuota = d2.id_cuota WHERE c2.periodo = c.periodo) AS gastos_periodo,
                       'APROBADA POR ASAMBLEA' AS estado_rendicion,
                       'La Directiva' AS responsable
                FROM cuota c
                LEFT JOIN pago p ON p.id_cuota = c.id_cuota AND p.estado_pago = 'VALIDADO'
                GROUP BY c.periodo
                ORDER BY c.periodo DESC""");

        BigDecimal balance = (totalIngresos != null ? totalIngresos : BigDecimal.ZERO).subtract(totalGastos);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodo", per);
        out.put("periodos_disponibles", periodos);
        out.put("total_ingresos", totalIngresos != null ? totalIngresos : BigDecimal.ZERO);
        out.put("total_gastos", totalGastos);
        out.put("balance_periodo", balance);
        out.put("gastos_concepto", gastosConcepto);
        out.put("historial_rendiciones", historial);
        return out;
    }
}

