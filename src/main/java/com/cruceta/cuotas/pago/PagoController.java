package com.cruceta.cuotas.pago;

import com.cruceta.cuotas.auth.AuthService;
import com.cruceta.cuotas.comun.Datos;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PagoController {

    private final PagoService pagos;
    private final AuthService auth;

    public PagoController(PagoService pagos, AuthService auth) {
        this.pagos = pagos;
        this.auth = auth;
    }

    /** RF-018 Consultar pagos realizados / RF-048 Historial por unidad (idUnidad). */
    @GetMapping("/pagos")
    public List<Map<String, Object>> listar(@RequestHeader(value = "Authorization", required = false) String a,
                                            @RequestParam(required = false) String estado,
                                            @RequestParam(required = false) Integer idUnidad) {
        return pagos.listar(auth.requerir(a), estado, idUnidad);
    }

    /** RF-016 Registrar pago + RF-017 Cargar comprobante (multipart/form-data). */
    @PostMapping(value = "/pagos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> registrar(@RequestHeader(value = "Authorization", required = false) String a,
                                         @RequestParam(value = "id_cuota", required = false) Integer idCuota,
                                         @RequestParam(value = "monto_pagado", required = false) String monto,
                                         @RequestParam(value = "metodo_pago", required = false) String metodo,
                                         @RequestParam(value = "nro_operacion", required = false) String nroOperacion,
                                         @RequestParam(value = "fecha_pago", required = false) String fecha,
                                         @RequestParam(value = "archivo", required = false) MultipartFile archivo) {
        return pagos.registrar(auth.requerir(a), idCuota, monto, metodo, nroOperacion, fecha, archivo);
    }

    /** RF-019 Validar comprobante de pago */
    @PostMapping("/pagos/{id}/validar")
    public Map<String, Object> validar(@RequestHeader(value = "Authorization", required = false) String a, @PathVariable int id) {
        auth.requerirAdmin(a);
        return pagos.validar(id);
    }

    /** RF-020 Rechazar comprobante de pago */
    @PostMapping("/pagos/{id}/rechazar")
    public Map<String, Object> rechazar(@RequestHeader(value = "Authorization", required = false) String a,
                                        @PathVariable int id, @RequestBody Map<String, Object> b) {
        auth.requerirAdmin(a);
        return pagos.rechazar(id, Datos.texto(b, "motivo"));
    }

    /** RF-022 Consultar constancias de pago */
    @GetMapping("/recibos")
    public List<Map<String, Object>> recibos(@RequestHeader(value = "Authorization", required = false) String a) {
        return pagos.recibos(auth.requerir(a));
    }

    /** RF-049 Consultar comprobante de pago (el token va como parametro para abrirlo en otra pestania). */
    @GetMapping("/comprobantes/{id}/archivo")
    public ResponseEntity<Resource> archivo(@PathVariable int id, @RequestParam String token) {
        Path f = pagos.archivoComprobante(auth.requerir("Bearer " + token), id);
        MediaType tipo = MediaTypeFactory.getMediaType(f.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.getFileName() + "\"")
                .body(new FileSystemResource(f));
    }
}
