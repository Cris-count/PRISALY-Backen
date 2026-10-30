package com.prisaly.m5.application;

/** Un dato obligatorio para un caso de uso de M5 es nulo o está vacío. */
public final class DatoAplicacionInvalidoException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    public DatoAplicacionInvalidoException(String mensaje) {
        super(mensaje);
    }
}
