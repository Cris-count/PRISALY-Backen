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

    /**
     * QB-07 (preparación): aviso por cambio de programación. Aún sin llamador ni proveedor: rechaza
     * para no simular entrega. {@code huellaCambio} identifica la nueva programación.
     */
    default void notificarCambioSesion(UUID sesionId, UUID estudianteId, String huellaCambio) {
        throw new com.prisaly.m4.domain.rule.ReglaNegocioException(
                "M4-PROVEEDOR-NO-DISPONIBLE",
                "La notificación de cambios de sesión no está disponible");
    }
}
