package com.prisaly.m1.domain.student;

import java.time.Duration;

/**
 * Ratified DD-02 session parameters (DD02-PLAN-IMPLEMENTACION-M1-M7 v1.1): absolute lifetime
 * of 8 h and 30 min of inactivity. Common to alternatives A and B.
 */
public final class PoliticaSesionEstudiante {

    public static final Duration VIGENCIA_MAXIMA = Duration.ofHours(8);
    public static final Duration INACTIVIDAD_MAXIMA = Duration.ofMinutes(30);

    private PoliticaSesionEstudiante() {
    }
}
