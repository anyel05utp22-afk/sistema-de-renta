package com.cruceta.cuotas;

import com.cruceta.cuotas.auth.AuthService;
import com.cruceta.cuotas.auth.Sesion;
import com.cruceta.cuotas.comun.ApiException;
import com.cruceta.cuotas.pago.PagoController;
import com.cruceta.cuotas.pago.PagoService;
import com.cruceta.cuotas.unidad.UnidadController;
import com.cruceta.cuotas.usuario.UsuarioController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityFixesTest {

    @Test
    void h1_bcryptGeneraHashesUnicosConSalYVerificaCorrectamente() {
        String h1 = AuthService.hash("ClaveSegura#2026");
        String h2 = AuthService.hash("ClaveSegura#2026");

        assertTrue(h1.startsWith("$2a$10$"), "Debe usar BCrypt ($2a$10$)");
        assertTrue(h2.startsWith("$2a$10$"), "Debe usar BCrypt ($2a$10$)");
        assertNotEquals(h1, h2, "Cada hash debe tener una sal aleatoria distinta");
        assertTrue(AuthService.verificarContrasena("ClaveSegura#2026", h1));
        assertTrue(AuthService.verificarContrasena("ClaveSegura#2026", h2));
        assertFalse(AuthService.verificarContrasena("ClaveIncorrecta", h1));
    }

    @Test
    void h1_migracionTransparenteDesdeSha256ABcryptEnLogin() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String legacySha256 = HexFormat.of().formatHex(md.digest("claveLegacy99".getBytes(StandardCharsets.UTF_8)));

        Map<String, Object> fila = new HashMap<>();
        fila.put("id_usuario", 99);
        fila.put("nombre", "Ana");
        fila.put("apellido", "Perez");
        fila.put("correo", "ana@cruceta.com");
        fila.put("contrasena", legacySha256);
        fila.put("estado_cuenta", "ACTIVO");
        fila.put("intentos_fallidos", 0);
        fila.put("bloqueado_hasta", null);
        fila.put("debe_cambiar_contrasena", 0);
        fila.put("nombre_rol", "PROPIETARIO");
        fila.put("id_unidad", 1);
        fila.put("unidad", "Piso 1 - Dpto. 101");

        when(jdbc.queryForList(anyString(), eq("ana@cruceta.com"))).thenReturn(List.of(fila));

        AuthService auth = new AuthService(jdbc, 5, 15, 30);
        Map<String, Object> res = auth.iniciarSesion("ana@cruceta.com", "claveLegacy99");
        assertNotNull(res.get("token"));

        // Debe haber actualizado la contrasena a un hash BCrypt
        verify(jdbc).update(eq("UPDATE usuario SET contrasena = ? WHERE id_usuario = ?"),
                argThat((Object h) -> h instanceof String s && s.startsWith("$2a$")), eq(99));
    }

    @Test
    void h2_bloqueoTrasCincoIntentosFallidosConMismoMensajeGenerico() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String bcrypt = AuthService.hash("Correcta!2026");

        Map<String, Object> fila = new HashMap<>();
        fila.put("id_usuario", 1);
        fila.put("nombre", "Rosa");
        fila.put("apellido", "Quispe");
        fila.put("correo", "admin@cruceta.com");
        fila.put("contrasena", bcrypt);
        fila.put("estado_cuenta", "ACTIVO");
        fila.put("intentos_fallidos", 0);
        fila.put("bloqueado_hasta", null);
        fila.put("debe_cambiar_contrasena", 0);
        fila.put("nombre_rol", "ADMINISTRADOR");
        fila.put("id_unidad", null);
        fila.put("unidad", null);

        when(jdbc.queryForList(anyString(), eq("admin@cruceta.com"))).thenReturn(List.of(fila));
        when(jdbc.queryForList(anyString(), eq("noexiste@cruceta.com"))).thenReturn(List.of());

        AuthService auth = new AuthService(jdbc, 5, 15, 30);

        String msgInexistente = assertThrows(ApiException.class,
                () -> auth.iniciarSesion("noexiste@cruceta.com", "mal")).getMessage();

        for (int i = 0; i < 5; i++) {
            final int intento = i;
            ApiException ex = assertThrows(ApiException.class,
                    () -> auth.iniciarSesion("admin@cruceta.com", "incorrecta" + intento));
            assertEquals(msgInexistente, ex.getMessage(), "El mensaje debe ser siempre generico");
        }

        // Al 6to intento, incluso con la contrasena correcta, debe seguir bloqueado con el mismo mensaje
        ApiException bloqueado = assertThrows(ApiException.class,
                () -> auth.iniciarSesion("admin@cruceta.com", "Correcta!2026"));
        assertEquals(HttpStatus.UNAUTHORIZED, bloqueado.getStatus());
        assertEquals(msgInexistente, bloqueado.getMessage());
    }

    @Test
    void h3_sesionExpiraPorInactividad() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String bcrypt = AuthService.hash("Correcta!2026");
        Map<String, Object> fila = new HashMap<>();
        fila.put("id_usuario", 1);
        fila.put("nombre", "Rosa");
        fila.put("apellido", "Quispe");
        fila.put("correo", "admin@cruceta.com");
        fila.put("contrasena", bcrypt);
        fila.put("estado_cuenta", "ACTIVO");
        fila.put("intentos_fallidos", 0);
        fila.put("bloqueado_hasta", null);
        fila.put("debe_cambiar_contrasena", 0);
        fila.put("nombre_rol", "ADMINISTRADOR");
        fila.put("id_unidad", null);
        fila.put("unidad", null);

        when(jdbc.queryForList(anyString(), eq("admin@cruceta.com"))).thenReturn(List.of(fila));

        // Usar constructor package-private via reflexion con timeout de 150ms
        var ctor = AuthService.class.getDeclaredConstructor(JdbcTemplate.class, int.class, Duration.class, Duration.class);
        ctor.setAccessible(true);
        AuthService auth = ctor.newInstance(jdbc, 5, Duration.ofMinutes(15), Duration.ofMillis(150));

        String token = (String) auth.iniciarSesion("admin@cruceta.com", "Correcta!2026").get("token");
        assertNotNull(auth.requerir("Bearer " + token));

        Thread.sleep(220);

        ApiException expirada = assertThrows(ApiException.class, () -> auth.requerir("Bearer " + token));
        assertEquals(HttpStatus.UNAUTHORIZED, expirada.getStatus());
    }

    @Test
    void h3_comprobanteAutenticaPorCabeceraAuthorizationYNoPorQueryParam() throws Exception {
        Method m = PagoController.class.getMethod("archivo", String.class, int.class);
        Parameter[] params = m.getParameters();
        assertNotNull(params[0].getAnnotation(RequestHeader.class), "El token debe viajar en cabecera Authorization");
        assertEquals("Authorization", params[0].getAnnotation(RequestHeader.class).value());
        for (Parameter p : params) {
            assertNull(p.getAnnotation(RequestParam.class), "No debe aceptar token como parametro en la URL");
        }
    }

    @Test
    void h4_contrasenaTemporalAleatoriaYCambioObligatorio() {
        String t1 = AuthService.generarContrasenaTemporal();
        String t2 = AuthService.generarContrasenaTemporal();
        assertTrue(t1.length() >= 12);
        assertNotEquals(t1, t2);
        assertTrue(t1.chars().anyMatch(Character::isUpperCase));
        assertTrue(t1.chars().anyMatch(Character::isLowerCase));
        assertTrue(t1.chars().anyMatch(Character::isDigit));

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String bcryptTemp = AuthService.hash(t1);
        Map<String, Object> fila = new HashMap<>();
        fila.put("id_usuario", 5);
        fila.put("nombre", "Luis");
        fila.put("apellido", "Torres");
        fila.put("correo", "luis@email.com");
        fila.put("contrasena", bcryptTemp);
        fila.put("estado_cuenta", "ACTIVO");
        fila.put("intentos_fallidos", 0);
        fila.put("bloqueado_hasta", null);
        fila.put("debe_cambiar_contrasena", 1);
        fila.put("nombre_rol", "PROPIETARIO");
        fila.put("id_unidad", 1);
        fila.put("unidad", "Piso 1 - Dpto. 101");

        when(jdbc.queryForList(anyString(), eq("luis@email.com"))).thenReturn(List.of(fila));
        when(jdbc.queryForObject(eq("SELECT contrasena FROM usuario WHERE id_usuario = ?"), eq(String.class), eq(5)))
                .thenReturn(bcryptTemp);

        AuthService auth = new AuthService(jdbc, 5, 15, 30);
        Map<String, Object> loginRes = auth.iniciarSesion("luis@email.com", t1);
        String token = (String) loginRes.get("token");
        Sesion s = (Sesion) loginRes.get("usuario");
        assertTrue(s.debeCambiarContrasena());

        // No puede acceder a endpoints normales hasta cambiar contrasena
        ApiException bloqueado = assertThrows(ApiException.class, () -> auth.requerir("Bearer " + token));
        assertEquals(HttpStatus.FORBIDDEN, bloqueado.getStatus());

        // Contrasena menor a 8 caracteres rechazada
        assertThrows(ApiException.class, () -> auth.cambiarContrasenaObligatoria("Bearer " + token, t1, "corta77"));

        // Cambio valido desbloquea la sesion
        Map<String, Object> cambio = auth.cambiarContrasenaObligatoria("Bearer " + token, t1, "NuevaClave!2026");
        assertFalse(((Sesion) cambio.get("usuario")).debeCambiarContrasena());
        assertDoesNotThrow(() -> auth.requerir("Bearer " + token));
    }

    @Test
    void h4_registroRechazaContrasenaCortaOIgualAlDniYGeneraTemporalSiVacia() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        when(auth.requerirAdmin(any())).thenReturn(new Sesion(1, "Admin", "admin@cruceta.com", "ADMINISTRADOR", null, null, false));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        UsuarioController ctrl = new UsuarioController(jdbc, auth);

        Map<String, Object> bodyCorta = new HashMap<>(Map.of(
                "nombre", "Juan", "apellido", "Lopez", "dni", "42000111",
                "correo", "juan@email.com", "id_rol", 2, "id_unidad", 1, "contrasena", "123456"));
        assertThrows(ApiException.class, () -> ctrl.registrar("Bearer x", bodyCorta));

        Map<String, Object> bodyDni = new HashMap<>(Map.of(
                "nombre", "Juan", "apellido", "Lopez", "dni", "42000111",
                "correo", "juan@email.com", "id_rol", 2, "id_unidad", 1, "contrasena", "42000111"));
        assertThrows(ApiException.class, () -> ctrl.registrar("Bearer x", bodyDni));

        Map<String, Object> bodyVacia = new HashMap<>(Map.of(
                "nombre", "Juan", "apellido", "Lopez", "dni", "42000111",
                "correo", "juan@email.com", "id_rol", 2, "id_unidad", 1, "contrasena", ""));
        Map<String, Object> ok = ctrl.registrar("Bearer x", bodyVacia);
        assertNotNull(ok.get("contrasena_temporal"));
        assertNotEquals("42000111", ok.get("contrasena_temporal"));
        assertTrue(ok.get("contrasena_temporal").toString().length() >= 12);
    }

    @Test
    void ps06_validacionMagicBytesRechazaArchivosDisfrazados() throws Exception {
        Method m = PagoService.class.getDeclaredMethod("contenidoValidoParaExtension",
                org.springframework.web.multipart.MultipartFile.class, String.class);
        m.setAccessible(true);

        MockMultipartFile pdfReal = new MockMultipartFile("archivo", "v.pdf", "application/pdf",
                "%PDF-1.4\ncontenido".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile pdfFalso = new MockMultipartFile("archivo", "v.pdf", "application/pdf",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile jpgReal = new MockMultipartFile("archivo", "v.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        MockMultipartFile pngReal = new MockMultipartFile("archivo", "v.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        MockMultipartFile pngFalso = new MockMultipartFile("archivo", "v.png", "image/png",
                "texto plano falso".getBytes(StandardCharsets.UTF_8));

        assertTrue((Boolean) m.invoke(null, pdfReal, "pdf"));
        assertFalse((Boolean) m.invoke(null, pdfFalso, "pdf"));
        assertTrue((Boolean) m.invoke(null, jpgReal, "jpg"));
        assertTrue((Boolean) m.invoke(null, pngReal, "png"));
        assertFalse((Boolean) m.invoke(null, pngFalso, "png"));
    }

    @Test
    void obsMenor3_unidadesSoloExponePropiaUnidadAPropietarios() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        when(auth.requerir("Bearer prop"))
                .thenReturn(new Sesion(2, "Luis Torres", "luis@email.com", "PROPIETARIO", 1, "Piso 1 - Dpto. 101", false));
        List<Map<String, Object>> propia = new ArrayList<>();
        propia.add(Map.of("id_unidad", 1, "bloque", "Piso 1", "numero_unidad", "101", "estado", "ACTIVA"));
        when(jdbc.queryForList(contains("WHERE un.id_unidad = ?"), eq(1))).thenReturn(propia);

        UnidadController ctrl = new UnidadController(jdbc, auth);
        List<Map<String, Object>> res = ctrl.listar("Bearer prop");
        assertEquals(1, res.size());
        assertFalse(res.get(0).containsKey("propietario"));
        verify(jdbc).queryForList(contains("WHERE un.id_unidad = ?"), eq(1));
    }
}
