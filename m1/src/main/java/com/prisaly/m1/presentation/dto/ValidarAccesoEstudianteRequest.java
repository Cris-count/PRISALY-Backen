package com.prisaly.m1.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DD-02 validation contract: opaque challenge id + one-time 8-digit code.
 * Client-supplied student ids are never accepted as proof of identity.
 */
public record ValidarAccesoEstudianteRequest(
        @NotBlank String challengeId,
        @NotBlank @Pattern(regexp = "\\d{8}") String codigo
) {
}
