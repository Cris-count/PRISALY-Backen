package com.prisaly.m7.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Queue row claimed by a worker for one processing attempt (lease held until leaseHasta). */
public record EnvioReclamado(
        UUID idEnvio,
        String correoDestino,
        String asunto,
        String contenido,
        int numeroIntentos,
        Instant leaseHasta) {

    public EnvioReclamado {
        Objects.requireNonNull(idEnvio, "idEnvio");
        Objects.requireNonNull(correoDestino, "correoDestino");
        Objects.requireNonNull(asunto, "asunto");
        Objects.requireNonNull(contenido, "contenido");
        Objects.requireNonNull(leaseHasta, "leaseHasta");
        if (numeroIntentos < 1) {
            throw new IllegalArgumentException("numeroIntentos must be >= 1 after claim");
        }
    }
}
