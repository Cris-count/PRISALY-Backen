package com.prisaly.m5.infrastructure.persistencia;

/**
 * El estado de un intento no puede traducirse entre el dominio y BD-005.2:
 * datos persistidos inconsistentes, valores que no son identificadores uuid o
 * un intento que no existe en la base al guardar.
 */
public final class PersistenciaIntentoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenciaIntentoException(String mensaje) {
        super(mensaje);
    }

    public PersistenciaIntentoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
