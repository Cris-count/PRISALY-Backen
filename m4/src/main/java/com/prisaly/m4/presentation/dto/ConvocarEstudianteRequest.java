package com.prisaly.m4.presentation.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * DTO para convocar a un estudiante a una sesión.
 */
public record ConvocarEstudianteRequest(
        @NotNull(message = "estudianteId es obligatorio")
        UUID estudianteId
) {}
