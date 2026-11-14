package com.prisaly.m4.presentation.dto;

import com.prisaly.m4.api.ContextoProgramacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Convocatoria propia vista por el estudiante (DC-BE-CIERRE-FUNCIONAL-09, fase 2). Solo datos persistidos de
 * programación: sin contenido académico, sin datos de otros estudiantes y sin decisión de elegibilidad. Que una
 * convocatoria aparezca aquí no autoriza iniciar un intento (eso lo decide M5 con reglas ratificadas).
 */
public record ConvocatoriaEstudianteResponse(
        UUID idConvocatoria,
        UUID idSesion,
        UUID idCaso,
        String estadoSesion,
        String estadoAutorizacion,
        Instant inicioPrevisto,
        Instant finPrevisto,
        Instant fechaInicioReal,
        Instant fechaFinReal,
        long tiempoMaximoParticipacionSegundos) {

    public static ConvocatoriaEstudianteResponse de(ContextoProgramacion c) {
        return new ConvocatoriaEstudianteResponse(c.convocatoriaId(), c.sesionId(), c.casoId(), c.estadoSesion(),
                c.estadoAutorizacionConvocatoria(), c.inicioPrevisto(), c.finPrevisto(),
                c.fechaInicioReal().orElse(null), c.fechaFinReal().orElse(null),
                c.tiempoMaximoParticipacion().toSeconds());
    }
}
