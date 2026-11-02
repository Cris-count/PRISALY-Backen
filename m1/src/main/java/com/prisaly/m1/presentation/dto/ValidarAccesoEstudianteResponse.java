package com.prisaly.m1.presentation.dto;

import java.util.UUID;

/**
 * Successful DD-02 validation result exposed by {@code POST /v1/acceso-estudiante/validar}.
 */
public record ValidarAccesoEstudianteResponse(
        UUID userId,
        UUID studentId,
        String challengeId,
        String estado
) {
}
