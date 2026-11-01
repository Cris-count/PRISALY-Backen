package com.prisaly.m6.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable SQL-shaped data; does not establish feedback lifecycle policies. */
public record RetroalimentacionGeneral(
        UUID idRetroalimentacion,
        UUID idParticipacion,
        UUID idDocente,
        String contenido,
        Instant fechaRegistro) {

    public RetroalimentacionGeneral {
        Objects.requireNonNull(idRetroalimentacion, "idRetroalimentacion");
        Objects.requireNonNull(idParticipacion, "idParticipacion");
        Objects.requireNonNull(idDocente, "idDocente");
        Objects.requireNonNull(contenido, "contenido");
        Objects.requireNonNull(fechaRegistro, "fechaRegistro");
        if (fechaRegistro.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("fechaRegistro must preserve PostgreSQL microsecond precision");
        }
    }
}
