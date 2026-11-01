package com.prisaly.m7.domain;

import java.util.Objects;
import java.util.UUID;

/** Evento mínimo; no contiene correo, credenciales, respuestas ni calificaciones. */
public record ConvocatoriaNotificable(
        UUID convocatoriaId, UUID sesionId, UUID estudianteId, UUID usuarioDestino) {
    public ConvocatoriaNotificable {
        Objects.requireNonNull(convocatoriaId, "convocatoriaId");
        Objects.requireNonNull(sesionId, "sesionId");
        Objects.requireNonNull(estudianteId, "estudianteId");
        Objects.requireNonNull(usuarioDestino, "usuarioDestino");
    }

    public String claveEvento() {
        return "convocatoria:" + convocatoriaId;
    }
}
