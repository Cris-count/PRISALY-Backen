package com.prisaly.m6.application;

/** Fixed local error codes, without provider diagnostics or capability-bearing causes. */
public final class PreparacionContextoException extends RuntimeException {
    public enum Codigo {
        INTENTO_INEXISTENTE, UUID_INTENTO_INCONSISTENTE, INTENTO_NO_FINALIZADO,
        EMISOR_AUSENTE, EMISOR_NO_DISPONIBLE, CAPACIDAD_INVALIDA,
        CONTEXTO_INEXISTENTE, CASO_INCONSISTENTE, REFERENCIAS_INCONSISTENTES,
        FALLO_CONSULTA_INTENTO, FALLO_CONSULTA_CONTEXTO
    }

    private final Codigo codigo;

    public PreparacionContextoException(Codigo codigo) {
        super(java.util.Objects.requireNonNull(codigo, "codigo").name());
        this.codigo = codigo;
    }

    public Codigo codigo() { return codigo; }
}
