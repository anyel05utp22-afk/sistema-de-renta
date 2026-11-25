package com.cruceta.cuotas.auth;

import com.cruceta.cuotas.comun.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RF-005 Recuperacion segura de contrasena con verificacion de comprobante de pago
 * y aprobacion por el administrador en el modulo "Alerta de seguridad".
 */
@Service
public class RecuperacionService {

    private static final Set<String> FORMATOS = Set.of("pdf", "jpg", "jpeg", "png");

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public RecuperacionService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    /** Consulta el estado de recuperacion de un correo (SOLICITAR, PENDIENTE o APROBADA). */
    public Map<String, Object> consultarEstado(String correo) {
        String c = validarCorreo(correo);

        List<Map<String, Object>> aprobadas = jdbc.queryForList("""
                SELECT id_solicitud, DATE_FORMAT(fecha_resolucion, '%Y-%m-%d %H:%i') AS fecha_resolucion
                FROM solicitud_recuperacion
                WHERE LOWER(correo) = LOWER(?) AND estado = 'APROBADA'
                ORDER BY id_solicitud DESC LIMIT 1""", c);
        if (!aprobadas.isEmpty()) {
            return Map.of(
                    "estado", "APROBADA",
                    "mensaje", "Su solicitud fue aprobada por el administrador. Ingrese y confirme su nueva contrasena.");
        }

        List<Map<String, Object>> pendientes = jdbc.queryForList("""
                SELECT id_solicitud, DATE_FORMAT(fecha_solicitud, '%Y-%m-%d %H:%i') AS fecha_solicitud
                FROM solicitud_recuperacion
                WHERE LOWER(correo) = LOWER(?) AND estado = 'PENDIENTE'
                ORDER BY id_solicitud DESC LIMIT 1""", c);
        if (!pendientes.isEmpty()) {
            return Map.of(
                    "estado", "PENDIENTE",
                    "mensaje", "Su solicitud ya esta en revision por el administrador. Mientras tanto, su cuenta permanece desactivada temporalmente.");
        }

        return Map.of(
                "estado", "SOLICITAR",
                "mensaje", "Adjunte un comprobante de pago (PDF, JPG o PNG) como prueba de identidad para enviar su solicitud al administrador.");
    }

    /**
     * Registra la solicitud de recuperacion con el comprobante de pago de prueba
     * y desactiva temporalmente la cuenta del usuario hasta que el administrador apruebe.
     */
    @Transactional
    public Map<String, Object> solicitar(String correo, MultipartFile archivo) {
        String c = validarCorreo(correo);
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.validacion("Debe adjuntar un comprobante de pago (PDF, JPG o PNG) como prueba.");
        }
        String original = archivo.getOriginalFilename() == null ? "comprobante" : archivo.getOriginalFilename();
        String ext = extension(original);
        if (!FORMATOS.contains(ext)) {
            throw ApiException.validacion("El comprobante debe estar en formato PDF, JPG o PNG.");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw ApiException.validacion("No se pudo leer el archivo adjunto. Intente nuevamente.");
        }
        if (!magicBytesValidos(bytes, ext)) {
            throw ApiException.validacion("El contenido del archivo no corresponde a un documento PDF, JPG o PNG valido.");
        }

        List<Map<String, Object>> usuarios = jdbc.queryForList("""
                SELECT u.id_usuario, u.correo, u.estado_cuenta, r.nombre_rol
                FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
                WHERE LOWER(u.correo) = LOWER(?)""", c);

        // Respuesta generica si el correo no existe para no filtrar cuentas registradas
        if (usuarios.isEmpty()) {
            return Map.of(
                    "estado", "PENDIENTE",
                    "mensaje", "Solicitud recibida. Si el correo corresponde a una cuenta registrada, fue enviada al administrador para su verificacion y la cuenta quedara desactivada temporalmente.");
        }

        Map<String, Object> u = usuarios.get(0);
        int idUsuario = ((Number) u.get("id_usuario")).intValue();
        String rol = String.valueOf(u.get("nombre_rol"));

