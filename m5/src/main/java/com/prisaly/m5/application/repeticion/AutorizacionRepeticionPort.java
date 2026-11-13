package com.prisaly.m5.application.repeticion;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de {@code "AutorizacionRepeticion"} (BD-005.2). Todas las operaciones exigen la
 * transacción de escritura del caso de uso. El consumo único lo garantiza la base al insertar el
 * intento repetido (V005 valida y bloquea; V007 marca la autorización como {@code Utilizada}).
 */
public interface AutorizacionRepeticionPort {

    /** Bloquea la participación ({@code FOR UPDATE}) y devuelve su convocatoria; vacío si no existe. */
    Optional<UUID> bloquearParticipacion(UUID idParticipacion);

    /** Hay una autorización {@code Vigente} y no vencida según el reloj de PostgreSQL. */
    boolean existeVigente(UUID idParticipacion);

    /**
     * Inserta una autorización {@code Vigente} con {@code fechaAutorizacion} del reloj de PostgreSQL.
     *
     * @param vigencia duración hasta {@code fechaVencimiento}, o {@code null} si no vence
     */
    AutorizacionRegistrada registrar(UUID idParticipacion, UUID idDocenteAutorizador, String motivo, Duration vigencia);

    /** Fila persistida, sin interpretación académica. */
    record AutorizacionRegistrada(UUID idAutorizacion, UUID idParticipacion, UUID idDocenteAutorizador,
            Optional<String> motivo, Instant fechaAutorizacion, Optional<Instant> fechaVencimiento, String estado) {
        public AutorizacionRegistrada {
            Objects.requireNonNull(idAutorizacion);
            Objects.requireNonNull(idParticipacion);
            Objects.requireNonNull(idDocenteAutorizador);
            Objects.requireNonNull(motivo);
            Objects.requireNonNull(fechaAutorizacion);
            Objects.requireNonNull(fechaVencimiento);
            Objects.requireNonNull(estado);
        }
    }
}
