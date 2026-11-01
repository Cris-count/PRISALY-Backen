package com.prisaly.m7.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Contexto mínimo resuelto para registrar aviso y encolar correo.
 * No autentica; no implica entrega SMTP.
 */
public record ConvocatoriaNotificable(
        UUID convocatoriaId,
        UUID sesionId,
        UUID estudianteId,
        UUID usuarioDestino,
        String correoDestino) {

    public ConvocatoriaNotificable {
        Objects.requireNonNull(convocatoriaId, "convocatoriaId");
        Objects.requireNonNull(sesionId, "sesionId");
        Objects.requireNonNull(estudianteId, "estudianteId");
        Objects.requireNonNull(usuarioDestino, "usuarioDestino");
        if (correoDestino == null || correoDestino.isBlank()) {
            throw new IllegalArgumentException("correoDestino is required");
        }
    }

    public String claveEvento() {
        return "convocatoria:" + convocatoriaId;
    }

    public String claveIdempotencia() {
        return claveEvento();
    }
}
