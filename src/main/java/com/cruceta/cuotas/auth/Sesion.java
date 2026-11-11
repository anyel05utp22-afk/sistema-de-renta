package com.cruceta.cuotas.auth;

/** Datos del usuario que inicio sesion. */
public record Sesion(int idUsuario, String nombreCompleto, String correo, String rol, Integer idUnidad, String unidad) {
    public boolean esAdmin() {
        return "ADMINISTRADOR".equalsIgnoreCase(rol);
    }

    public boolean esJunta() {
        return "JUNTA DIRECTIVA".equalsIgnoreCase(rol) || "JUNTA".equalsIgnoreCase(rol);
    }

    public boolean tieneAccesoGestion() {
        return esAdmin() || esJunta();
    }
}

