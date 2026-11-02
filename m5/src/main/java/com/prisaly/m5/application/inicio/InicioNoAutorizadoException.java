package com.prisaly.m5.application.inicio;

import com.prisaly.m5.application.ParticipacionAplicacionException;

/**
 * La regla de elegibilidad ratificada determina que el estudiante no puede
 * iniciar. Hoy no se produce: la regla sigue pendiente
 * ({@link ElegibilidadNoRatificada}).
 */
public final class InicioNoAutorizadoException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    public InicioNoAutorizadoException() {
        super("El estudiante no cumple las condiciones para iniciar el intento");
    }
}
