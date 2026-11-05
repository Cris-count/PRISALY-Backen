package com.prisaly.m4.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Reserva transaccional D-06. Exige la transacción de escritura del consumidor. Bloquea Sesion y
 * después Convocatoria hasta su commit/rollback. El estudiante procede de M1; el contexto es
 * informativo y no autoriza un primer inicio. Requiere READ COMMITTED: rechaza ausencia de
 * transacción, solo lectura, otro aislamiento o indisponibilidad. Vacío para convocatoria
 * inexistente/ajena.
 */
public interface ProgramacionInicioCoordinadoPort {
    Optional<ContextoProgramacion> bloquearYConsultarConvocatoria(
            UUID convocatoriaId, UUID estudianteAutenticadoId);
}
