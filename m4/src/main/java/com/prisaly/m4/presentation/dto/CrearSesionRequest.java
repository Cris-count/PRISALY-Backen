package com.prisaly.m4.presentation.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public record CrearSesionRequest(
        @NotNull UUID casoId,
        @NotBlank @Size(max = 150) String titulo,
        @com.fasterxml.jackson.annotation.JsonAlias("descripcion") String mensajePersonalizado,
        @NotNull Instant fechaInicio,
        @NotNull Instant fechaFin,
        @NotNull @Positive Integer tiempoMaximoParticipacionSegundos,
        @Size(max = 200) String lugarFisico) {

    /** Compatibilidad: solicitudes sin lugar físico. */
    public CrearSesionRequest(
            UUID casoId,
            String titulo,
            String mensajePersonalizado,
            Instant fechaInicio,
            Instant fechaFin,
            Integer tiempoMaximoParticipacionSegundos) {
        this(casoId, titulo, mensajePersonalizado, fechaInicio, fechaFin,
                tiempoMaximoParticipacionSegundos, null);
    }
}
