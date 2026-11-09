package com.prisaly.m1.domain.model;

/** Rol institucional de una cuenta. */
public final class Rol {
    private final Integer idRol;
    private final String nombre;
    private final String descripcion;

    public Rol(Integer idRol, String nombre, String descripcion) {
        this.idRol = idRol;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Integer getIdRol() {
        return idRol;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
