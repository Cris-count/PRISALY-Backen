package com.prisaly.m1.application.port;

/**
 * D-B2.5 (DC-DD02-B4-B5-01): M7 asks M1 to void an access code whose delivery failed permanently or was exhausted.
 * Idempotent; a consumed or already revoked code is left untouched.
 */
public interface RevocacionCodigoAccesoPort {

    void revocarPorFalloDeEntrega(String challengeId);
}
