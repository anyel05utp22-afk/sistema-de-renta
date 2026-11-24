package com.cruceta.cuotas.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/** Convierte las excepciones en respuestas JSON {"error": "..."} que la interfaz muestra en rojo. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> duplicado(DuplicateKeyException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "El registro ya existe (dato duplicado)."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> archivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "El archivo supera el tamano maximo permitido (5 MB)."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> bd(DataAccessException e) {
        log.error("Error de acceso a base de datos", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Error de base de datos. Intente nuevamente."));
    }
}
