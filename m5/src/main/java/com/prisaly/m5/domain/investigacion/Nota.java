package com.prisaly.m5.domain.investigacion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Nota de investigación del intento; idempotente por su UUID (asignado por el servidor). */
public record Nota(UUID idNota, UUID idIntento, String titulo, String texto, Instant fechaCreacion) {
    public Nota {
        Objects.requireNonNull(idNota, "idNota");
        Objects.requireNonNull(idIntento, "idIntento");
        if (titulo == null || titulo.isBlank()) throw new IllegalArgumentException("El titulo es obligatorio");
    }
}
