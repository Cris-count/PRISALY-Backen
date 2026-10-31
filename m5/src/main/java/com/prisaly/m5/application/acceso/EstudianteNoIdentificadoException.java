package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/** No hay un estudiante autenticado para la operación. */
public final class EstudianteNoIdentificadoException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    public EstudianteNoIdentificadoException() {
        super("La operación requiere un estudiante autenticado");
    }
}
