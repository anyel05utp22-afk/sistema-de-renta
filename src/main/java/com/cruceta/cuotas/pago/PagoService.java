package com.cruceta.cuotas.pago;

import com.cruceta.cuotas.auth.Sesion;
import com.cruceta.cuotas.comun.ApiException;
import com.cruceta.cuotas.comun.Datos;
import com.cruceta.cuotas.cuota.CuotaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

/**
 * RF-016 Registrar pago, RF-017 Cargar comprobante, RF-018 Consultar pagos,
 * RF-019 Validar comprobante, RF-020 Rechazar comprobante,
 * RF-021 Emitir constancia (recibo), RF-022 Consultar constancias,
 * RF-048 Historial de pagos por unidad, RF-049 Consultar comprobante.
 */
@Service
public class PagoService {

    private static final Set<String> FORMATOS = Set.of("pdf", "jpg", "jpeg", "png");

    private static final String VISTA_PAGO = """
            SELECT p.id_pago, p.id_cuota, c.periodo, c.id_unidad,
                   CONCAT(un.bloque, ' - ', un.numero_unidad) AS unidad,
                   CONCAT(u.nombre, ' ', u.apellido) AS usuario,
                   DATE_FORMAT(p.fecha_pago, '%Y-%m-%d') AS fecha_pago, p.monto_pagado,
                   p.metodo_pago, p.nro_operacion, p.estado_pago,
                   cp.id_comprobante, cp.archivo, cp.estado_validacion, cp.observacion,
                   r.id_recibo, r.numero_recibo
            FROM pago p
            JOIN cuota c   ON c.id_cuota = p.id_cuota
            JOIN unidad un ON un.id_unidad = c.id_unidad
            JOIN usuario u ON u.id_usuario = p.id_usuario
            LEFT JOIN comprobante cp ON cp.id_comprobante =
                 (SELECT MAX(x.id_comprobante) FROM comprobante x WHERE x.id_pago = p.id_pago)
            LEFT JOIN recibo r ON r.id_pago = p.id_pago
            """;

    private final JdbcTemplate jdbc;
    private final CuotaService cuotas;
    private final Path uploads;

    public PagoService(JdbcTemplate jdbc, CuotaService cuotas, @Value("${app.uploads-dir}") String uploadsDir) {
        this.jdbc = jdbc;
        this.cuotas = cuotas;
        this.uploads = Paths.get(uploadsDir).toAbsolutePath();
    }

