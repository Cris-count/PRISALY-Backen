package com.prisaly.m4.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creación de una sesión académica.
 */
public record CrearSesionRequest(
        @NotNull(message = "casoId es obligatorio")
        UUID casoId,

        @NotBlank(message = "titulo es obligatorio")
        @Size(max = 200, message = "titulo no puede exceder 200 caracteres")
        String titulo,

        @Size(max = 1000, message = "descripcion no puede exceder 1000 caracteres")
        String descripcion,

        @NotNull(message = "fechaInicio es obligatoria")
        LocalDateTime fechaInicio,

        @NotNull(message = "fechaFin es obligatoria")
        LocalDateTime fechaFin
) {}
