package com.cruceta.cuotas.auth;

/** Datos del usuario que inicio sesion. */
public record Sesion(int idUsuario, String nombreCompleto, String correo, String rol, Integer idUnidad, String unidad) {
    public boolean esAdmin() {
        return "ADMINISTRADOR".equals(rol);
    }
}
