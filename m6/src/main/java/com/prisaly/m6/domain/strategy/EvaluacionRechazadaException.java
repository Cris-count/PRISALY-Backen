package com.prisaly.m6.domain.strategy;

import java.util.Objects;

/**
 * A strategy refused to evaluate because its input does not satisfy the strategy's preconditions
 * (for example incomplete or inconsistent weights). No result is produced, so nothing is recorded.
 */
public final class EvaluacionRechazadaException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final String codigo;

    public EvaluacionRechazadaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public String codigo() {
        return codigo;
    }
}
