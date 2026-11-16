package com.prisaly.m5.application.fase;

import java.util.Objects;
import java.util.UUID;

/**
 * Guion narrativo que corresponde al intento (contrato P1, candidato): siempre la versión fijada en
 * {@code "FaseIntento"."idGuion"} al crear el intento, nunca la última publicada. {@code definicionJson} es el documento
 * {@code prisaly-guion-1} publicado tal cual, sin datos académicos (lo garantiza el validador de M3).
 */
public record NarrativaIntento(UUID idIntento, UUID idGuion, int version, String hashDefinicion, String definicionJson) {
    public NarrativaIntento {
        Objects.requireNonNull(idIntento, "idIntento");
        Objects.requireNonNull(idGuion, "idGuion");
        Objects.requireNonNull(hashDefinicion, "hashDefinicion");
        Objects.requireNonNull(definicionJson, "definicionJson");
    }
}
