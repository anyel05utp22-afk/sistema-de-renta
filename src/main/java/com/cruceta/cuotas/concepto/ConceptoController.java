package com.cruceta.cuotas.concepto;

import com.cruceta.cuotas.auth.AuthService;
import com.cruceta.cuotas.comun.ApiException;
import com.cruceta.cuotas.comun.Datos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** RF-009 Configurar conceptos de cobro (gastos del edificio). */
@RestController
@RequestMapping("/api/conceptos")
public class ConceptoController {

    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public ConceptoController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerir(a);
        return jdbc.queryForList("SELECT id_concepto, nombre_concepto, tipo_concepto, monto_base FROM concepto_cobro ORDER BY id_concepto");
    }

    @PostMapping
    public Map<String, Object> crear(@RequestHeader(value = "Authorization", required = false) String a,
                                     @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        String nombre = Datos.obligatorio(b, "nombre_concepto", "Nombre del concepto");
        String tipo = Datos.obligatorio(b, "tipo_concepto", "Tipo");
        BigDecimal monto = validarMonto(b);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM concepto_cobro WHERE LOWER(nombre_concepto) = LOWER(?)", Integer.class, nombre);
        if (n != null && n > 0) throw ApiException.validacion("El concepto \"" + nombre + "\" ya existe.");
        jdbc.update("INSERT INTO concepto_cobro (nombre_concepto, tipo_concepto, monto_base) VALUES (?,?,?)", nombre, tipo, monto);
        return Map.of("mensaje", "Concepto registrado correctamente.");
    }

    @PutMapping("/{id}")
    public Map<String, Object> editar(@RequestHeader(value = "Authorization", required = false) String a,
                                      @PathVariable int id, @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        String nombre = Datos.obligatorio(b, "nombre_concepto", "Nombre del concepto");
        String tipo = Datos.obligatorio(b, "tipo_concepto", "Tipo");
        BigDecimal monto = validarMonto(b);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM concepto_cobro WHERE LOWER(nombre_concepto) = LOWER(?) AND id_concepto <> ?",
                Integer.class, nombre, id);
        if (n != null && n > 0) throw ApiException.validacion("Ya existe otro concepto con ese nombre.");
        jdbc.update("UPDATE concepto_cobro SET nombre_concepto = ?, tipo_concepto = ?, monto_base = ? WHERE id_concepto = ?",
                nombre, tipo, monto, id);
        return Map.of("mensaje", "Concepto actualizado correctamente.");
    }

    private BigDecimal validarMonto(Map<String, Object> b) {
        BigDecimal m = Datos.monto(b.get("monto_base"), "Monto base");
        if (m.signum() < 0) throw ApiException.validacion("El monto base no puede ser negativo.");
        return m;
    }
}
