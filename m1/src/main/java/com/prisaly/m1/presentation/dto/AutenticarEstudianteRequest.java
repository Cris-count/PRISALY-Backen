package com.prisaly.m1.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** Login estudiantil por correo y contraseña. No acepta studentId enviado por el cliente. */
public record AutenticarEstudianteRequest(@NotBlank String correo, @NotBlank String contrasena) {
}
