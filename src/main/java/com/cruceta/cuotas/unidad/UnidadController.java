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
                       CONCAT(un.bloque, ' - ', un.numero_unidad) AS unidad,
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

    @PutMapping("/{id}")
    public Map<String, Object> cambiarEstado(@RequestHeader(value = "Authorization", required = false) String a,
                                             @PathVariable int id, @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        String estado = Datos.obligatorio(b, "estado", "Estado");
        jdbc.update("UPDATE unidad SET estado = ? WHERE id_unidad = ?", estado, id);
        return Map.of("mensaje", "Estado de la unidad actualizado.");
    }
}
