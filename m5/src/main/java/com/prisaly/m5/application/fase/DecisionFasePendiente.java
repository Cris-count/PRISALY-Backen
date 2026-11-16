package com.prisaly.m5.application.fase;

/**
 * Decisiones funcionales del Incremento B que Dirección Central no ha resuelto (DC-BE-SIM-DECISIONES-23) y que B2a
 * alcanza. Cada una se evalúa en un único punto del código; mientras no se ratifique, ese punto falla cerrado con
 * {@link DecisionFaseNoRatificadaException} en lugar de elegir en silencio una alternativa.
 *
 * <p>D-SIM-2 ({@code /contenido} en exploración) y D-SIM-4 (finalizar desde exploración) no se alcanzan en B2a: esas
 * rutas no cambian y sus puntos de decisión corresponden a B2b.
 */
public enum DecisionFasePendiente {
    /** ¿Se puede entrar al cuestionario después de {@code limiteTemporal}? */
    D_SIM_3,
    /** ¿Se puede volver a leer la narrativa de un intento finalizado? */
    D_SIM_10
}
