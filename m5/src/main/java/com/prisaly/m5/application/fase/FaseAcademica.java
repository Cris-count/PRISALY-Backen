package com.prisaly.m5.application.fase;

/**
 * Fase académica de un intento (DC-BE-SIM-B1-B2A-24, Incremento B, candidato). {@link #SIN_FASES} es el intento sin
 * fila {@code "FaseIntento"}: conserva exactamente el comportamiento anterior de M5 (invariantes C1/C2).
 */
public enum FaseAcademica {
    SIN_FASES,
    EXPLORACION,
    CUESTIONARIO
}
