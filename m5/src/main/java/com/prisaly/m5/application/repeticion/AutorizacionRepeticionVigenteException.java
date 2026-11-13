package com.prisaly.m5.application.repeticion;

/** La participación ya tiene una autorización de repetición vigente sin consumir. */
public final class AutorizacionRepeticionVigenteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AutorizacionRepeticionVigenteException() {
        super("La participación ya tiene una autorización de repetición vigente");
    }
}
