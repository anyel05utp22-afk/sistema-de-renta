package com.cruceta.cuotas.cuota;

import com.cruceta.cuotas.auth.Sesion;
import com.cruceta.cuotas.comun.ApiException;
import com.cruceta.cuotas.comun.Datos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Logica de cuotas de mantenimiento.
 * Cada mes el administrador registra cuanto gasto el edificio por concepto
 * (vigilancia, limpieza, agua, luz...) y ese gasto se reparte entre los
 * departamentos activos -> se genera una CUOTA por departamento con su DETALLE_CUOTA.
 */
@Service
public class CuotaService {

    /** Vista de cuota con totales calculados (monto_total = suma del detalle). */
    static final String VISTA_CUOTA = """
            SELECT x.*, (x.monto_total - x.monto_pagado) AS saldo FROM (
              SELECT c.id_cuota, c.id_unidad, CONCAT(un.bloque, ' - ', un.numero_unidad) AS unidad, c.periodo,
                     DATE_FORMAT(c.fecha_emision, '%Y-%m-%d') AS fecha_emision,
                     DATE_FORMAT(c.fecha_vencimiento, '%Y-%m-%d') AS fecha_vencimiento,
                     c.estado_cuota,
                     (SELECT CONCAT(u.nombre, ' ', u.apellido) FROM usuario u
                       WHERE u.id_unidad = c.id_unidad AND u.id_rol = 2 AND u.estado_cuenta = 'ACTIVO' LIMIT 1) AS propietario,
                     COALESCE((SELECT SUM(d.monto_concepto) FROM detalle_cuota d WHERE d.id_cuota = c.id_cuota), 0) AS monto_total,
                     COALESCE((SELECT SUM(p.monto_pagado) FROM pago p WHERE p.id_cuota = c.id_cuota AND p.estado_pago = 'VALIDADO'), 0) AS monto_pagado,
                     COALESCE((SELECT SUM(p.monto_pagado) FROM pago p WHERE p.id_cuota = c.id_cuota AND p.estado_pago = 'EN_REVISION'), 0) AS monto_en_revision
              FROM cuota c JOIN unidad un ON un.id_unidad = c.id_unidad
            ) x
            """;

    private final JdbcTemplate jdbc;

    public CuotaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ===================== RF-023 Detectar cuota vencida =====================
    /** Marca como VENCIDA las cuotas pendientes cuya fecha vencio y no tienen pago validado ni en revision. */
    public int detectarVencidas() {
        return jdbc.update("""
                UPDATE cuota c SET c.estado_cuota = 'VENCIDA'
                WHERE c.estado_cuota = 'PENDIENTE' AND c.fecha_vencimiento < CURDATE()
                  AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.id_cuota = c.id_cuota
                                  AND p.estado_pago IN ('VALIDADO', 'EN_REVISION'))""");
    }

    /** Recalcula el estado de la cuota segun lo pagado (se usa al validar/rechazar pagos). */
    public void actualizarEstado(int idCuota) {
        Map<String, Object> c = jdbc.queryForMap(VISTA_CUOTA + " WHERE x.id_cuota = ?", idCuota);
        BigDecimal saldo = (BigDecimal) c.get("saldo");
        String estado;
        if (saldo.signum() <= 0) estado = "PAGADA";
        else if (LocalDate.parse((String) c.get("fecha_vencimiento")).isBefore(LocalDate.now())
                && ((BigDecimal) c.get("monto_en_revision")).signum() == 0) estado = "VENCIDA";
        else estado = "PENDIENTE";
        jdbc.update("UPDATE cuota SET estado_cuota = ? WHERE id_cuota = ?", estado, idCuota);
    }

