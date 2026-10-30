package com.prisaly.m4.presentation.dto;

import com.prisaly.m4.domain.model.EstadoSesion;

import jakarta.validation.constraints.NotNull;

/**
 * DTO para solicitar un cambio de estado en la sesión.
 */
public record CambiarEstadoRequest(
        @NotNull(message = "nuevoEstado es obligatorio")
        EstadoSesion nuevoEstado
) {}
