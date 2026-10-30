package com.prisaly.m4.domain.model;

/**
 * Estados posibles de una sesión académica.
 *
 * <p>PROPUESTO — los estados no han sido aprobados formalmente por el equipo.
 * Los nombres se basan en el flujo académico natural:
 * PROGRAMADA → ABIERTA → EN_CURSO → CERRADA, con CANCELADA como terminal alternativo.</p>
 */
public enum EstadoSesion {

    /** Sesión creada pero aún no abierta para convocatorias. */
    PROGRAMADA,

    /** Sesión abierta: se aceptan convocatorias e inscripciones. */
    ABIERTA,

    /** Sesión en curso: los estudiantes están participando activamente. */
    EN_CURSO,

    /** Sesión cerrada: no se aceptan más participaciones. */
    CERRADA,

    /** Sesión cancelada: estado terminal, no se puede revertir. */
    CANCELADA
}