    // ===================== RF-012 Consultar cuota =====================
    public List<Map<String, Object>> listar(Sesion s, String periodo, String estado, Integer idUnidad) {
        detectarVencidas();
        StringBuilder sql = new StringBuilder(VISTA_CUOTA + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!s.esAdmin()) idUnidad = s.idUnidad() == null ? -1 : s.idUnidad();
        if (idUnidad != null) { sql.append(" AND x.id_unidad = ?"); params.add(idUnidad); }
        if (periodo != null && !periodo.isBlank()) { sql.append(" AND x.periodo = ?"); params.add(periodo); }
        if (estado != null && !estado.isBlank()) { sql.append(" AND x.estado_cuota = ?"); params.add(estado); }
        sql.append(" ORDER BY x.periodo DESC, x.unidad");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> obtener(Sesion s, int idCuota) {
        List<Map<String, Object>> l = jdbc.queryForList(VISTA_CUOTA + " WHERE x.id_cuota = ?", idCuota);
        if (l.isEmpty()) throw ApiException.noEncontrado("La cuota solicitada no existe.");
        Map<String, Object> cuota = new LinkedHashMap<>(l.get(0));
        if (!s.esAdmin() && !Objects.equals(cuota.get("id_unidad"), s.idUnidad()))
            throw ApiException.prohibido("No puede consultar cuotas de otro departamento.");
        cuota.put("detalles", jdbc.queryForList("""
                SELECT d.id_concepto, k.nombre_concepto, k.tipo_concepto, d.monto_concepto
                FROM detalle_cuota d JOIN concepto_cobro k ON k.id_concepto = d.id_concepto
                WHERE d.id_cuota = ? ORDER BY d.id_concepto""", idCuota));
        cuota.put("pagos", jdbc.queryForList("""
                SELECT p.id_pago, DATE_FORMAT(p.fecha_pago, '%Y-%m-%d') AS fecha_pago, p.monto_pagado,
                       p.metodo_pago, p.nro_operacion, p.estado_pago
                FROM pago p WHERE p.id_cuota = ? ORDER BY p.fecha_pago""", idCuota));
        return cuota;
    }

    public List<String> periodos() {
        return jdbc.queryForList("SELECT DISTINCT periodo FROM cuota ORDER BY periodo DESC", String.class);
    }

    private static final Set<String> EXONERADOS_VIGILANCIA = Set.of("101", "301", "401", "402", "502");

    private boolean esVigilancia(String nombreConcepto) {
        return nombreConcepto != null && nombreConcepto.toLowerCase(Locale.ROOT).contains("vigilancia");
    }

    // ===================== RF-011 Calcular monto de cuota =====================
    public Map<String, Object> calcular(List<Map<String, Object>> gastos) {
        int unidades = unidadesActivas();
        if (unidades == 0) throw ApiException.validacion("No hay departamentos activos para repartir el gasto.");
        if (gastos == null || gastos.isEmpty())
            throw ApiException.validacion("Ingrese el gasto del mes de al menos un concepto.");

        List<Map<String, Object>> detalle = new ArrayList<>();
        BigDecimal totalEdificio = BigDecimal.ZERO, generalPorUnidad = BigDecimal.ZERO, vigilanciaPorUnidad = BigDecimal.ZERO;
        for (Map<String, Object> g : gastos) {
            Integer idConcepto = Datos.entero(g.get("id_concepto"));
            String nombre = jdbc.queryForObject("SELECT nombre_concepto FROM concepto_cobro WHERE id_concepto = ?", String.class, idConcepto);
            // Flujo alternativo: "si falta un valor necesario para el calculo, informa el dato faltante"
            BigDecimal gasto = Datos.monto(g.get("monto"), nombre);
            if (gasto.signum() < 0) throw ApiException.validacion("El gasto de " + nombre + " no puede ser negativo.");
            boolean vig = esVigilancia(nombre);
            int divisor = vig ? Math.max(1, unidades - EXONERADOS_VIGILANCIA.size()) : unidades;
            BigDecimal parte = gasto.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
            totalEdificio = totalEdificio.add(gasto);
            if (vig) vigilanciaPorUnidad = vigilanciaPorUnidad.add(parte);
            else generalPorUnidad = generalPorUnidad.add(parte);
            detalle.add(Map.of("id_concepto", idConcepto, "nombre_concepto", nombre, "gasto", gasto,
                    "por_unidad", parte, "es_vigilancia", vig));
        }
        if (totalEdificio.signum() == 0) throw ApiException.validacion("El gasto total del mes no puede ser cero.");
        // Redondeo al sol entero como en la hoja de compromisos (ej. S/. 104.70 -> S/. 105.00 + S/. 10.00 = S/. 115.00)
        BigDecimal baseRedondeada = generalPorUnidad.setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.HALF_UP);
        BigDecimal conVigilancia = baseRedondeada.add(vigilanciaPorUnidad);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("unidades", unidades);
        r.put("total_edificio", totalEdificio);
        r.put("monto_sin_vigilancia", baseRedondeada);
        r.put("monto_por_unidad", conVigilancia);
        r.put("detalle", detalle);
        return r;
    }

