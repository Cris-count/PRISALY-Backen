package com.prisaly.m4.domain.rule;

/**
 * Se lanza cuando se viola una regla de negocio en M4.
 */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;

    public ReglaNegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
