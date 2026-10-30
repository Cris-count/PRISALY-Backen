package com.prisaly.m4.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para actualizar una sesión académica.
 */
public record ActualizarSesionRequest(
        @NotBlank(message = "titulo es obligatorio")
        @Size(max = 200)
        String titulo,

        @Size(max = 1000)
        String descripcion,

        @NotNull(message = "fechaInicio es obligatoria")
        LocalDateTime fechaInicio,

        @NotNull(message = "fechaFin es obligatoria")
        LocalDateTime fechaFin
) {}
