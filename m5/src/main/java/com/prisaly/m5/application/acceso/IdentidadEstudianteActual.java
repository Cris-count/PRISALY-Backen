package com.prisaly.m5.application.acceso;

import java.util.Optional;

/**
 * Puerto PROVISIONAL de M5 (lado consumidor): el estudiante autenticado en
 * la operación en curso.
 *
 * <p>No tiene implementación de producción. Deberá implementarse sobre el
 * contrato que M1 defina al ratificarse DD-02, sin depender de Spring
 * Security. Mientras no exista, ninguna operación de estudiante puede
 * exponerse.
 */
public interface IdentidadEstudianteActual {

    /** Estudiante autenticado; vacío si no hay uno. */
    Optional<IdentificadorEstudiante> actual();
}
