package com.prisaly.m7.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Outbox {@code EntregaCodigoAcceso} (candidate V016). Final states always erase the encrypted payload (D-B2.4). */
public interface EntregaCodigoAccesoRepositorio {

    /** Claims due PENDIENTE rows with a lease ({@code FOR UPDATE SKIP LOCKED}); concurrent workers never share a row. */
    List<EntregaReclamada> reclamar(int lote, Duration lease, Instant ahora);

    void marcarEnviada(UUID idEntrega, Instant ahora);

    void reprogramar(UUID idEntrega, int intentos, Instant proximoIntento, String codigoError);

    void finalizarSinEnvio(UUID idEntrega, EstadoFinal estado, String codigoError, Instant ahora);

    /** Cancels PENDIENTE rows whose code expires at or before {@code umbral} and erases their payload. */
    int cancelarSinTiempo(Instant umbral, Instant ahora);

    /** Deletes final rows (metadata only) created before {@code limite}. */
    int purgarMetadatos(Instant limite);

    enum EstadoFinal { CANCELADA_VENCIDA, FALLIDA_PERMANENTE, AGOTADA }

    record EntregaReclamada(UUID idEntrega, String challengeId, byte[] carga, byte[] nonce, String kid, int intentos,
            Instant creada, Instant expira) {
    }
}
