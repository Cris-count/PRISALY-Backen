package com.prisaly.m1.api;

/**
 * Catálogo de códigos de permiso de casos. Los controladores y el puerto de autorización
 * comparan estos literales; no hay otro sitio donde se definan.
 */
public final class PermisosCaso {
    /** Permite POST /v1/casos y deja el caso en BORRADOR. */
    public static final String CREAR_CASO = "CREAR_CASO";
    /** Permite leer la ficha y el contenido del caso propio. */
    public static final String CONSULTAR_CASO = "CONSULTAR_CASO";
    /** Permite parche, escenarios, preguntas, opciones y rúbrica mientras sigue en borrador. */
    public static final String MODIFICAR_CASO = "MODIFICAR_CASO";
    /** Permite pasar el borrador a PUBLICADO si el contenido está completo. */
    public static final String PUBLICAR_CASO = "PUBLICAR_CASO";
    /** Permite pasar un caso publicado a ARCHIVADO. */
    public static final String ARCHIVAR_CASO = "ARCHIVAR_CASO";

    private PermisosCaso() {
    }
}
