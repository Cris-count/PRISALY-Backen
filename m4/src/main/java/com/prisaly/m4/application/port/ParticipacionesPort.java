package com.prisaly.m4.application.port;

import java.util.UUID;

/**
 * Consulta de historial requerida por M4. App descubre el único proveedor productivo
 * ParticipacionesM4DesdeM5, que usa el contrato HistorialParticipacionPort de M5;
 * desconocer el historial nunca equivale a ausencia.
 * Se consulta dentro de la transacción de escritura que bloquea la sesión.
 * Los intentos están incluidos por su FK obligatoria a Participacion. El futuro
 * inicio de participaciones debe coordinarse con ese mismo bloqueo de sesión.
 */
public interface ParticipacionesPort {
    boolean tieneParticipacionesOIntentos(UUID sesionId);
}
