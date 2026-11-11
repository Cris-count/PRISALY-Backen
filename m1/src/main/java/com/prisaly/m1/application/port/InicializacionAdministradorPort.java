package com.prisaly.m1.application.port;

import java.util.UUID;

/**
 * P5 / DM1-01 (DC-P5-FASE-B-01): persistent, immutable marker of the first-administrator initialization (V013).
 * Every method must run inside the caller's transaction.
 */
public interface InicializacionAdministradorPort {

    /** Serializes concurrent initializations until the current transaction ends (transaction-level advisory lock). */
    void bloquear();

    boolean marcadorExiste();

    /** Inserts the single marker row. The database rejects a second row and any later change. */
    void registrarMarcador(UUID idUsuarioAdministrador, String origen);
}