    // ===================== RF-010 Generar cuota mensual =====================
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> generar(String periodo, String fechaVenc, List<Map<String, Object>> gastos) {
        if (periodo == null || !periodo.matches("\\d{4}-(0[1-9]|1[0-2])"))
            throw ApiException.validacion("Seleccione un periodo de cobro valido.");
        LocalDate venc = Datos.fecha(fechaVenc, "de vencimiento");
        if (venc.isBefore(LocalDate.parse(periodo + "-01")))
            throw ApiException.validacion("La fecha de vencimiento no puede ser anterior al inicio del periodo.");

        Map<String, Object> calculo = calcular(gastos);
        List<Map<String, Object>> unidades = jdbc.queryForList("SELECT id_unidad, numero_unidad FROM unidad WHERE estado = 'ACTIVA'");
        List<Map<String, Object>> detalle = (List<Map<String, Object>>) calculo.get("detalle");

        // Ajuste de centimos para que la suma de conceptos generales cuadre con la base redondeada (ej. S/. 105.00)
        BigDecimal sumaGen = detalle.stream().filter(d -> !Boolean.TRUE.equals(d.get("es_vigilancia")))
                .map(d -> (BigDecimal) d.get("por_unidad")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diferencia = ((BigDecimal) calculo.get("monto_sin_vigilancia")).subtract(sumaGen);

        int generadas = 0, omitidas = 0;
        for (Map<String, Object> u : unidades) {
            Integer idUnidad = ((Number) u.get("id_unidad")).intValue();
            String numUnidad = String.valueOf(u.get("numero_unidad"));
            boolean exoneradoVig = EXONERADOS_VIGILANCIA.contains(numUnidad);
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE id_unidad = ? AND periodo = ?",
                    Integer.class, idUnidad, periodo);
            if (existe != null && existe > 0) { omitidas++; continue; } // evita duplicar
            jdbc.update("INSERT INTO cuota (id_unidad, periodo, fecha_emision, fecha_vencimiento, estado_cuota) VALUES (?,?,CURDATE(),?, 'PENDIENTE')",
                    idUnidad, periodo, venc);
            Integer idCuota = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
            boolean ajusteAplicado = false;
            for (int i = 0; i < detalle.size(); i++) {
                Map<String, Object> d = detalle.get(i);
                boolean vig = Boolean.TRUE.equals(d.get("es_vigilancia"));
                BigDecimal monto = (BigDecimal) d.get("por_unidad");
                if (vig && exoneradoVig) {
                    monto = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                } else if (!vig && !ajusteAplicado && i == detalle.size() - 2) {
                    monto = monto.add(diferencia);
                    ajusteAplicado = true;
                }
                jdbc.update("INSERT INTO detalle_cuota (id_cuota, id_concepto, monto_concepto) VALUES (?,?,?)",
                        idCuota, d.get("id_concepto"), monto);
            }
            generadas++;
        }
        // Flujo alternativo: "si una unidad ya tiene una cuota para el periodo, el sistema evita duplicarla"
        if (generadas == 0)
            throw ApiException.validacion("Todas las unidades ya tienen una cuota generada para el periodo " + periodo + ".");

        Map<String, Object> r = new LinkedHashMap<>(calculo);
        r.put("generadas", generadas);
        r.put("omitidas", omitidas);
        r.put("mensaje", "Se generaron " + generadas + " cuota(s) para el periodo " + periodo
                + " (S/. " + calculo.get("monto_por_unidad") + " con vigilancia / S/. " + calculo.get("monto_sin_vigilancia")
                + " sin vigilancia para dptos. 101, 301, 401, 402 y 502).");
        return r;
    }

    // ===================== RF-015 Editar cuota =====================
    @Transactional
    public Map<String, Object> editar(int idCuota, String fechaVenc, List<Map<String, Object>> detalles) {
        Map<String, Object> c = jdbc.queryForMap(VISTA_CUOTA + " WHERE x.id_cuota = ?", idCuota);
        // Flujo alternativo: "si la cuota ya esta cerrada o pagada, se restringe la edicion"
        if ("PAGADA".equals(c.get("estado_cuota")) || ((BigDecimal) c.get("monto_pagado")).signum() > 0)
            throw ApiException.validacion("La cuota ya esta pagada (o tiene pagos validados) y no se puede editar.");
        LocalDate venc = Datos.fecha(fechaVenc, "de vencimiento");
        jdbc.update("UPDATE cuota SET fecha_vencimiento = ? WHERE id_cuota = ?", venc, idCuota);
        if (detalles != null) {
            for (Map<String, Object> d : detalles) {
                BigDecimal m = Datos.monto(d.get("monto_concepto"), String.valueOf(d.get("nombre_concepto")));
                if (m.signum() < 0) throw ApiException.validacion("Los montos no pueden ser negativos.");
                jdbc.update("UPDATE detalle_cuota SET monto_concepto = ? WHERE id_cuota = ? AND id_concepto = ?",
                        m, idCuota, Datos.entero(d.get("id_concepto")));
            }
        }
        actualizarEstado(idCuota);
        return Map.of("mensaje", "Cuota actualizada correctamente.");
    }

