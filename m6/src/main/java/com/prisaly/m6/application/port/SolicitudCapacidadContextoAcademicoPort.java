package com.prisaly.m6.application.port;

import java.util.UUID;

/**
 * Required M6 consumer port; no identity, role or permission can be selected by its caller.
 * The trusted app adapter must ask M1 for a case-scoped institutional capability for
 * M6_EVALUACION / CONSULTAR_CONTEXTO_EVALUABLE, using a trusted invocation identity.
 * Denial uses AccesoEvaluacionDenegadoException; unavailability is a technical failure.
 * No returned String proves authenticity: M3 must verify it. Never log it.
 */
@FunctionalInterface
public interface SolicitudCapacidadContextoAcademicoPort {
    String solicitarCapacidad(UUID idIntento, UUID idCaso);
}
