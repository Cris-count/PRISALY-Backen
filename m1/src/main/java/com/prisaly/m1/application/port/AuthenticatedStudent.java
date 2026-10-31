package com.prisaly.m1.application.port;

import java.util.UUID;

/**
 * Student identity exposed to M4/M5. Never trust client-supplied student ids as authority.
 * Aligned with BD-005.2 {@code Usuario.idUsuario} and {@code Estudiante.idEstudiante}.
 */
public record AuthenticatedStudent(
        UUID userId,
        UUID studentId
) {
}
