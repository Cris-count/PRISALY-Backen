package com.prisaly.m1.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record AutenticarEstudianteRequest(@NotBlank String correo, @NotBlank String contrasena) {
}
