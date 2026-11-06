package com.prisaly.m5.application;

public final class RevisionObsoletaException extends ParticipacionAplicacionException {
    public RevisionObsoletaException() {
        super("La revisión esperada no coincide con la revisión persistida");
    }
}
