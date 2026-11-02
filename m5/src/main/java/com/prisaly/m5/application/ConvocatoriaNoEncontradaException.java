package com.prisaly.m5.application;

/**
 * No hay una convocatoria disponible para el estudiante autenticado: no existe
 * o es de otro estudiante. M4 no distingue ambos casos para no revelar
 * convocatorias ajenas, y M5 tampoco. El mensaje no repite la referencia.
 */
public final class ConvocatoriaNoEncontradaException extends ParticipacionAplicacionException {

    private static final long serialVersionUID = 1L;

    public ConvocatoriaNoEncontradaException() {
        super("No hay una convocatoria disponible para el estudiante autenticado");
    }
}
