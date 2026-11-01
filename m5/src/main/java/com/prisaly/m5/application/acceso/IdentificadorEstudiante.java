package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;

/**
 * Identificador opaco del estudiante. PROVISIONAL: su forma depende de DD-02
 * y del contrato de identidad de M1, que hoy solo cubre al personal.
 */
public record IdentificadorEstudiante(String valor) {

    public IdentificadorEstudiante {
        if (valor == null || valor.isBlank()) {
            throw new DatoAplicacionInvalidoException("El identificador del estudiante es obligatorio");
        }
    }
}
