package com.prisaly.m4.infrastructure.adapter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.prisaly.m4.application.port.NotificacionPort;

/**
 * Stub temporal de M7 – Notificaciones.
 *
 * <p>BLOQUEO DE INTEGRACIÓN: M7 aún no tiene contratos implementados.
 * Este stub registra las solicitudes de notificación sin enviarlas.
 * Debe ser reemplazado cuando M7 exponga su mecanismo aprobado.</p>
 */
@Component
public class NotificacionStubAdapter implements NotificacionPort {

    private static final Logger log = LoggerFactory.getLogger(NotificacionStubAdapter.class);

    @Override
    public void notificarConvocatoria(UUID sesionId, UUID estudianteId) {
        log.warn("[M7-STUB] Notificación de convocatoria para sesión {} / estudiante {} — " +
                "registrada por stub (sin envío real)", sesionId, estudianteId);
    }
}
