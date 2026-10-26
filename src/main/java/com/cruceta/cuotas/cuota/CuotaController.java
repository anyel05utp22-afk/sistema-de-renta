package com.cruceta.cuotas.cuota;

import com.cruceta.cuotas.auth.AuthService;
import com.cruceta.cuotas.auth.Sesion;
import com.cruceta.cuotas.comun.Datos;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RF-010 Generar cuota mensual, RF-011 Calcular monto, RF-012 Consultar cuota,
 * RF-013 Estado de cuenta, RF-014 Aviso de cobro (datos), RF-015 Editar cuota,
 * RF-023 Detectar vencidas, RF-024 Morosidad, RF-041 Dashboard.
 */
@RestController
@RequestMapping("/api")
public class CuotaController {

    private final CuotaService cuotas;
    private final AuthService auth;

    public CuotaController(CuotaService cuotas, AuthService auth) {
        this.cuotas = cuotas;
        this.auth = auth;
    }

    @GetMapping("/cuotas")
    public List<Map<String, Object>> listar(@RequestHeader(value = "Authorization", required = false) String a,
                                            @RequestParam(required = false) String periodo,
                                            @RequestParam(required = false) String estado,
                                            @RequestParam(required = false) Integer idUnidad) {
        return cuotas.listar(auth.requerir(a), periodo, estado, idUnidad);
    }

    @GetMapping("/cuotas/periodos")
    public List<String> periodos(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerir(a);
        return cuotas.periodos();
    }

    /** Detalle de la cuota (tambien se usa para el aviso de cobro RF-014). */
    @GetMapping("/cuotas/{id}")
    public Map<String, Object> obtener(@RequestHeader(value = "Authorization", required = false) String a, @PathVariable int id) {
        return cuotas.obtener(auth.requerir(a), id);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/cuotas/calcular")
    public Map<String, Object> calcular(@RequestHeader(value = "Authorization", required = false) String a,
                                        @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        return cuotas.calcular((List<Map<String, Object>>) b.get("gastos"));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/cuotas/generar")
    public Map<String, Object> generar(@RequestHeader(value = "Authorization", required = false) String a,
                                       @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        return cuotas.generar(Datos.texto(b, "periodo"), Datos.texto(b, "fecha_vencimiento"),
                (List<Map<String, Object>>) b.get("gastos"));
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/cuotas/{id}")
    public Map<String, Object> editar(@RequestHeader(value = "Authorization", required = false) String a,
                                      @PathVariable int id, @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        return cuotas.editar(id, Datos.texto(b, "fecha_vencimiento"), (List<Map<String, Object>>) b.get("detalles"));
    }

    @PostMapping("/cuotas/detectar-vencidas")
    public Map<String, Object> detectar(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerirAdmin(a);
        int n = cuotas.detectarVencidas();
        return Map.of("mensaje", n == 0 ? "No se detectaron nuevas cuotas vencidas." : "Se marcaron " + n + " cuota(s) como VENCIDA.");
    }

    @GetMapping("/estado-cuenta")
    public Map<String, Object> estadoCuenta(@RequestHeader(value = "Authorization", required = false) String a,
                                            @RequestParam(required = false) Integer idUnidad) {
        Sesion s = auth.requerir(a);
        return cuotas.estadoCuenta(s, idUnidad);
    }

    @GetMapping("/morosidad")
    public List<Map<String, Object>> morosidad(@RequestHeader(value = "Authorization", required = false) String a) {
        auth.requerirAdmin(a);
        return cuotas.morosidad();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader(value = "Authorization", required = false) String a,
                                         @RequestParam(required = false) String periodo) {
        auth.requerirAdmin(a);
        return cuotas.dashboard(periodo);
    }
}
