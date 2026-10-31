package com.prisaly.m4.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * DTO de respuesta para sesiones académicas.
 */
public record SesionResponse(
        UUID id,
        UUID casoId,
        UUID docenteId,
        String titulo,
        String descripcion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        EstadoSesion estado,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static SesionResponse from(SesionAcademica s) {
        return new SesionResponse(
                s.getId(), s.getCasoId(), s.getDocenteId(),
                s.getTitulo(), s.getDescripcion(),
                s.getFechaInicio(), s.getFechaFin(),
                s.getEstado(),
                s.getCreadoEn(), s.getActualizadoEn()
        );
    }
}
