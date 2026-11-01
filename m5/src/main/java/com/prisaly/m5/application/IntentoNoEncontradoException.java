package com.prisaly.m5.application;

/** No existe un intento para la referencia indicada. */
public final class IntentoNoEncontradoException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    private final transient ReferenciaIntento referencia;

    public IntentoNoEncontradoException(ReferenciaIntento referencia) {
        super("No existe el intento " + referencia.valor());
        this.referencia = referencia;
    }

    public ReferenciaIntento referencia() {
        return referencia;
    }
}
