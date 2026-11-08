package com.prisaly.m1.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** El cliente no envía userId ni studentId. */
public record ActivarCuentaEstudianteRequest(
        @NotNull UUID challengeId,
        @NotBlank @Pattern(regexp = "\\d{8}") String codigo,
        @NotBlank String contrasena) {
}
