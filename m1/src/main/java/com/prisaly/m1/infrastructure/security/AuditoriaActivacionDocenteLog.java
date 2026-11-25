package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.AuditoriaActivacionDocentePort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * D-05 (DC-77): persistent activation audit ({@code auditoria-activacion.log}, same rotation and retention as PD-01).
 * EMISION, REVOCACION and CONSUMO are written only after the transaction commits (a rolled-back issue, e.g. a mail
 * failure, leaves no success line); RECHAZO is written immediately. Never receives the token or the password.
 */
public class AuditoriaActivacionDocenteLog implements AuditoriaActivacionDocentePort {
    public static final String LOGGER = "prisaly.auditoria.activacion";
    private static final Logger auditoria = LoggerFactory.getLogger(LOGGER);

    @Override
    public void registrar(Evento evento, UUID idUsuario, UUID actor, String motivo) {
        if (evento == Evento.RECHAZO || !TransactionSynchronizationManager.isSynchronizationActive()) {
            escribir(evento, idUsuario, actor, motivo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                escribir(evento, idUsuario, actor, motivo);
            }
        });
    }

    private static void escribir(Evento evento, UUID idUsuario, UUID actor, String motivo) {
        auditoria.info("AUDITORIA_ACTIVACION evento={} usuario={} actor={} motivo={} fecha={}",
                evento, idUsuario, actor, motivo == null ? "-" : motivo, Instant.now());
    }
}
