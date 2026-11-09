package com.prisaly.m1.domain.model;

/** Permiso del catálogo institucional. */
public final class Permiso {
    private final Integer idPermiso;
    private final String codigo;
    private final String nombre;
    private final String descripcion;

    public Permiso(Integer idPermiso, String codigo, String nombre, String descripcion) {
        this.idPermiso = idPermiso;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Integer getIdPermiso() {
        return idPermiso;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
