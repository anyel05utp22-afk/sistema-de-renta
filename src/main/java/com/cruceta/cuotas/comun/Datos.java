package com.cruceta.cuotas.comun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Utilidades para leer y validar datos de los formularios. */
public final class Datos {
    private Datos() {}

    public static String texto(Map<String, Object> body, String campo) {
        Object v = body.get(campo);
        return v == null ? "" : v.toString().trim();
    }

    public static String obligatorio(Map<String, Object> body, String campo, String etiqueta) {
        String v = texto(body, campo);
        if (v.isEmpty()) throw ApiException.validacion("Falta completar el campo obligatorio: " + etiqueta + ".");
        return v;
    }

    public static Integer entero(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try {
            return Integer.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            throw ApiException.validacion("Valor numerico invalido: " + v);
        }
    }

    public static BigDecimal monto(Object v, String etiqueta) {
        if (v == null || v.toString().isBlank()) throw ApiException.validacion("Falta el monto de: " + etiqueta + ".");
        try {
            return new BigDecimal(v.toString().trim()).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw ApiException.validacion("El monto de " + etiqueta + " no es un numero valido.");
        }
    }

    public static LocalDate fecha(String v, String etiqueta) {
        if (v == null || v.isBlank()) throw ApiException.validacion("Falta la fecha: " + etiqueta + ".");
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validacion("La fecha " + etiqueta + " no tiene un formato valido.");
        }
    }
}
