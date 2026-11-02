package com.prisaly.m3.api;

/** Uniform security rejection without resource existence, capability or verifier diagnostics. */
public final class AccesoContextoAcademicoDenegadoException extends RuntimeException {
    public AccesoContextoAcademicoDenegadoException() {
        super("Academic context access denied");
    }
}
