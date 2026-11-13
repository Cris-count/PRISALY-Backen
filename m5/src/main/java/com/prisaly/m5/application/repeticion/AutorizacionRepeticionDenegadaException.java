package com.prisaly.m5.application.repeticion;

/** Solo el docente que programó la sesión puede autorizar la repetición. */
public final class AutorizacionRepeticionDenegadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AutorizacionRepeticionDenegadaException() {
        super("Solo el docente que programó la sesión puede autorizar la repetición");
    }
}
