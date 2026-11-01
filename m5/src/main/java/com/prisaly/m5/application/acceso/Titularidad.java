package com.prisaly.m5.application.acceso;

/**
 * Relación entre un estudiante autenticado y un intento, resuelta por el
 * servidor. No distingue más de lo necesario para fallar de forma cerrada.
 */
public enum Titularidad {
    /** El intento existe y pertenece al estudiante. */
    PROPIO,
    /** El intento existe, pero su convocatoria no es del estudiante. */
    AJENO,
    /** No existe un intento para la referencia. */
    INEXISTENTE
}
