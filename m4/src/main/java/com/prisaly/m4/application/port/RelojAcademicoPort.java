package com.prisaly.m4.application.port;

import java.time.Instant;

/**
 * Reloj de las marcas académicas de M4 que M5 compara con las suyas ({@code fechaInicioReal},
 * {@code fechaFinReal}).
 *
 * <p>C4 (DC-CLAUDE-BE-COMP-03): el instante procede de PostgreSQL ({@code clock_timestamp()})
 * dentro de la transacción que persiste la transición, el mismo reloj que ya usa M5 para
 * {@code Intento.inicio}/{@code fin}. No define límites, ingreso tardío ni cierre automático.
 */
@FunctionalInterface
public interface RelojAcademicoPort {

    /** Instante actual del reloj académico; nunca nulo. */
    Instant ahora();
}
