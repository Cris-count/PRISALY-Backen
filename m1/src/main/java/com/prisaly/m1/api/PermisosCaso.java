package com.prisaly.m1.api;

/** Códigos de permiso de casos autorizados en PD-01 y su ampliación. Fuente única. */
public final class PermisosCaso {
    /** Creación de borradores (CU-05). Ya existía como literal; aquí solo se centraliza (A7). */
    public static final String CREAR_CASO = "CREAR_CASO";
    public static final String CONSULTAR_CASO = "CONSULTAR_CASO";
    public static final String MODIFICAR_CASO = "MODIFICAR_CASO";
    public static final String PUBLICAR_CASO = "PUBLICAR_CASO";
    public static final String ARCHIVAR_CASO = "ARCHIVAR_CASO";

    private PermisosCaso() {
    }
}
