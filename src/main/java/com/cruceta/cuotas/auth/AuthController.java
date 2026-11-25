package com.cruceta.cuotas.auth;

import com.cruceta.cuotas.comun.Datos;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final RecuperacionService recuperacion;

    public AuthController(AuthService auth, RecuperacionService recuperacion) {
        this.auth = auth;
        this.recuperacion = recuperacion;
    }

    /** RF-003 Iniciar sesion */
    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        return auth.iniciarSesion(Datos.texto(body, "correo"), Datos.texto(body, "contrasena"));
    }

    /** RF-004 Cerrar sesion */
    @PostMapping("/auth/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.cerrarSesion(a);
        return Map.of("mensaje", "Sesion cerrada correctamente.");
    }

    @GetMapping("/auth/me")
    public Sesion me(@RequestHeader(value = "Authorization", required = false) String a) {
        return auth.requerirPermitirCambioContrasena(a);
    }

    /** Cambio obligatorio de contrasena temporal en el primer ingreso. */
    @PostMapping("/auth/cambiar-contrasena")
    public Map<String, Object> cambiarContrasena(@RequestHeader(value = "Authorization", required = false) String a,
                                                 @RequestBody Map<String, Object> body) {
        return auth.cambiarContrasenaObligatoria(a,
                Datos.texto(body, "contrasena_actual"),
                Datos.texto(body, "contrasena_nueva"));
    }

    // ===================== RF-005 Recuperacion de contrasena =====================

    @PostMapping("/auth/recuperar/estado")
    public Map<String, Object> estadoRecuperacion(@RequestBody Map<String, Object> body) {
        return recuperacion.consultarEstado(Datos.texto(body, "correo"));
    }

    @PostMapping(value = "/auth/recuperar/solicitar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> solicitarRecuperacion(@RequestParam(value = "correo", required = false) String correo,
                                                     @RequestParam(value = "archivo", required = false) MultipartFile archivo) {
        return recuperacion.solicitar(correo, archivo);
    }

    @PostMapping("/auth/recuperar/confirmar")
    public Map<String, Object> confirmarRecuperacion(@RequestBody Map<String, Object> body) {
        return recuperacion.confirmarNuevaContrasena(
                Datos.texto(body, "correo"),
                Datos.texto(body, "contrasena_nueva"),
                Datos.texto(body, "confirmar_contrasena"));
    }

    // ===================== Panel Admin: Alerta de seguridad =====================

    @GetMapping("/seguridad/solicitudes")
    public List<Map<String, Object>> listarSolicitudes(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerirAdmin(a);
        return recuperacion.listarSolicitudes();
    }

    @GetMapping("/seguridad/solicitudes/{id}/comprobante")
    public ResponseEntity<byte[]> comprobanteSolicitud(@RequestHeader(value = "Authorization", required = false) String a,
                                                       @PathVariable int id) {
        auth.requerirAdmin(a);
        Map<String, Object> f = recuperacion.obtenerArchivoPrueba(id);
        byte[] datos = (byte[]) f.get("archivo_datos");
        String tipo = String.valueOf(f.get("archivo_tipo"));
        String nombre = String.valueOf(f.get("archivo_nombre"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre + "\"")
                .body(datos);
    }

    @PostMapping("/seguridad/solicitudes/{id}/aprobar")
    public Map<String, Object> aprobarSolicitud(@RequestHeader(value = "Authorization", required = false) String a,
                                                @PathVariable int id) {
        Sesion s = auth.requerirAdmin(a);
        return recuperacion.aprobar(id, s.idUsuario());
    }

    @PostMapping("/seguridad/solicitudes/{id}/rechazar")
    public Map<String, Object> rechazarSolicitud(@RequestHeader(value = "Authorization", required = false) String a,
                                                 @PathVariable int id,
                                                 @RequestBody Map<String, Object> body) {
        Sesion s = auth.requerirAdmin(a);
        return recuperacion.rechazar(id, s.idUsuario(), Datos.texto(body, "motivo"));
    }
}
