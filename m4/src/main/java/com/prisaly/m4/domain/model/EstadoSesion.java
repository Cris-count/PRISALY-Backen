package com.prisaly.m4.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Catálogo exacto de V002. No convierte estados propuestos. */
public enum EstadoSesion {
    SIN_INICIAR("SinIniciar"),
    EN_CURSO("EnCurso"),
    FINALIZADO("Finalizado");
    private final String valor;

    EstadoSesion(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String valorBd() {
        return valor;
    }

    @JsonCreator
    public static EstadoSesion desdeBd(String valor) {
        for (var estado : values()) if (estado.valor.equals(valor)) return estado;
        throw new IllegalArgumentException("Estado de sesión no reconocido");
    }
}
