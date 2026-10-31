package com.prisaly.m4.presentation.dto;

import com.prisaly.m4.domain.model.Convocatoria;

import java.time.Instant;
import java.util.UUID;

public record ConvocatoriaResponse(
        UUID id,
        UUID sesionId,
        UUID estudianteId,
        Instant convocadoEn,
        String estadoAutorizacion,
        Instant fechaRetiro) {
    public static ConvocatoriaResponse from(Convocatoria c) {
        return new ConvocatoriaResponse(
                c.getId(),
                c.getSesionId(),
                c.getEstudianteId(),
                c.getConvocadoEn(),
                c.getEstadoAutorizacion(),
                c.getFechaRetiro());
    }
}
