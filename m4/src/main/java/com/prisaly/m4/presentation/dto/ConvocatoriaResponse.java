package com.prisaly.m4.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.prisaly.m4.domain.model.Convocatoria;

/**
 * DTO de respuesta para convocatorias.
 */
public record ConvocatoriaResponse(
        UUID id,
        UUID sesionId,
        UUID estudianteId,
        LocalDateTime convocadoEn,
        boolean confirmada
) {
    public static ConvocatoriaResponse from(Convocatoria c) {
        return new ConvocatoriaResponse(
                c.getId(), c.getSesionId(), c.getEstudianteId(),
                c.getConvocadoEn(), c.isConfirmada()
        );
    }
}
