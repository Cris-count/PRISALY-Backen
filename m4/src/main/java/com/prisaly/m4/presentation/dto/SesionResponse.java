package com.prisaly.m4.presentation.dto;

import com.prisaly.m4.domain.model.*;

import java.time.Instant;
import java.util.UUID;

public record SesionResponse(
        UUID id,
        UUID casoId,
        UUID docenteId,
        String titulo,
        String mensajePersonalizado,
        Instant fechaInicio,
        Instant fechaFin,
        EstadoSesion estado,
        Instant creadoEn,
        Instant fechaInicioReal,
        Instant fechaFinReal,
        int tiempoMaximoParticipacionSegundos) {
    public static SesionResponse from(SesionAcademica s) {
        return new SesionResponse(
                s.getId(),
                s.getCasoId(),
                s.getDocenteId(),
                s.getTitulo(),
                s.getDescripcion(),
                s.getFechaInicio(),
                s.getFechaFin(),
                s.getEstado(),
                s.getCreadoEn(),
                s.getFechaInicioReal(),
                s.getFechaFinReal(),
                s.getTiempoMaximoParticipacionSegundos());
    }
}
