package com.prisaly.m4.presentation.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record ActualizarSesionRequest(
        @NotBlank @Size(max = 150) String titulo,
        @com.fasterxml.jackson.annotation.JsonAlias("descripcion") String mensajePersonalizado,
        @NotNull Instant fechaInicio,
        @NotNull Instant fechaFin,
        @Size(max = 200) String lugarFisico) {

    /** Compatibilidad: sin lugar físico se conserva el valor persistido. */
    public ActualizarSesionRequest(
            String titulo, String mensajePersonalizado, Instant fechaInicio, Instant fechaFin) {
        this(titulo, mensajePersonalizado, fechaInicio, fechaFin, null);
    }
}
