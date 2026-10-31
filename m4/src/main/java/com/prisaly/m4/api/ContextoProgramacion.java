package com.prisaly.m4.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Datos persistidos BD-005.2, sin traducción de catálogos ni cálculo de permisos.
 * inicioPrevisto/finPrevisto NO se presentan como apertura/cierre autorizados.
 * observadoEn procede de clock_timestamp() PostgreSQL, no del cliente.
 * Su adopción como reloj académico requiere ratificación de Dirección Central.
 */
public record ContextoProgramacion(
        UUID convocatoriaId,
        UUID estudianteId,
        UUID sesionId,
        UUID casoId,
        String estadoSesion,
        String estadoAutorizacionConvocatoria,
        Optional<Instant> fechaRetiroConvocatoria,
        String estadoCaso,
        boolean casoActivo,
        Instant inicioPrevisto,
        Instant finPrevisto,
        Optional<Instant> fechaInicioReal,
        Optional<Instant> fechaFinReal,
        Duration tiempoMaximoParticipacion,
        Instant observadoEn) {

    public ContextoProgramacion {
        Objects.requireNonNull(convocatoriaId);
        Objects.requireNonNull(estudianteId);
        Objects.requireNonNull(sesionId);
        Objects.requireNonNull(casoId);
        Objects.requireNonNull(estadoSesion);
        Objects.requireNonNull(estadoAutorizacionConvocatoria);
        Objects.requireNonNull(fechaRetiroConvocatoria);
        Objects.requireNonNull(estadoCaso);
        Objects.requireNonNull(inicioPrevisto);
        Objects.requireNonNull(finPrevisto);
        Objects.requireNonNull(fechaInicioReal);
        Objects.requireNonNull(fechaFinReal);
        Objects.requireNonNull(tiempoMaximoParticipacion);
        Objects.requireNonNull(observadoEn);
        if (!finPrevisto.isAfter(inicioPrevisto) || tiempoMaximoParticipacion.isNegative()
                || tiempoMaximoParticipacion.isZero()) {
            throw new IllegalArgumentException("Programación persistida inconsistente");
        }
    }

    /** Hecho informativo; no equivale a autorización académica de acceso. */
    public boolean casoPublicadoYActivo() {
        return "Publicado".equals(estadoCaso) && casoActivo;
    }
}