    // ===================== RF-013 Consultar estado de cuenta =====================
    public Map<String, Object> estadoCuenta(Sesion s, Integer idUnidad) {
        if (!s.esAdmin()) idUnidad = s.idUnidad();
        if (idUnidad == null) throw ApiException.validacion("Seleccione un departamento.");
        detectarVencidas();
        Map<String, Object> r = new LinkedHashMap<>(jdbc.queryForMap("""
                SELECT un.id_unidad, CONCAT(un.bloque, ' - ', un.numero_unidad) AS unidad,
                       (SELECT CONCAT(u.nombre, ' ', u.apellido) FROM usuario u
                         WHERE u.id_unidad = un.id_unidad AND u.id_rol = 2 AND u.estado_cuenta = 'ACTIVO' LIMIT 1) AS propietario
                FROM unidad un WHERE un.id_unidad = ?""", idUnidad));
        List<Map<String, Object>> cuotas = jdbc.queryForList(VISTA_CUOTA + " WHERE x.id_unidad = ? ORDER BY x.periodo", idUnidad);
        BigDecimal cargos = BigDecimal.ZERO, abonos = BigDecimal.ZERO;
        for (Map<String, Object> c : cuotas) {
            cargos = cargos.add((BigDecimal) c.get("monto_total"));
            abonos = abonos.add((BigDecimal) c.get("monto_pagado"));
        }
        r.put("cuotas", cuotas);
        r.put("total_cargos", cargos);
        r.put("total_pagado", abonos);
        r.put("saldo_pendiente", cargos.subtract(abonos));
        return r;
    }

    // ===================== RF-024 Consultar morosidad =====================
    public List<Map<String, Object>> morosidad() {
        detectarVencidas();
        return jdbc.queryForList("""
                SELECT v.*, DATEDIFF(CURDATE(), v.fecha_vencimiento) AS dias_atraso,
                       (SELECT u.telefono FROM usuario u WHERE u.id_unidad = v.id_unidad AND u.id_rol = 2 LIMIT 1) AS telefono
                FROM (""" + VISTA_CUOTA + """
                ) v WHERE v.estado_cuota = 'VENCIDA' ORDER BY v.unidad, v.periodo""");
    }

    // ===================== RF-041 Dashboard administrativo =====================
    public Map<String, Object> dashboard(String periodo) {
        detectarVencidas();
        if (periodo == null || periodo.isBlank()) {
            List<String> p = periodos();
            periodo = p.isEmpty() ? "" : p.get(0);
        }
        Map<String, Object> r = new LinkedHashMap<>(jdbc.queryForMap("""
                SELECT COUNT(*) AS cuotas,
                       COALESCE(SUM(monto_total), 0) AS total_emitido,
                       COALESCE(SUM(monto_pagado), 0) AS total_recaudado,
                       COALESCE(SUM(saldo), 0) AS total_pendiente,
                       COALESCE(SUM(estado_cuota = 'PAGADA'), 0) AS pagadas,
                       COALESCE(SUM(estado_cuota = 'PENDIENTE'), 0) AS pendientes
                FROM (""" + VISTA_CUOTA + ") v WHERE v.periodo = ?", periodo));
        r.put("periodo", periodo);
        r.putAll(jdbc.queryForMap("""
                SELECT COUNT(DISTINCT id_unidad) AS unidades_morosas, COUNT(*) AS cuotas_vencidas,
                       COALESCE(SUM(saldo), 0) AS deuda_vencida
                FROM (""" + VISTA_CUOTA + ") v WHERE v.estado_cuota = 'VENCIDA'"));
        r.put("pagos_en_revision", jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE estado_pago = 'EN_REVISION'", Integer.class));
        r.put("unidades", unidadesActivas());
        return r;
    }

    private int unidadesActivas() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM unidad WHERE estado = 'ACTIVA'", Integer.class);
        return n == null ? 0 : n;
    }
}
