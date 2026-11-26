package com.prisaly.m5.domain.investigacion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Edificio visitado: identificador + ubicación/descripción extraída del contacto. */
public record Edificio(UUID idEdificio, UUID idIntento, String nombre, String ubicacion, Instant fechaCreacion) {
    public Edificio {
        Objects.requireNonNull(idEdificio, "idEdificio");
        Objects.requireNonNull(idIntento, "idIntento");
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
    }
}
