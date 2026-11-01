package com.prisaly.m4.infrastructure.persistence;

import com.prisaly.m4.domain.model.EstadoSesion;

import jakarta.persistence.*;

@Converter
public class EstadoSesionConverter implements AttributeConverter<EstadoSesion, String> {
    public String convertToDatabaseColumn(EstadoSesion e) {
        return e == null ? null : e.valorBd();
    }

    public EstadoSesion convertToEntityAttribute(String v) {
        return v == null ? null : EstadoSesion.desdeBd(v);
    }
}
