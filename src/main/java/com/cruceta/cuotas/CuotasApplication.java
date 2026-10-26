package com.cruceta.cuotas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sistema Web "Edificio La Cruceta" - Cobro de cuotas de mantenimiento.
 * Arquitectura (igual que los diagramas de secuencia del proyecto):
 *   Interfaz (React, carpeta static)  ->  Control (controllers / services)  ->  Entidad (MySQL)
 */
@SpringBootApplication
public class CuotasApplication {
    public static void main(String[] args) {
        SpringApplication.run(CuotasApplication.class, args);
        System.out.println("\n>>> Sistema listo: abre http://localhost:8080 en tu navegador\n");
    }
}
