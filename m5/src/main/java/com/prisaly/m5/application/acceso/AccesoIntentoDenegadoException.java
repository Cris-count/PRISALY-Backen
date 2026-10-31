package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/**
 * El estudiante autenticado no es el titular del intento. El mensaje no
 * revela quién es el titular.
 */
public final class AccesoIntentoDenegadoException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    public AccesoIntentoDenegadoException() {
        super("El intento no pertenece al estudiante autenticado");
    }
}
