package com.prisaly.m6.domain.strategy;

import java.util.Objects;
import java.util.UUID;

/**
 * Minimal references backed by Calificacion foreign keys in BD-005.2.
 * Does not assert existence, ownership, finalized state, or matching case.
 * A stable academic context remains pending the M3/M5 contracts and DD-03.
 */
public record SolicitudEvaluacion(UUID idIntento, UUID idRubrica) {
    public SolicitudEvaluacion {
        Objects.requireNonNull(idIntento, "idIntento");
        Objects.requireNonNull(idRubrica, "idRubrica");
    }
}
