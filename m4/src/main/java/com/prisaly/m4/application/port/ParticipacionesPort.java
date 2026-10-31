package com.prisaly.m4.application.port;

import java.util.UUID;

/**
 * Consulta de historial requerida por M4. Pendiente de enlazar con un contrato
 * público de M5; desconocer el historial nunca equivale a ausencia de historial.
 * El proveedor debe incluir participaciones e intentos y coordinar el bloqueo
 * con las escrituras de M5 en la misma transacción de modificación de la sesión.
 */
public interface ParticipacionesPort {
    boolean tieneParticipacionesOIntentos(UUID sesionId);
}
