package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.AuditoriaPermisosPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Escribe en el registro dedicado que APP persiste con rotación y retención. */
@Component
public class AuditoriaPermisosLog implements AuditoriaPermisosPort {
    private static final Logger auditoria = LoggerFactory.getLogger(LOGGER);
    private static final String EXITOSO = "EXITOSO";

    @Override
    public void registrarRechazo(String accion, UUID usuario, String permiso, UUID actor, String codigoRechazo) {
        escribir(accion, usuario, permiso, actor, "RECHAZADO:" + codigoRechazo);
    }

    @Override
    public void registrarExitoAlConfirmar(String accion, UUID usuario, String permiso, UUID actor) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            escribir(accion, usuario, permiso, actor, EXITOSO);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                escribir(accion, usuario, permiso, actor, EXITOSO);
            }
        });
    }

    private static void escribir(String accion, UUID usuario, String permiso, UUID actor, String resultado) {
        auditoria.info("AUDITORIA_PERMISO accion={} usuario={} permiso={} administrador={} fecha={} resultado={}",
                accion, usuario, permiso, actor, Instant.now(), resultado);
    }
}
