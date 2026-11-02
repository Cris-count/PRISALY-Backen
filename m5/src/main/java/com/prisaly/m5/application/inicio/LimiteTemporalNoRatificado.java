package com.prisaly.m5.application.inicio;

import java.time.Instant;
import java.util.Optional;

import com.prisaly.m5.application.ReferenciaConvocatoria;

/**
 * Implementación de producción mientras el cálculo del límite temporal siga
 * pendiente: no devuelve ningún límite, así que no se crea ningún intento.
 * No es una fórmula provisional.
 */
public final class LimiteTemporalNoRatificado implements LimiteTemporalInicio {

    @Override
    public Optional<Instant> calcular(ReferenciaConvocatoria convocatoria, Instant inicio) {
        return Optional.empty();
    }
}
