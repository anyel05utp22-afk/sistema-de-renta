package com.cruceta.cuotas.auth;

import com.cruceta.cuotas.comun.Datos;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** RF-003 Iniciar sesion */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        return auth.iniciarSesion(Datos.texto(body, "correo"), Datos.texto(body, "contrasena"));
    }

    /** RF-004 Cerrar sesion */
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.cerrarSesion(a);
        return Map.of("mensaje", "Sesion cerrada correctamente.");
    }

    @GetMapping("/me")
    public Sesion me(@RequestHeader(value = "Authorization", required = false) String a) {
        return auth.requerir(a);
    }
}
