package com.cruceta.cuotas.auth;

import com.cruceta.cuotas.comun.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** RF-003 Iniciar sesion / RF-004 Cerrar sesion. Sesiones simples por token en memoria. */
@Service
public class AuthService {

    private final JdbcTemplate jdbc;
    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String hash(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> iniciarSesion(String correo, String contrasena) {
        if (correo.isBlank() || contrasena.isBlank())
            throw ApiException.validacion("Ingrese su correo y contrasena.");

        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT u.id_usuario, u.nombre, u.apellido, u.correo, u.contrasena, u.estado_cuenta,
                       r.nombre_rol, u.id_unidad, CONCAT(un.bloque, ' - ', un.numero_unidad) AS unidad
                FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
                LEFT JOIN unidad un ON un.id_unidad = u.id_unidad
                WHERE u.correo = ?""", correo.trim());

        // Mensaje generico: no revela si el correo existe (flujo alternativo RF-003)
        if (filas.isEmpty() || !hash(contrasena).equals(filas.get(0).get("contrasena")))
            throw ApiException.noAutorizado("Credenciales incorrectas. Verifique su correo y contrasena.");

        Map<String, Object> f = filas.get(0);
        if (!"ACTIVO".equals(f.get("estado_cuenta")))
            throw ApiException.noAutorizado("Su cuenta se encuentra desactivada. Comuniquese con la administracion.");

        Sesion s = new Sesion(
                ((Number) f.get("id_usuario")).intValue(),
                f.get("nombre") + " " + f.get("apellido"),
                (String) f.get("correo"),
                (String) f.get("nombre_rol"),
                f.get("id_unidad") == null ? null : ((Number) f.get("id_unidad")).intValue(),
                (String) f.get("unidad"));
        String token = UUID.randomUUID().toString();
        sesiones.put(token, s);
        return Map.of("token", token, "usuario", s);
    }

    public void cerrarSesion(String authHeader) {
        String t = token(authHeader);
        if (t != null) sesiones.remove(t);
    }

    /** Obtiene la sesion o lanza 401. */
    public Sesion requerir(String authHeader) {
        String t = token(authHeader);
        Sesion s = t == null ? null : sesiones.get(t);
        if (s == null) throw ApiException.noAutorizado("Sesion expirada. Inicie sesion nuevamente.");
        return s;
    }

    /** Solo administradores (flujo alternativo: "si no tiene autorizacion, se rechaza la operacion"). */
    public Sesion requerirAdmin(String authHeader) {
        Sesion s = requerir(authHeader);
        if (!s.esAdmin()) throw ApiException.prohibido("No tiene autorizacion para realizar esta operacion.");
        return s;
    }

    private String token(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7).trim();
    }
}
