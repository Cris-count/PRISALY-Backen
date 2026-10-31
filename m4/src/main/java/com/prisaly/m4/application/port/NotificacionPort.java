package com.prisaly.m4.application.port;

import java.util.UUID;

/**
 * Puerto hacia M7 – Notificaciones.
 *
 * <p>M4 emite eventos de convocatoria confirmada para que M7
 * notifique a los estudiantes. Si M7 no está implementado,
 * se rechaza la operación; no se simula una entrega exitosa.</p>
 */
public interface NotificacionPort {

    /**
     * Solicita el envío de una notificación de convocatoria.
     *
     * @param sesionId      identificador de la sesión
     * @param estudianteId  identificador del estudiante convocado
     */
    void notificarConvocatoria(UUID sesionId, UUID estudianteId);
}
