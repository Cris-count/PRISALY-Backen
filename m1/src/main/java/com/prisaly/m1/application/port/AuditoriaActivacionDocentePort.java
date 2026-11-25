package com.prisaly.m1.application.port;

import java.util.UUID;

/** D-05 CANDIDATE: audit trail of activation events. Never receives the token or the password. */
@FunctionalInterface
public interface AuditoriaActivacionDocentePort {
    enum Evento { EMISION, REVOCACION, CONSUMO, RECHAZO }

    /** @param motivo internal reason (rejections only); never sent to the client */
    void registrar(Evento evento, UUID idUsuario, UUID actor, String motivo);
}
