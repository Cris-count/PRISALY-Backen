package com.prisaly.m5.application.fase;

import com.prisaly.m5.domain.intento.EstadoIntento;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fase persistida del intento (contrato P2, candidato y no publicado). La autoridad es el servidor (R5):
 * {@code ahoraServidor} es el {@code clock_timestamp()} de PostgreSQL leído en la misma transacción.
 * {@code limiteTemporal} es el valor persistido del intento: esta vista no lo calcula ni decide su política.
 */
public record EstadoFase(UUID idIntento, EstadoIntento estadoIntento, FaseAcademica fase,
        Optional<Instant> fechaInicioExploracion, Optional<Instant> fechaInicioCuestionario,
        Instant limiteTemporal, Instant ahoraServidor) {
    public EstadoFase {
        Objects.requireNonNull(idIntento, "idIntento");
        Objects.requireNonNull(estadoIntento, "estadoIntento");
        Objects.requireNonNull(fase, "fase");
        Objects.requireNonNull(fechaInicioExploracion, "fechaInicioExploracion");
        Objects.requireNonNull(fechaInicioCuestionario, "fechaInicioCuestionario");
        Objects.requireNonNull(limiteTemporal, "limiteTemporal");
        Objects.requireNonNull(ahoraServidor, "ahoraServidor");
        if ((fase == FaseAcademica.SIN_FASES) != fechaInicioExploracion.isEmpty()
                || (fase == FaseAcademica.CUESTIONARIO) != fechaInicioCuestionario.isPresent()) {
            throw new IllegalArgumentException("Fechas incoherentes con la fase " + fase);
        }
    }
}
