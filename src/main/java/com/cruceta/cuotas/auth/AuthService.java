package com.cruceta.cuotas.auth;

import com.cruceta.cuotas.comun.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RF-003 Iniciar sesion / RF-004 Cerrar sesion.
 * Seguridad: hash BCrypt con sal unica, migracion transparente desde SHA-256,
 * bloqueo temporal por intentos fallidos, expiracion de sesion por inactividad
 * y soporte de cambio obligatorio de contrasena temporal.
 */
@Service
public class AuthService {

    public static final int MIN_CONTRASENA = 8;
    private static final String MSG_CREDENCIALES = "Credenciales incorrectas. Verifique su correo y contrasena.";
    private static final Duration DURACION_MAXIMA_ABSOLUTA = Duration.ofHours(8);

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);
    private static final String DUMMY_BCRYPT = BCRYPT.encode("cruceta-dummy-timing-guard");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String MAYUS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUS = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final String SIMBOLOS = "!@#$%&*";
    private static final String TODOS = MAYUS + MINUS + DIGITOS + SIMBOLOS;

    private final JdbcTemplate jdbc;
    private final int maxIntentosFallidos;
    private final Duration duracionBloqueo;
    private final Duration timeoutInactividad;

    private final Map<String, SesionActiva> sesiones = new ConcurrentHashMap<>();
    private final Map<String, EstadoIntentos> intentosPorCorreo = new ConcurrentHashMap<>();

    @Autowired
    public AuthService(JdbcTemplate jdbc,
                       @Value("${app.security.max-login-attempts:5}") int maxIntentosFallidos,
                       @Value("${app.security.lockout-minutes:15}") int minutosBloqueo,
                       @Value("${app.security.session-timeout-minutes:30}") int timeoutMinutos) {
        this.jdbc = jdbc;
        this.maxIntentosFallidos = Math.max(1, maxIntentosFallidos);
        this.duracionBloqueo = Duration.ofMinutes(Math.max(1, minutosBloqueo));
        this.timeoutInactividad = Duration.ofMinutes(Math.max(1, timeoutMinutos));
    }

    /** Constructor auxiliar para pruebas con duraciones personalizadas. */
    AuthService(JdbcTemplate jdbc, int maxIntentosFallidos, Duration duracionBloqueo, Duration timeoutInactividad) {
        this.jdbc = jdbc;
        this.maxIntentosFallidos = maxIntentosFallidos;
        this.duracionBloqueo = duracionBloqueo;
        this.timeoutInactividad = timeoutInactividad;
    }

    /** Genera un hash BCrypt con sal aleatoria unica (factor de costo 10). */
    public static String hash(String texto) {
        return BCRYPT.encode(texto);
    }

    /** Indica si un hash almacenado corresponde a BCrypt ($2a$, $2b$ o $2y$). */
    public static boolean esHashBcrypt(String hashGuardado) {
        return hashGuardado != null
                && (hashGuardado.startsWith("$2a$") || hashGuardado.startsWith("$2b$") || hashGuardado.startsWith("$2y$"));
    }

    /**
     * Verifica la contrasena contra un hash BCrypt o, por compatibilidad durante
     * migracion, contra un hash SHA-256 heredado de 64 caracteres hexadecimales.
     */
    public static boolean verificarContrasena(String textoPlano, String hashGuardado) {
        if (textoPlano == null || hashGuardado == null || hashGuardado.isBlank()) {
            return false;
        }
        if (esHashBcrypt(hashGuardado)) {
            return BCRYPT.matches(textoPlano, hashGuardado);
        }
        String sha = sha256Hex(textoPlano);
        return MessageDigest.isEqual(
                sha.getBytes(StandardCharsets.UTF_8),
                hashGuardado.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** Genera una contrasena temporal aleatoria segura de 12 caracteres. */
    public static String generarContrasenaTemporal() {
        List<Character> chars = new ArrayList<>(12);
        chars.add(MAYUS.charAt(SECURE_RANDOM.nextInt(MAYUS.length())));
        chars.add(MINUS.charAt(SECURE_RANDOM.nextInt(MINUS.length())));
        chars.add(DIGITOS.charAt(SECURE_RANDOM.nextInt(DIGITOS.length())));
        chars.add(SIMBOLOS.charAt(SECURE_RANDOM.nextInt(SIMBOLOS.length())));
        for (int i = 4; i < 12; i++) {
            chars.add(TODOS.charAt(SECURE_RANDOM.nextInt(TODOS.length())));
        }
        Collections.shuffle(chars, SECURE_RANDOM);
        StringBuilder sb = new StringBuilder(12);
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    private static String sha256Hex(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String generarToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Map<String, Object> iniciarSesion(String correo, String contrasena) {
        if (correo == null || correo.isBlank() || contrasena == null || contrasena.isBlank())
            throw ApiException.validacion("Ingrese su correo y contrasena.");

        String correoLimpio = correo.trim();
        String correoKey = correoLimpio.toLowerCase(Locale.ROOT);
        Instant ahora = Instant.now();

        // Verificar bloqueo en memoria (cubre tanto correos existentes como inexistentes)
        if (estaBloqueadoEnMemoria(correoKey, ahora)) {
            BCRYPT.matches(contrasena, DUMMY_BCRYPT);
            throw ApiException.noAutorizado(MSG_CREDENCIALES);
        }

        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT u.id_usuario, u.nombre, u.apellido, u.correo, u.contrasena, u.estado_cuenta,
                       u.intentos_fallidos, u.bloqueado_hasta, u.debe_cambiar_contrasena,
                       r.nombre_rol, u.id_unidad, CONCAT(un.bloque, ' - Dpto. ', un.numero_unidad) AS unidad
                FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
                LEFT JOIN unidad un ON un.id_unidad = u.id_unidad
                WHERE u.correo = ?""", correoLimpio);

        // Correo inexistente: simular costo de BCrypt, contar intento y responder mensaje generico
        if (filas.isEmpty()) {
            BCRYPT.matches(contrasena, DUMMY_BCRYPT);
            registrarFalloEnMemoria(correoKey, ahora);
            throw ApiException.noAutorizado(MSG_CREDENCIALES);
        }

        Map<String, Object> f = filas.get(0);
        int idUsuario = ((Number) f.get("id_usuario")).intValue();

        // Verificar bloqueo persistido en BD
        Instant bloqueadoBd = extraerInstante(f.get("bloqueado_hasta"));
        if (bloqueadoBd != null && ahora.isBefore(bloqueadoBd)) {
            BCRYPT.matches(contrasena, DUMMY_BCRYPT);
            throw ApiException.noAutorizado(MSG_CREDENCIALES);
        }

        String hashGuardado = (String) f.get("contrasena");
        if (!verificarContrasena(contrasena, hashGuardado)) {
            int intentosActuales = f.get("intentos_fallidos") == null ? 0 : ((Number) f.get("intentos_fallidos")).intValue();
            if (bloqueadoBd != null && !ahora.isBefore(bloqueadoBd)) {
                intentosActuales = 0; // el bloqueo previo ya vencio
            }
            int nuevosIntentos = intentosActuales + 1;
            Timestamp nuevoBloqueo = nuevosIntentos >= maxIntentosFallidos
                    ? Timestamp.from(ahora.plus(duracionBloqueo))
                    : null;
            jdbc.update("UPDATE usuario SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id_usuario = ?",
                    nuevosIntentos, nuevoBloqueo, idUsuario);
            registrarFalloEnMemoria(correoKey, ahora);
            throw ApiException.noAutorizado(MSG_CREDENCIALES);
        }

        if (!"ACTIVO".equals(f.get("estado_cuenta")))
            throw ApiException.noAutorizado("Su cuenta se encuentra desactivada. Comuniquese con la administracion.");

        // Reiniciar contador de intentos fallidos tras inicio de sesion exitoso
        intentosPorCorreo.remove(correoKey);
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = ?", idUsuario);

        // Migracion transparente: si la cuenta aun tenia hash SHA-256 heredado, actualizar a BCrypt
        if (!esHashBcrypt(hashGuardado)) {
            jdbc.update("UPDATE usuario SET contrasena = ? WHERE id_usuario = ?", hash(contrasena), idUsuario);
        }

        boolean debeCambiar = esVerdadero(f.get("debe_cambiar_contrasena"));
        Sesion s = new Sesion(
                idUsuario,
                f.get("nombre") + " " + f.get("apellido"),
                (String) f.get("correo"),
                (String) f.get("nombre_rol"),
                f.get("id_unidad") == null ? null : ((Number) f.get("id_unidad")).intValue(),
                (String) f.get("unidad"),
                debeCambiar);

        limpiarSesionesExpiradas(ahora);
        String token = generarToken();
        sesiones.put(token, new SesionActiva(s, ahora));
        return Map.of("token", token, "usuario", s);
    }

    public void cerrarSesion(String authHeader) {
        String t = token(authHeader);
        if (t != null) sesiones.remove(t);
    }

    /** Invalida todas las sesiones activas de un usuario (ej. al desactivarlo). */
    public void invalidarSesionesDeUsuario(int idUsuario) {
        sesiones.entrySet().removeIf(e -> e.getValue().sesion.idUsuario() == idUsuario);
    }

    /** Limpia el bloqueo en memoria de un correo (ej. tras completar recuperacion aprobada). */
    public void limpiarIntentosCorreo(String correo) {
        if (correo != null) {
            intentosPorCorreo.remove(correo.trim().toLowerCase(Locale.ROOT));
        }
    }

    /** Obtiene la sesion activa o lanza 401; bloquea si debe cambiar contrasena temporal. */
    public Sesion requerir(String authHeader) {
        return requerirInterno(authHeader, true);
    }

    /** Obtiene la sesion activa incluso si tiene cambio obligatorio de contrasena pendiente. */
    public Sesion requerirPermitirCambioContrasena(String authHeader) {
        return requerirInterno(authHeader, false);
    }

    private Sesion requerirInterno(String authHeader, boolean exigirContrasenaDefinitiva) {
        String t = token(authHeader);
        SesionActiva activa = t == null ? null : sesiones.get(t);
        if (activa == null) throw ApiException.noAutorizado("Sesion expirada. Inicie sesion nuevamente.");

        Instant ahora = Instant.now();
        if (estaExpirada(activa, ahora)) {
            sesiones.remove(t);
            throw ApiException.noAutorizado("Sesion expirada por inactividad. Inicie sesion nuevamente.");
        }

        activa.ultimaActividad = ahora;
        if (exigirContrasenaDefinitiva && activa.sesion.debeCambiarContrasena()) {
            throw ApiException.prohibido("Debe cambiar su contrasena temporal antes de continuar.");
        }
        return activa.sesion;
    }

    /** Solo administradores (flujo alternativo: "si no tiene autorizacion, se rechaza la operacion"). */
    public Sesion requerirAdmin(String authHeader) {
        Sesion s = requerir(authHeader);
        if (!s.esAdmin()) throw ApiException.prohibido("No tiene autorizacion para realizar esta operacion.");
        return s;
    }

    /** Administrador o Miembro de la Junta Directiva (auditoria, reportes y fiscalizacion). */
    public Sesion requerirAdminOJunta(String authHeader) {
        Sesion s = requerir(authHeader);
        if (!s.tieneAccesoGestion()) throw ApiException.prohibido("Esta operacion requiere rol de Administrador o Junta Directiva.");
        return s;
    }

    /** Permite al usuario cambiar su contrasena temporal en el primer inicio de sesion. */
    public Map<String, Object> cambiarContrasenaObligatoria(String authHeader, String actual, String nueva) {
        String t = token(authHeader);
        Sesion s = requerirPermitirCambioContrasena(authHeader);
        if (actual == null || actual.isBlank() || nueva == null || nueva.isBlank()) {
            throw ApiException.validacion("Ingrese su contrasena actual y la nueva contrasena.");
        }
        String guardada = jdbc.queryForObject("SELECT contrasena FROM usuario WHERE id_usuario = ?", String.class, s.idUsuario());
        if (!verificarContrasena(actual, guardada)) {
            throw ApiException.validacion("La contrasena actual no es correcta.");
        }
        if (nueva.length() < MIN_CONTRASENA) {
            throw ApiException.validacion("La nueva contrasena debe tener al menos " + MIN_CONTRASENA + " caracteres.");
        }
        if (nueva.equals(actual)) {
            throw ApiException.validacion("La nueva contrasena debe ser distinta a la contrasena temporal.");
        }

        jdbc.update("UPDATE usuario SET contrasena = ?, debe_cambiar_contrasena = 0 WHERE id_usuario = ?",
                hash(nueva), s.idUsuario());

        Sesion actualizada = new Sesion(s.idUsuario(), s.nombreCompleto(), s.correo(), s.rol(),
                s.idUnidad(), s.unidad(), false);
        if (t != null) {
            SesionActiva activa = sesiones.get(t);
            if (activa != null) {
                activa.sesion = actualizada;
            }
        }
        marcarContrasenaCambiada(s.idUsuario());
        return Map.of("mensaje", "Contrasena actualizada correctamente.", "usuario", actualizada);
    }

    /** Actualiza en memoria las sesiones del usuario cuando cambia su contrasena desde el perfil. */
    public void marcarContrasenaCambiada(int idUsuario) {
        for (SesionActiva activa : sesiones.values()) {
            if (activa.sesion.idUsuario() == idUsuario && activa.sesion.debeCambiarContrasena()) {
                Sesion s = activa.sesion;
                activa.sesion = new Sesion(s.idUsuario(), s.nombreCompleto(), s.correo(), s.rol(),
                        s.idUnidad(), s.unidad(), false);
            }
        }
    }

    private boolean estaExpirada(SesionActiva activa, Instant ahora) {
        Duration inactividad = Duration.between(activa.ultimaActividad, ahora);
        Duration vidaTotal = Duration.between(activa.creadaEn, ahora);
        return inactividad.compareTo(timeoutInactividad) >= 0
                || vidaTotal.compareTo(DURACION_MAXIMA_ABSOLUTA) >= 0;
    }

    private void limpiarSesionesExpiradas(Instant ahora) {
        sesiones.entrySet().removeIf(e -> estaExpirada(e.getValue(), ahora));
    }

    private boolean estaBloqueadoEnMemoria(String correoKey, Instant ahora) {
        EstadoIntentos estado = intentosPorCorreo.get(correoKey);
        if (estado == null) return false;
        synchronized (estado) {
            if (estado.bloqueadoHasta == null) return false;
            if (ahora.isBefore(estado.bloqueadoHasta)) return true;
            // El bloqueo vencio: reiniciar estado
            estado.intentos = 0;
            estado.bloqueadoHasta = null;
            return false;
        }
    }

    private void registrarFalloEnMemoria(String correoKey, Instant ahora) {
        EstadoIntentos estado = intentosPorCorreo.computeIfAbsent(correoKey, k -> new EstadoIntentos());
        synchronized (estado) {
            if (estado.bloqueadoHasta != null && !ahora.isBefore(estado.bloqueadoHasta)) {
                estado.intentos = 0;
                estado.bloqueadoHasta = null;
            }
            estado.intentos++;
            if (estado.intentos >= maxIntentosFallidos) {
                estado.bloqueadoHasta = ahora.plus(duracionBloqueo);
            }
        }
    }

    private static Instant extraerInstante(Object valor) {
        if (valor instanceof Timestamp ts) return ts.toInstant();
        if (valor instanceof java.time.LocalDateTime ldt)
            return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
        return null;
    }

    private static boolean esVerdadero(Object valor) {
        if (valor instanceof Boolean b) return b;
        if (valor instanceof Number n) return n.intValue() != 0;
        return false;
    }

    private String token(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String t = authHeader.substring(7).trim();
        return t.isEmpty() ? null : t;
    }

    private static final class SesionActiva {
        volatile Sesion sesion;
        volatile Instant ultimaActividad;
        final Instant creadaEn;

        SesionActiva(Sesion sesion, Instant ahora) {
            this.sesion = sesion;
            this.ultimaActividad = ahora;
            this.creadaEn = ahora;
        }
    }

    private static final class EstadoIntentos {
        int intentos;
        Instant bloqueadoHasta;
    }
}
