package com.prisaly.m5.domain.investigacion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Zona explorada del intento. */
public record Zona(UUID idZona, UUID idIntento, String nombre, String descripcion, Instant fechaCreacion) {
    public Zona {
        Objects.requireNonNull(idZona, "idZona");
        Objects.requireNonNull(idIntento, "idIntento");
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
    }
}
