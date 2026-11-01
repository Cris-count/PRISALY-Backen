package com.prisaly.m4.domain.rule;

/**
 * Se lanza cuando se intenta una operación no permitida según
 * el estado actual de la sesión académica.
 */
public class SesionOperacionNoPermitidaException extends RuntimeException {

    private final String estadoActual;
    private final String operacion;

    public SesionOperacionNoPermitidaException(String estadoActual, String operacion) {
        super("Operación '%s' no permitida en estado '%s'".formatted(operacion, estadoActual));
        this.estadoActual = estadoActual;
        this.operacion = operacion;
    }

    public String getEstadoActual() {
        return estadoActual;
    }

    public String getOperacion() {
        return operacion;
    }
}
