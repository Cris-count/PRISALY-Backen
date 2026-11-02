package com.prisaly.m5.application;

/**
 * Iniciar exigiría un intento repetido, y la repetición no está habilitada
 * (DD-14 pendiente). Ocurre si la participación ya tiene un intento
 * finalizado, o si V005 ({@code prisaly_validar_intento}) detecta un intento
 * previo del mismo estudiante sobre el mismo caso en otra sesión.
 */
public final class RepeticionNoHabilitadaException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    public RepeticionNoHabilitadaException() {
        super("No se puede iniciar otro intento: la repetición no está habilitada (DD-14 pendiente)");
    }
}
