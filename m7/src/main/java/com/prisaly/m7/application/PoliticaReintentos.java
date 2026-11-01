package com.prisaly.m7.application;

import java.time.Duration;
import java.util.Objects;

/**
 * Ratified retry policy (D4): max 5 claims; delays 1, 2, 4, 8 minutes; cap 60 minutes.
 * Pure functions; no I/O.
 */
public final class PoliticaReintentos {

    public static final int MAX_INTENTOS = 5;
    public static final long TOPE_MINUTOS = 60L;

    private final int maxIntentos;
    private final long topeMinutos;

    public PoliticaReintentos(int maxIntentos, long topeMinutos) {
        if (maxIntentos < 1) {
            throw new IllegalArgumentException("maxIntentos must be >= 1");
        }
        if (topeMinutos < 1) {
            throw new IllegalArgumentException("topeMinutos must be >= 1");
        }
        this.maxIntentos = maxIntentos;
        this.topeMinutos = topeMinutos;
    }

    public static PoliticaReintentos ratificada() {
        return new PoliticaReintentos(MAX_INTENTOS, TOPE_MINUTOS);
    }

    public int maxIntentos() {
        return maxIntentos;
    }

    public boolean agotoIntentos(int numeroIntentos) {
        return numeroIntentos >= maxIntentos;
    }

    /**
     * Wait after a failed attempt {@code numeroIntentos} (1-based claim count) before the next claim.
     * Attempt 1→1m, 2→2m, 3→4m, 4→8m, capped at {@code topeMinutos}.
     */
    public Duration esperaTrasIntento(int numeroIntentos) {
        if (numeroIntentos < 1) {
            throw new IllegalArgumentException("numeroIntentos must be >= 1");
        }
        long minutos = Math.min(1L << (numeroIntentos - 1), topeMinutos);
        return Duration.ofMinutes(minutos);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PoliticaReintentos that)) {
            return false;
        }
        return maxIntentos == that.maxIntentos && topeMinutos == that.topeMinutos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxIntentos, topeMinutos);
    }
}
