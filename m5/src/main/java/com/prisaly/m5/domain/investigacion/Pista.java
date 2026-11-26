package com.prisaly.m5.domain.investigacion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** pista obtenida durante la investigación del intento. */
public record Pista(UUID idPista, UUID idIntento, String descripcion, Instant fechaCreacion) {
    public Pista {
        Objects.requireNonNull(idPista, "idPista");
        Objects.requireNonNull(idIntento, "idIntento");
        if (descripcion == null || descripcion.isBlank()) throw new IllegalArgumentException("La descripcion es obligatoria");
    }
}
