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
                   CONCAT(un.bloque, ' - Dpto. ', un.numero_unidad) AS unidad,
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
        if (!s.tieneAccesoGestion()) idUnidad = s.idUnidad() == null ? -1 : s.idUnidad();
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

    // ===================== RF-019 Aprobar comprobante =====================
    @Transactional
    public Map<String, Object> validar(int idPago) {
        Map<String, Object> p = pagoEnRevision(idPago);
        jdbc.update("UPDATE pago SET estado_pago = 'VALIDADO' WHERE id_pago = ?", idPago);
        jdbc.update("UPDATE comprobante SET estado_validacion = 'VALIDADO', observacion = NULL WHERE id_pago = ?", idPago);
        String numero = emitirRecibo(idPago, "Pago aprobado por la administracion");
        cuotas.actualizarEstado(((Number) p.get("id_cuota")).intValue());
        return Map.of("mensaje", "Comprobante aprobado exitosamente. El sistema emitio la constancia de pago N° " + numero + ".", "numero_recibo", numero);
    }

    // ===================== Anular pago =====================
    @Transactional
    public Map<String, Object> anular(int idPago, String motivo) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT id_pago, id_cuota, estado_pago FROM pago WHERE id_pago = ?", idPago);
        if (filas.isEmpty()) throw ApiException.noEncontrado("Pago no encontrado.");
        Map<String, Object> p = filas.get(0);
        if ("ANULADO".equals(p.get("estado_pago"))) {
            throw ApiException.validacion("El pago ya se encuentra anulado.");
        }
        String obs = (motivo == null || motivo.isBlank()) ? "Pago anulado por la administracion" : motivo.trim();
        jdbc.update("UPDATE pago SET estado_pago = 'ANULADO' WHERE id_pago = ?", idPago);
        jdbc.update("UPDATE comprobante SET estado_validacion = 'ANULADO', observacion = ? WHERE id_pago = ?", obs, idPago);
        jdbc.update("UPDATE recibo SET observacion = CONCAT('[ANULADO] ', ?) WHERE id_pago = ?", obs, idPago);

        int idCuota = ((Number) p.get("id_cuota")).intValue();
        cuotas.actualizarEstado(idCuota);
        return Map.of("mensaje", "Pago #" + idPago + " anulado correctamente. La cuota ha sido actualizada.");
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
        if (s.tieneAccesoGestion()) return jdbc.queryForList(sql + " ORDER BY r.id_recibo DESC");
        return jdbc.queryForList(sql + " WHERE v.id_unidad = ? ORDER BY r.id_recibo DESC", s.idUnidad());
    }

    // ===================== RF-049 Consultar comprobante =====================
    public Path archivoComprobante(Sesion s, int idComprobante) {
        List<Map<String, Object>> l = jdbc.queryForList("""
                SELECT cp.archivo, c.id_unidad, p.id_pago, p.monto_pagado, p.metodo_pago,
                       p.nro_operacion, p.fecha_pago, un.numero_unidad, un.bloque,
                       u.nombre, u.apellido, r.numero_recibo
                FROM comprobante cp
                JOIN pago p ON p.id_pago = cp.id_pago
                JOIN cuota c ON c.id_cuota = p.id_cuota
                JOIN unidad un ON un.id_unidad = c.id_unidad
                JOIN usuario u ON u.id_usuario = p.id_usuario
                LEFT JOIN recibo r ON r.id_pago = p.id_pago
                WHERE cp.id_comprobante = ?""", idComprobante);
        if (l.isEmpty()) throw ApiException.noEncontrado("Comprobante no encontrado.");
        if (!s.tieneAccesoGestion() && !Objects.equals(l.get(0).get("id_unidad"), s.idUnidad()))
            throw ApiException.prohibido("No puede ver comprobantes de otro departamento.");
        
        Map<String, Object> row = l.get(0);
        String archivo = (String) row.get("archivo");
        if (archivo == null || archivo.isBlank() || archivo.equals("seed/voucher_demo.svg")) {
            archivo = "seed/voucher_" + row.get("id_pago") + ".svg";
        }
        Path f = uploads.resolve(archivo).normalize();

        // Si es boleta tipo seed o el archivo aun no existe, generamos el voucher SVG personalizado
        if (!Files.exists(f) || archivo.startsWith("seed/")) {
            f = generarVoucherPersonalizado(row, f);
        }

        if (!f.startsWith(uploads) || !Files.exists(f))
            throw ApiException.noEncontrado("El archivo del comprobante no esta disponible.");
        return f;
    }

    private Path generarVoucherPersonalizado(Map<String, Object> r, Path destino) {
        try {
            if (destino.getParent() != null) Files.createDirectories(destino.getParent());
            String numUnidad = String.valueOf(r.get("numero_unidad"));
            String propietario = r.get("nombre") + " " + r.get("apellido");
            java.math.BigDecimal monto = (java.math.BigDecimal) r.get("monto_pagado");
            String montoStr = monto != null ? String.format(java.util.Locale.US, "%.2f", monto) : "0.00";
            String metodo = String.valueOf(r.get("metodo_pago"));
            if ("null".equals(metodo) || metodo == null || metodo.isBlank()) metodo = "Efectivo";
            String fechaStr = String.valueOf(r.get("fecha_pago"));
            String dia = "25", mes = "10", anio = "26";
            if (fechaStr != null && fechaStr.length() >= 10) {
                String[] partes = fechaStr.split("-");
                if (partes.length == 3) {
                    anio = partes[0].substring(Math.max(0, partes[0].length() - 2));
                    mes = partes[1];
                    dia = partes[2];
                }
            }
            String nroRecibo = r.get("numero_recibo") != null ? String.valueOf(r.get("numero_recibo")) : String.valueOf(r.get("nro_operacion"));
            if (nroRecibo == null || "null".equals(nroRecibo) || nroRecibo.isBlank()) {
                nroRecibo = "00" + r.get("id_pago");
            }

            String svg = """
<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">
  <rect x="4" y="4" width="632" height="352" rx="6" fill="#fcfdff" stroke="#94a3b8" stroke-width="2"/>
  <text x="230" y="44" font-family="Arial, sans-serif" font-size="22" font-weight="bold" fill="#1e3a8a" text-anchor="middle" letter-spacing="1">RESIDENCIAL LA CRUCETA</text>
  <text x="230" y="68" font-family="Arial, sans-serif" font-size="18" font-weight="bold" fill="#1e3a8a" text-anchor="middle" letter-spacing="1">SURCO</text>
  <text x="585" y="42" font-family="'Comic Sans MS', 'Segoe Print', cursive" font-size="20" fill="#334155" text-anchor="end">%s</text>
  <text x="28" y="108" font-family="Arial, sans-serif" font-size="13" font-weight="bold" fill="#334155">RECIBO :</text>
  <line x1="95" y1="108" x2="310" y2="108" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="28" y="142" font-family="Arial, sans-serif" font-size="13" fill="#334155">Recibí del Sr. (a) :</text>
  <line x1="145" y1="142" x2="600" y2="142" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="180" y="138" font-family="'Segoe Print', 'Comic Sans MS', cursive" font-size="19" fill="#1e293b">%s</text>
  <text x="28" y="176" font-family="Arial, sans-serif" font-size="13" fill="#334155">La cantidad de :</text>
  <line x1="135" y1="176" x2="600" y2="176" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="165" y="172" font-family="'Segoe Print', 'Comic Sans MS', cursive" font-size="19" font-weight="bold" fill="#1e293b">S/. %s</text>
  <text x="28" y="210" font-family="Arial, sans-serif" font-size="13" fill="#334155">Por concepto de :</text>
  <line x1="145" y1="210" x2="600" y2="210" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="175" y="206" font-family="'Segoe Print', 'Comic Sans MS', cursive" font-size="18" fill="#1e293b">Pago de servicios (%s)</text>
  <line x1="28" y1="240" x2="600" y2="240" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="310" y="272" font-family="Arial, sans-serif" font-size="13" fill="#334155">Surco</text>
  <line x1="350" y1="272" x2="405" y2="272" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="377" y="268" font-family="'Segoe Print', cursive" font-size="16" fill="#1e293b" text-anchor="middle">%s</text>
  <text x="412" y="272" font-family="Arial, sans-serif" font-size="13" fill="#334155">de</text>
  <line x1="432" y1="272" x2="535" y2="272" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="483" y="268" font-family="'Segoe Print', cursive" font-size="16" fill="#1e293b" text-anchor="middle">%s</text>
  <text x="542" y="272" font-family="Arial, sans-serif" font-size="13" fill="#334155">20</text>
  <line x1="558" y1="272" x2="600" y2="272" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="579" y="268" font-family="'Segoe Print', cursive" font-size="16" fill="#1e293b" text-anchor="middle">%s</text>
  <g transform="translate(82,292) rotate(-8)">
    <ellipse cx="0" cy="0" rx="64" ry="44" fill="none" stroke="#1d4ed8" stroke-width="2.5"/>
    <ellipse cx="0" cy="0" rx="58" ry="38" fill="none" stroke="#1d4ed8" stroke-width="1"/>
    <text x="0" y="-20" font-family="Arial, sans-serif" font-size="9" font-weight="bold" fill="#1d4ed8" text-anchor="middle">EDIFICIO</text>
    <rect x="-46" y="-9" width="92" height="17" fill="none" stroke="#1d4ed8" stroke-width="1.4"/>
    <text x="0" y="3" font-family="Arial, sans-serif" font-size="10" font-weight="bold" fill="#1d4ed8" text-anchor="middle">LA DIRECTIVA</text>
    <text x="0" y="26" font-family="Arial, sans-serif" font-size="8.5" font-weight="bold" fill="#1d4ed8" text-anchor="middle">LA CRUCETA - SURCO</text>
  </g>
  <text x="155" y="315" font-family="'Courier New', monospace" font-size="22" font-weight="bold" fill="#dc2626">N° %s</text>
  <path d="M 430 315 C 445 275, 475 275, 465 310 C 460 325, 495 280, 505 305 L 420 322" fill="none" stroke="#1e293b" stroke-width="1.8"/>
  <line x1="365" y1="322" x2="575" y2="322" stroke="#64748b" stroke-dasharray="2,2"/>
  <text x="470" y="338" font-family="Arial, sans-serif" font-size="13" font-weight="bold" fill="#334155" text-anchor="middle">LA DIRECTIVA</text>
</svg>
""".formatted(numUnidad, propietario, montoStr, metodo, dia, mes, anio, nroRecibo);

            java.nio.file.Files.writeString(destino, svg, java.nio.charset.StandardCharsets.UTF_8);
            return destino;
        } catch (Exception e) {
            return destino;
        }
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
