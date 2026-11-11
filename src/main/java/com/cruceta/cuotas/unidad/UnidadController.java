package com.cruceta.cuotas.unidad;

import com.cruceta.cuotas.auth.AuthService;
import com.cruceta.cuotas.comun.ApiException;
import com.cruceta.cuotas.comun.Datos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** RF-002 Registrar unidad del edificio (departamentos). */
@RestController
@RequestMapping("/api/unidades")
public class UnidadController {

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public UnidadController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerir(a);
        return jdbc.queryForList("""
                SELECT un.id_unidad, un.bloque, un.numero_unidad, un.estado,
                       CONCAT(un.bloque, ' - Dpto. ', un.numero_unidad) AS unidad,
                       (SELECT CONCAT(u.nombre, ' ', u.apellido) FROM usuario u
                         WHERE u.id_unidad = un.id_unidad AND u.id_rol = 2 AND u.estado_cuenta = 'ACTIVO' LIMIT 1) AS propietario
                FROM unidad un ORDER BY un.bloque, un.numero_unidad""");
    }

    @PostMapping
    public Map<String, Object> registrar(@RequestHeader(value = "Authorization", required = false) String a,
                                         @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        String bloque = Datos.obligatorio(b, "bloque", "Bloque / Torre");
        String numero = Datos.obligatorio(b, "numero_unidad", "Numero de departamento");
        String estado = Datos.texto(b, "estado");
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM unidad WHERE bloque = ? AND numero_unidad = ?",
                Integer.class, bloque, numero);
        if (n != null && n > 0)
            throw ApiException.validacion("La unidad " + bloque + " - " + numero + " ya esta registrada.");
        jdbc.update("INSERT INTO unidad (bloque, numero_unidad, estado) VALUES (?,?,?)",
                bloque, numero, estado.isEmpty() ? "ACTIVA" : estado);
        return Map.of("mensaje", "Unidad " + bloque + " - " + numero + " registrada correctamente.");
    }

    /** Modificar unidad del edificio (bloque, numero_unidad, estado) */
    @PutMapping("/{id}")
    public Map<String, Object> modificar(@RequestHeader(value = "Authorization", required = false) String a,
                                         @PathVariable int id, @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        String bloque = Datos.texto(b, "bloque");
        String numero = Datos.texto(b, "numero_unidad");
        String estado = Datos.texto(b, "estado");

        if (bloque.isEmpty() && numero.isEmpty() && !estado.isEmpty()) {
            // Compatibilidad directa con cambio rápido de estado
            int n = jdbc.update("UPDATE unidad SET estado = ? WHERE id_unidad = ?", estado, id);
            if (n == 0) throw ApiException.noEncontrado("Unidad no encontrada.");
            return Map.of("mensaje", "Estado de la unidad actualizado.");
        }

        if (bloque.isEmpty()) throw ApiException.validacion("El piso o bloque es obligatorio.");
        if (numero.isEmpty()) throw ApiException.validacion("El número de departamento es obligatorio.");
        if (estado.isEmpty()) estado = "ACTIVA";

        Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM unidad WHERE bloque = ? AND numero_unidad = ? AND id_unidad <> ?",
                Integer.class, bloque, numero, id);
        if (dup != null && dup > 0)
            throw ApiException.validacion("Ya existe otra unidad registrada como " + bloque + " - " + numero + ".");

        int n = jdbc.update("UPDATE unidad SET bloque = ?, numero_unidad = ?, estado = ? WHERE id_unidad = ?",
                bloque, numero, estado, id);
        if (n == 0) throw ApiException.noEncontrado("Unidad no encontrada.");

        return Map.of("mensaje", "Unidad " + bloque + " - " + numero + " modificada correctamente.");
    }
}
