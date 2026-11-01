package com.prisaly.m6.application.port;

import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;

/**
 * M6-owned consumer contract, not an institutional permission catalogue.
 * A future adapter must establish a trusted current identity and resource access;
 * the request identifies resources, never authenticates its caller.
 */
@FunctionalInterface
public interface AutorizacionEvaluacionPort {
    /** Access outcomes, not academic lifecycle states. Only AUTORIZADO permits delegation. */
    enum DecisionAcceso { AUTORIZADO, DENEGADO, NO_DISPONIBLE }

    DecisionAcceso verificarAcceso(SolicitudEvaluacion solicitud);
}
