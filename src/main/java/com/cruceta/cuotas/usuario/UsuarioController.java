package com.cruceta.cuotas.usuario;

import com.cruceta.cuotas.auth.AuthService;
import com.cruceta.cuotas.auth.Sesion;
import com.cruceta.cuotas.comun.ApiException;
import com.cruceta.cuotas.comun.Datos;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RF-001 Registrar propietario o residente, RF-006 Asignar roles,
 * RF-007 Actualizar datos de perfil, RF-008 Desactivar usuario.
 */
@RestController
@RequestMapping("/api")
public class UsuarioController {

    private static final String SELECT_USUARIO = """
            SELECT u.id_usuario, u.dni, u.nombre, u.apellido,
                   DATE_FORMAT(u.fecha_nacimiento, '%Y-%m-%d') AS fecha_nacimiento,
                   u.telefono, u.correo, u.direccion, u.id_rol, r.nombre_rol, u.id_unidad,
                   CONCAT(un.bloque, ' - ', un.numero_unidad) AS unidad, u.estado_cuenta
            FROM usuario u JOIN rol r ON r.id_rol = u.id_rol
            LEFT JOIN unidad un ON un.id_unidad = u.id_unidad
            """;

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public UsuarioController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerir(a);
        return jdbc.queryForList("SELECT id_rol, nombre_rol FROM rol ORDER BY id_rol");
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> listar(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerirAdmin(a);
        return jdbc.queryForList(SELECT_USUARIO + " ORDER BY u.id_rol, un.bloque, un.numero_unidad");
    }

    /** RF-001 Registrar propietario o residente */
    @PostMapping("/usuarios")
    public Map<String, Object> registrar(@RequestHeader(value = "Authorization", required = false) String a,
                                         @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        String nombre = Datos.obligatorio(b, "nombre", "Nombres");
        String apellido = Datos.obligatorio(b, "apellido", "Apellidos");
        String dni = Datos.obligatorio(b, "dni", "DNI");
        String correo = Datos.obligatorio(b, "correo", "Correo electronico");
        String telefono = Datos.texto(b, "telefono");
        String direccion = Datos.texto(b, "direccion");
        String fechaNac = Datos.texto(b, "fecha_nacimiento");
        Integer idRol = Datos.entero(b.get("id_rol"));
        Integer idUnidad = Datos.entero(b.get("id_unidad"));
        String contrasena = Datos.texto(b, "contrasena");

        validarFormato(dni, correo, telefono);
        if (idRol == null) throw ApiException.validacion("Seleccione el tipo de usuario (rol).");
        if (idRol != 1 && idUnidad == null)
            throw ApiException.validacion("Seleccione el departamento (unidad) del propietario o residente.");

        // Flujo alternativo: "si el identificador ya existe, el sistema impide duplicarlo"
        if (existe("SELECT COUNT(*) FROM usuario WHERE dni = ?", dni))
            throw ApiException.validacion("Ya existe un usuario registrado con el DNI " + dni + ".");
        if (existe("SELECT COUNT(*) FROM usuario WHERE correo = ?", correo))
            throw ApiException.validacion("Ya existe un usuario registrado con el correo " + correo + ".");
        // Cada departamento tiene un solo duenio
        if (idRol == 2 && existe("SELECT COUNT(*) FROM usuario WHERE id_unidad = ? AND id_rol = 2 AND estado_cuenta = 'ACTIVO'", idUnidad))
            throw ApiException.validacion("Ese departamento ya tiene un propietario activo registrado.");

        jdbc.update("""
                INSERT INTO usuario (dni, nombre, apellido, fecha_nacimiento, telefono, correo, direccion,
                                     contrasena, id_rol, id_unidad, estado_cuenta)
                VALUES (?,?,?,?,?,?,?,?,?,?, 'ACTIVO')""",
                dni, nombre, apellido, fechaNac.isEmpty() ? null : fechaNac, telefono, correo, direccion,
                AuthService.hash(contrasena.isEmpty() ? dni : contrasena), idRol, idRol == 1 ? null : idUnidad);

        return Map.of("mensaje", "Usuario registrado correctamente. Su contrasena inicial es "
                + (contrasena.isEmpty() ? "su DNI." : "la indicada en el formulario."));
    }

    /** RF-006 Asignar roles de usuario */
    @PutMapping("/usuarios/{id}/rol")
    public Map<String, Object> asignarRol(@RequestHeader(value = "Authorization", required = false) String a,
                                          @PathVariable int id, @RequestBody Map<String, Object> b) {
        Sesion s = auth.requerirAdmin(a);
        Integer idRol = Datos.entero(b.get("id_rol"));
        if (idRol == null) throw ApiException.validacion("Seleccione un rol.");
        if (id == s.idUsuario()) throw ApiException.validacion("No puede cambiar su propio rol.");
        int n = jdbc.update("UPDATE usuario SET id_rol = ? WHERE id_usuario = ?", idRol, id);
        if (n == 0) throw ApiException.noEncontrado("Usuario no encontrado.");
        return Map.of("mensaje", "Rol asignado correctamente.");
    }

