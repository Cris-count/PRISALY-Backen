package com.prisaly.m4.infrastructure.persistence;

import com.prisaly.m4.domain.model.EstadoSesion;

import jakarta.persistence.*;

/**
 * Conversor JPA del estado. En la base el valor es el texto SinIniciar, EnCurso o Finalizado.
 * En el dominio es el enum {@link EstadoSesion}, del que la fábrica arma el objeto State.
 * No aplica reglas: solo traduce la columna.
 */
@Converter
public class EstadoSesionConverter implements AttributeConverter<EstadoSesion, String> {
    public String convertToDatabaseColumn(EstadoSesion e) {
        return e == null ? null : e.valorBd();
    }

    public EstadoSesion convertToEntityAttribute(String v) {
        return v == null ? null : EstadoSesion.desdeBd(v);
    }
}
