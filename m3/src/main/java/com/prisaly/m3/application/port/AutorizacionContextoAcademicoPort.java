package com.prisaly.m3.application.port;

import java.util.UUID;

/**
 * M3-owned required port. The trusted app adapter must delegate to M1, verifying authenticity,
 * consumer M6_EVALUACION, operation, exact case UUID, expiry and revocation.
 * No property of the supplied String establishes any of those guarantees locally.
 * Implementations must never log the capability or include it in errors.
 */
@FunctionalInterface
public interface AutorizacionContextoAcademicoPort {
    enum Operacion { CONSULTAR_CONTEXTO_EVALUABLE }
    enum Decision { AUTORIZADO, DENEGADO, NO_DISPONIBLE }

    Decision verificar(String capacidad, UUID idCaso, Operacion operacion);
}