    /** RF-008 Desactivar (o reactivar) usuario */
    @PutMapping("/usuarios/{id}/estado")
    public Map<String, Object> cambiarEstado(@RequestHeader(value = "Authorization", required = false) String a,
                                             @PathVariable int id, @RequestBody Map<String, Object> b) {
        Sesion s = auth.requerirAdmin(a);
        String estado = Datos.obligatorio(b, "estado", "Estado");
        boolean confirmar = Boolean.parseBoolean(Datos.texto(b, "confirmar"));
        if (id == s.idUsuario()) throw ApiException.validacion("No puede desactivar su propia cuenta.");

        if ("INACTIVO".equals(estado) && !confirmar) {
            Integer pendientes = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM cuota c JOIN usuario u ON u.id_unidad = c.id_unidad
                    WHERE u.id_usuario = ? AND c.estado_cuota IN ('PENDIENTE','VENCIDA')""", Integer.class, id);
            if (pendientes != null && pendientes > 0)
                // Flujo alternativo: advertir operaciones pendientes antes de desactivar
                throw new ApiException(HttpStatus.CONFLICT,
                        "El usuario tiene " + pendientes + " cuota(s) pendiente(s) o vencida(s). Desea desactivarlo de todas formas?");
        }
        jdbc.update("UPDATE usuario SET estado_cuenta = ? WHERE id_usuario = ?", estado, id);
        return Map.of("mensaje", "INACTIVO".equals(estado) ? "Usuario desactivado." : "Usuario reactivado.");
    }

    @GetMapping("/perfil")
    public Map<String, Object> perfil(@RequestHeader(value = "Authorization", required = false) String a) {
        Sesion s = auth.requerir(a);
        return jdbc.queryForMap(SELECT_USUARIO + " WHERE u.id_usuario = ?", s.idUsuario());
    }

    /** RF-007 Actualizar datos de perfil */
    @PutMapping("/perfil")
    public Map<String, Object> actualizarPerfil(@RequestHeader(value = "Authorization", required = false) String a,
                                                @RequestBody Map<String, Object> b) {
        Sesion s = auth.requerir(a);
        String correo = Datos.obligatorio(b, "correo", "Correo electronico");
        String telefono = Datos.texto(b, "telefono");
        String direccion = Datos.texto(b, "direccion");
        validarFormato(null, correo, telefono);
        if (existe("SELECT COUNT(*) FROM usuario WHERE correo = ? AND id_usuario <> " + s.idUsuario(), correo))
            throw ApiException.validacion("El correo ya esta siendo usado por otro usuario.");

        jdbc.update("UPDATE usuario SET correo = ?, telefono = ?, direccion = ? WHERE id_usuario = ?",
                correo, telefono, direccion, s.idUsuario());

        String nueva = Datos.texto(b, "contrasena_nueva");
        if (!nueva.isEmpty()) {
            String actual = Datos.texto(b, "contrasena_actual");
            String guardada = jdbc.queryForObject("SELECT contrasena FROM usuario WHERE id_usuario = ?", String.class, s.idUsuario());
            if (!AuthService.hash(actual).equals(guardada))
                throw ApiException.validacion("La contrasena actual no es correcta.");
            if (nueva.length() < 6) throw ApiException.validacion("La nueva contrasena debe tener al menos 6 caracteres.");
            jdbc.update("UPDATE usuario SET contrasena = ? WHERE id_usuario = ?", AuthService.hash(nueva), s.idUsuario());
        }
        return Map.of("mensaje", "Perfil actualizado correctamente.");
    }

    // ---------------------------------------------------------------
    private void validarFormato(String dni, String correo, String telefono) {
        // Flujo alternativo RF-007: "si el formato de un dato es invalido, solicita corregirlo"
        if (dni != null && !dni.matches("\\d{8}")) throw ApiException.validacion("El DNI debe tener 8 digitos.");
        if (!correo.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$"))
            throw ApiException.validacion("El correo electronico no tiene un formato valido.");
        if (!telefono.isEmpty() && !telefono.matches("9\\d{8}"))
            throw ApiException.validacion("El telefono debe tener 9 digitos y empezar con 9.");
    }

    private boolean existe(String sql, Object param) {
        Integer n = jdbc.queryForObject(sql, Integer.class, param);
        return n != null && n > 0;
    }
}
