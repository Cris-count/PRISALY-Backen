package com.prisaly.m7.application;

import com.prisaly.m7.domain.EnvioReclamado;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface ReclamoEnvioPort {

    /**
     * Claims up to {@code lote} Pendiente rows whose next-retry is due, using SKIP LOCKED.
     * Increments {@code numeroIntentos} (claim count) and sets lease on {@code proximoReintento}.
     */
    List<EnvioReclamado> reclamar(int lote, Duration lease, int maxIntentos);

    /**
     * Persists SMTP acceptance only if this worker still holds the claimed lease.
     * Returns false when the lease was lost (at-least-once recovery path).
     */
    boolean registrarAceptacion(UUID idEnvio, InstantLease lease, String messageId);

    /**
     * Schedules another attempt or marks Fallido only if the lease is still held.
     */
    boolean registrarFallo(
            UUID idEnvio, InstantLease lease, String estadoFinal, String ultimoError, Duration proximoEn);

    /** Lease identity returned by claim (PostgreSQL clock). */
    record InstantLease(java.time.Instant leaseHasta, int numeroIntentos) {}
}
