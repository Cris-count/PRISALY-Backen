package com.prisaly.m5.application;

/**
 * Control de concurrencia del intento. El cliente manda la revisión que leyó;
 * si otro guardado ya la cambió, esta excepción impide pisar esas respuestas.
 */
public final class RevisionObsoletaException extends ParticipacionAplicacionException {
    public RevisionObsoletaException() {
        super("La revisión esperada no coincide con la revisión persistida");
    }
}