        // Evitar bloquear la unica cuenta administradora activa del sistema
        if ("ADMINISTRADOR".equalsIgnoreCase(rol)) {
            Integer adminsActivos = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM usuario u JOIN rol r ON r.id_rol = u.id_rol WHERE UPPER(r.nombre_rol) = 'ADMINISTRADOR' AND u.estado_cuenta = 'ACTIVO'",
                    Integer.class);
            if (adminsActivos != null && adminsActivos <= 1) {
                throw ApiException.validacion("La cuenta administradora principal no puede desactivarse por este medio porque es la unica activa para aprobar solicitudes.");
            }
        }

        Integer yaActiva = jdbc.queryForObject(
                "SELECT COUNT(*) FROM solicitud_recuperacion WHERE id_usuario = ? AND estado IN ('PENDIENTE', 'APROBADA')",
                Integer.class, idUsuario);
        if (yaActiva != null && yaActiva > 0) {
            throw ApiException.validacion("Ya existe una solicitud de recuperacion en curso para este correo.");
        }

        String mime = mimeParaExtension(ext);
        String nombreLimpio = "prueba_rec_" + idUsuario + "_" + System.currentTimeMillis() + "." + ext;

        jdbc.update("""
                INSERT INTO solicitud_recuperacion (id_usuario, correo, archivo_nombre, archivo_tipo, archivo_datos, estado, fecha_solicitud)
                VALUES (?, ?, ?, ?, ?, 'PENDIENTE', NOW())""",
                idUsuario, u.get("correo"), nombreLimpio, mime, bytes);

        // Desactivar temporalmente la cuenta e invalidar cualquier sesion activa
        jdbc.update("UPDATE usuario SET estado_cuenta = 'INACTIVO' WHERE id_usuario = ?", idUsuario);
        auth.invalidarSesionesDeUsuario(idUsuario);

        return Map.of(
                "estado", "PENDIENTE",
                "mensaje", "Solicitud enviada al administrador. Por seguridad, su cuenta ha sido desactivada temporalmente hasta que el administrador valide su comprobante y apruebe el cambio de contrasena.");
    }

    /** Completa el cambio de contrasena cuando el administrador ya aprobo la solicitud. */
    @Transactional
    public Map<String, Object> confirmarNuevaContrasena(String correo, String nueva, String confirmar) {
        String c = validarCorreo(correo);
        if (nueva == null || nueva.isBlank() || confirmar == null || confirmar.isBlank()) {
            throw ApiException.validacion("Ingrese y confirme su nueva contrasena.");
        }
        if (nueva.length() < AuthService.MIN_CONTRASENA) {
            throw ApiException.validacion("La nueva contrasena debe tener al menos " + AuthService.MIN_CONTRASENA + " caracteres.");
        }
        if (!nueva.equals(confirmar)) {
            throw ApiException.validacion("Las contrasenas ingresadas no coinciden.");
        }

        List<Map<String, Object>> aprobadas = jdbc.queryForList("""
                SELECT id_solicitud, id_usuario
                FROM solicitud_recuperacion
                WHERE LOWER(correo) = LOWER(?) AND estado = 'APROBADA'
                ORDER BY id_solicitud DESC LIMIT 1""", c);
        if (aprobadas.isEmpty()) {
            throw ApiException.validacion("No existe una solicitud aprobada para este correo. Espere a que el administrador apruebe su solicitud.");
        }

        int idSolicitud = ((Number) aprobadas.get(0).get("id_solicitud")).intValue();
        int idUsuario = ((Number) aprobadas.get(0).get("id_usuario")).intValue();

        jdbc.update("""
                UPDATE usuario
                SET contrasena = ?, estado_cuenta = 'ACTIVO', intentos_fallidos = 0,
                    bloqueado_hasta = NULL, debe_cambiar_contrasena = 0
                WHERE id_usuario = ?""", AuthService.hash(nueva), idUsuario);

        jdbc.update("UPDATE solicitud_recuperacion SET estado = 'COMPLETADA', fecha_resolucion = NOW() WHERE id_solicitud = ?", idSolicitud);
        auth.limpiarIntentosCorreo(c);

        return Map.of("mensaje", "Contrasena actualizada correctamente y cuenta reactivada. Ya puede iniciar sesion con su nueva contrasena.");
    }

    // ===================== Panel Admin: Alerta de seguridad =====================

    public List<Map<String, Object>> listarSolicitudes() {
        return jdbc.queryForList("""
                SELECT s.id_solicitud, s.id_usuario, s.correo, s.archivo_nombre, s.archivo_tipo,
                       s.estado, s.observacion,
                       DATE_FORMAT(s.fecha_solicitud, '%Y-%m-%d %H:%i') AS fecha_solicitud,
                       DATE_FORMAT(s.fecha_resolucion, '%Y-%m-%d %H:%i') AS fecha_resolucion,
                       CONCAT(u.nombre, ' ', u.apellido) AS usuario,
                       u.dni, u.estado_cuenta,
                       CONCAT(un.bloque, ' - Dpto. ', un.numero_unidad) AS unidad
                FROM solicitud_recuperacion s
                JOIN usuario u ON u.id_usuario = s.id_usuario
                LEFT JOIN unidad un ON un.id_unidad = u.id_unidad
                ORDER BY CASE s.estado WHEN 'PENDIENTE' THEN 0 WHEN 'APROBADA' THEN 1 ELSE 2 END,
                         s.fecha_solicitud DESC""");
    }

    public Map<String, Object> obtenerArchivoPrueba(int idSolicitud) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT archivo_nombre, archivo_tipo, archivo_datos FROM solicitud_recuperacion WHERE id_solicitud = ?",
                idSolicitud);
        if (filas.isEmpty()) throw ApiException.noEncontrado("Solicitud de recuperacion no encontrada.");
        return filas.get(0);
    }

    @Transactional
    public Map<String, Object> aprobar(int idSolicitud, int idAdmin) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id_solicitud, id_usuario, correo, estado FROM solicitud_recuperacion WHERE id_solicitud = ?",
                idSolicitud);
        if (filas.isEmpty()) throw ApiException.noEncontrado("Solicitud no encontrada.");
        Map<String, Object> s = filas.get(0);
        if (!"PENDIENTE".equals(s.get("estado"))) {
            throw ApiException.validacion("La solicitud ya fue procesada (estado: " + s.get("estado") + ").");
        }
        jdbc.update("""
                UPDATE solicitud_recuperacion
                SET estado = 'APROBADA', fecha_resolucion = NOW(), resuelto_por = ?, observacion = NULL
                WHERE id_solicitud = ?""", idAdmin, idSolicitud);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mensaje", "Solicitud #" + idSolicitud + " aprobada. El usuario (" + s.get("correo")
                + ") ya puede ingresar su correo en 'Recuperar contrasena' para definir su nueva contrasena.");
        return resp;
    }

    @Transactional
    public Map<String, Object> rechazar(int idSolicitud, int idAdmin, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.validacion("Debe indicar el motivo del rechazo.");
        }
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id_solicitud, id_usuario, correo, estado FROM solicitud_recuperacion WHERE id_solicitud = ?",
                idSolicitud);
        if (filas.isEmpty()) throw ApiException.noEncontrado("Solicitud no encontrada.");
        Map<String, Object> s = filas.get(0);
        if (!"PENDIENTE".equals(s.get("estado"))) {
            throw ApiException.validacion("La solicitud ya fue procesada (estado: " + s.get("estado") + ").");
        }
        int idUsuario = ((Number) s.get("id_usuario")).intValue();
        jdbc.update("""
                UPDATE solicitud_recuperacion
                SET estado = 'RECHAZADA', observacion = ?, fecha_resolucion = NOW(), resuelto_por = ?
                WHERE id_solicitud = ?""", motivo.trim(), idAdmin, idSolicitud);
        // Al rechazar la solicitud, se reactiva la cuenta del usuario con su contrasena original
        jdbc.update("UPDATE usuario SET estado_cuenta = 'ACTIVO' WHERE id_usuario = ?", idUsuario);
        return Map.of("mensaje", "Solicitud #" + idSolicitud + " rechazada. La cuenta fue reactivada con su contrasena anterior.");
    }

    // ---------------------------------------------------------------
    private static String validarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            throw ApiException.validacion("Ingrese el correo electronico registrado.");
        }
        String c = correo.trim();
        if (!c.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            throw ApiException.validacion("El correo electronico no tiene un formato valido.");
        }
        return c;
    }

    private static String extension(String nombre) {
        if (nombre == null || !nombre.contains(".")) return "";
        return nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String mimeParaExtension(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    static boolean magicBytesValidos(byte[] header, String ext) {
        if (header == null || ext == null) return false;
        if ("pdf".equals(ext)) {
            return header.length >= 5
                    && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44
                    && header[3] == 0x46 && header[4] == 0x2D;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return header.length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E
                    && header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A
                    && header[6] == 0x1A && header[7] == 0x0A;
        }
        return false;
    }
}