    public List<Map<String, Object>> listar(Sesion s, String estado, Integer idUnidad) {
        StringBuilder sql = new StringBuilder(VISTA_PAGO + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!s.esAdmin()) idUnidad = s.idUnidad() == null ? -1 : s.idUnidad();
        if (idUnidad != null) { sql.append(" AND c.id_unidad = ?"); params.add(idUnidad); }
        if (estado != null && !estado.isBlank()) { sql.append(" AND p.estado_pago = ?"); params.add(estado); }
        sql.append(" ORDER BY p.fecha_pago DESC, p.id_pago DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    // ===================== RF-016 / RF-017 =====================
    @Transactional
    public Map<String, Object> registrar(Sesion s, Integer idCuota, String montoTxt, String metodo,
                                         String nroOperacion, String fechaTxt, MultipartFile archivo) {
        if (idCuota == null) throw ApiException.validacion("Seleccione la cuota que desea pagar.");
        Map<String, Object> cuota = cuotas.obtener(s, idCuota); // valida existencia y pertenencia
        if ("PAGADA".equals(cuota.get("estado_cuota")))
            throw ApiException.validacion("La cuota seleccionada ya se encuentra pagada.");

        BigDecimal monto = Datos.monto(montoTxt, "Monto pagado");
        // Flujo alternativo Escenario 2: monto invalido
        if (monto.signum() <= 0) throw ApiException.validacion("El monto no puede ser cero ni negativo. Operacion rechazada.");
        BigDecimal disponible = ((BigDecimal) cuota.get("saldo")).subtract((BigDecimal) cuota.get("monto_en_revision"));
        if (monto.compareTo(disponible) > 0)
            throw ApiException.validacion("El monto (S/. " + monto + ") supera el saldo pendiente de la cuota (S/. " + disponible + ").");

        LocalDate fecha = Datos.fecha(fechaTxt, "del pago");
        if (fecha.isAfter(LocalDate.now())) throw ApiException.validacion("La fecha del pago no puede ser futura.");

        // Escenario 3: el metodo de pago es opcional (solo se acepta YAPE o EFECTIVO)
        metodo = metodo == null || metodo.isBlank() ? null : metodo.trim().toUpperCase(Locale.ROOT);
        if (metodo != null && !"YAPE".equals(metodo) && !"EFECTIVO".equals(metodo))
            throw ApiException.validacion("Metodo de pago no permitido. Solo se acepta YAPE o EFECTIVO.");
        nroOperacion = nroOperacion == null ? "" : nroOperacion.trim();
        if ("YAPE".equals(metodo) && nroOperacion.isEmpty())
            throw ApiException.validacion("Ingrese el numero de operacion / voucher de Yape.");
        if (!nroOperacion.isEmpty()) {
            Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE nro_operacion = ? AND estado_pago <> 'RECHAZADO'",
                    Integer.class, nroOperacion);
            if (dup != null && dup > 0) throw ApiException.validacion("El numero de operacion " + nroOperacion + " ya fue registrado.");
        }

        boolean tieneArchivo = archivo != null && !archivo.isEmpty();
        // Escenario 2: falta el comprobante requerido (obligatorio para el propietario)
        if (!s.esAdmin() && !tieneArchivo)
            throw ApiException.validacion("Falta el comprobante requerido para validar la transaccion.");
        String ext = tieneArchivo ? extension(archivo.getOriginalFilename()) : null;
        if (tieneArchivo && !FORMATOS.contains(ext))
            throw ApiException.validacion("El archivo no cumple los formatos permitidos (PDF, JPG o PNG). Se rechazo la carga.");

        // El administrador registra pagos recibidos en oficina -> quedan validados directamente
        String estado = s.esAdmin() ? "VALIDADO" : "EN_REVISION";
        jdbc.update("""
                INSERT INTO pago (id_cuota, id_usuario, fecha_pago, monto_pagado, metodo_pago, nro_operacion, estado_pago)
                VALUES (?,?,?,?,?,?,?)""",
                idCuota, s.esAdmin() ? propietarioDe((Integer) cuota.get("id_unidad"), s.idUsuario()) : s.idUsuario(),
                fecha, monto, metodo, nroOperacion.isEmpty() ? null : nroOperacion, estado);
        Integer idPago = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        if (tieneArchivo) guardarComprobante(idPago, archivo, ext, s.esAdmin() ? "VALIDADO" : "PENDIENTE");

        String numeroRecibo = null;
        if (s.esAdmin()) {
            numeroRecibo = emitirRecibo(idPago, "Pago registrado por la administracion");
            cuotas.actualizarEstado(idCuota);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id_pago", idPago);
        r.put("estado_pago", estado);
        r.put("numero_recibo", numeroRecibo);
        r.put("mensaje", s.esAdmin()
                ? "Pago registrado y validado. Constancia emitida: " + numeroRecibo
                : "Pago registrado correctamente. Queda EN REVISION hasta que la administracion valide el comprobante.");
        return r;
    }

    // ===================== RF-019 Validar comprobante =====================
    @Transactional
    public Map<String, Object> validar(int idPago) {
        Map<String, Object> p = pagoEnRevision(idPago);
        jdbc.update("UPDATE pago SET estado_pago = 'VALIDADO' WHERE id_pago = ?", idPago);
        jdbc.update("UPDATE comprobante SET estado_validacion = 'VALIDADO', observacion = NULL WHERE id_pago = ?", idPago);
        String numero = emitirRecibo(idPago, "Pago validado por la administracion");
        cuotas.actualizarEstado(((Number) p.get("id_cuota")).intValue());
        return Map.of("mensaje", "Comprobante validado. Se emitio la constancia de pago " + numero + ".", "numero_recibo", numero);
    }

    // ===================== RF-020 Rechazar comprobante =====================
    @Transactional
    public Map<String, Object> rechazar(int idPago, String motivo) {
        if (motivo == null || motivo.isBlank())
            throw ApiException.validacion("Debe registrar el motivo del rechazo.");
        Map<String, Object> p = pagoEnRevision(idPago);
        jdbc.update("UPDATE pago SET estado_pago = 'RECHAZADO' WHERE id_pago = ?", idPago);
        jdbc.update("UPDATE comprobante SET estado_validacion = 'RECHAZADO', observacion = ? WHERE id_pago = ?", motivo.trim(), idPago);
        cuotas.actualizarEstado(((Number) p.get("id_cuota")).intValue());
        return Map.of("mensaje", "Comprobante rechazado. El propietario podra registrar nuevamente su pago.");
    }

    // ===================== RF-021 / RF-022 Constancias =====================
    private String emitirRecibo(int idPago, String observacion) {
        String numero = String.format("%06d", 4000 + idPago);
        jdbc.update("INSERT IGNORE INTO recibo (numero_recibo, fecha_emision, id_pago, observacion) VALUES (?, CURDATE(), ?, ?)",
                numero, idPago, observacion);
        return jdbc.queryForObject("SELECT numero_recibo FROM recibo WHERE id_pago = ?", String.class, idPago);
    }

    public List<Map<String, Object>> recibos(Sesion s) {
        String sql = """
                SELECT r.id_recibo, r.numero_recibo, DATE_FORMAT(r.fecha_emision, '%Y-%m-%d') AS fecha_emision, r.observacion,
                       v.id_pago, v.periodo, v.unidad, v.usuario, v.monto_pagado, v.metodo_pago, v.nro_operacion, v.fecha_pago, v.id_unidad
                FROM recibo r JOIN (""" + VISTA_PAGO + """
                ) v ON v.id_pago = r.id_pago""";
        if (s.esAdmin()) return jdbc.queryForList(sql + " ORDER BY r.id_recibo DESC");
        return jdbc.queryForList(sql + " WHERE v.id_unidad = ? ORDER BY r.id_recibo DESC", s.idUnidad());
    }

    // ===================== RF-049 Consultar comprobante =====================
    public Path archivoComprobante(Sesion s, int idComprobante) {
        List<Map<String, Object>> l = jdbc.queryForList("""
                SELECT cp.archivo, c.id_unidad FROM comprobante cp
                JOIN pago p ON p.id_pago = cp.id_pago JOIN cuota c ON c.id_cuota = p.id_cuota
                WHERE cp.id_comprobante = ?""", idComprobante);
        if (l.isEmpty()) throw ApiException.noEncontrado("Comprobante no encontrado.");
        if (!s.esAdmin() && !Objects.equals(l.get(0).get("id_unidad"), s.idUnidad()))
            throw ApiException.prohibido("No puede ver comprobantes de otro departamento.");
        Path f = uploads.resolve((String) l.get(0).get("archivo")).normalize();
        if (!f.startsWith(uploads) || !Files.exists(f))
            throw ApiException.noEncontrado("El archivo del comprobante no esta disponible.");
        return f;
    }

    // ---------------------------------------------------------------
    private Map<String, Object> pagoEnRevision(int idPago) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT id_pago, id_cuota, estado_pago FROM pago WHERE id_pago = ?", idPago);
        if (l.isEmpty()) throw ApiException.noEncontrado("Pago no encontrado.");
        if (!"EN_REVISION".equals(l.get(0).get("estado_pago")))
            throw ApiException.validacion("El pago ya fue procesado (estado: " + l.get(0).get("estado_pago") + ").");
        return l.get(0);
    }

    private int propietarioDe(Integer idUnidad, int porDefecto) {
        List<Integer> l = jdbc.queryForList(
                "SELECT id_usuario FROM usuario WHERE id_unidad = ? AND id_rol = 2 AND estado_cuenta = 'ACTIVO' LIMIT 1",
                Integer.class, idUnidad);
        return l.isEmpty() ? porDefecto : l.get(0);
    }

    private void guardarComprobante(int idPago, MultipartFile archivo, String ext, String estado) {
        try {
            Path dir = uploads.resolve("comprobantes");
            Files.createDirectories(dir);
            String nombre = "comprobantes/pago_" + idPago + "_" + System.currentTimeMillis() + "." + ext;
            archivo.transferTo(uploads.resolve(nombre));
            jdbc.update("INSERT INTO comprobante (id_pago, archivo, fecha_carga, estado_validacion) VALUES (?,?,NOW(),?)",
                    idPago, nombre, estado);
        } catch (IOException e) {
            throw ApiException.validacion("No se pudo guardar el archivo del comprobante. Intente nuevamente.");
        }
    }

    private static String extension(String nombre) {
        if (nombre == null || !nombre.contains(".")) return "";
        return nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
