package com.prisaly.m1.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * D-05 CANDIDATE (DC-BE-CIERRE-FUNCIONAL-NOCTURNO-67): persistence of teacher activation tokens. NO adapter exists:
 * its table needs a migration that Dirección Central has not authorized (D-ESQ-1). Only the SHA-256 hash of a token is
 * ever stored. A future adapter must lock the row in {@link #bloquearPorHash} ({@code SELECT … FOR UPDATE}) inside the
 * caller's write transaction, so two concurrent activations of the same token serialize and only one consumes it.
 */
public interface ActivacionDocenteRepositorioPort {

    record RegistroActivacion(String hashToken, UUID idUsuario, UUID emisor, Instant emitidoEn, Instant expiraEn,
            Instant consumidoEn, Instant revocadoEn) {
        public RegistroActivacion {
            Objects.requireNonNull(hashToken, "hashToken");
            Objects.requireNonNull(idUsuario, "idUsuario");
            Objects.requireNonNull(emisor, "emisor");
            Objects.requireNonNull(emitidoEn, "emitidoEn");
            Objects.requireNonNull(expiraEn, "expiraEn");
            if (!expiraEn.isAfter(emitidoEn)) {
                throw new IllegalArgumentException("expiraEn must be after emitidoEn");
            }
        }
    }

    void registrar(RegistroActivacion registro);

    /** Marks every unconsumed, unrevoked token of the user as revoked at {@code instante}; returns how many. */
    int revocarVigentes(UUID idUsuario, Instant instante);

    /** Locks and returns the token row by hash; empty if unknown. */
    Optional<RegistroActivacion> bloquearPorHash(String hashToken);

    /** Marks the locked token as consumed; false if it was already consumed or revoked. */
    boolean marcarConsumido(String hashToken, Instant instante);
}
