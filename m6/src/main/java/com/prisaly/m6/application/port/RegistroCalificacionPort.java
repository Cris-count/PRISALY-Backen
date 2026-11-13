package com.prisaly.m6.application.port;

import com.prisaly.m6.domain.model.Calificacion;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal insert-only write port for BD-005.2 {@code Calificacion}. A grade is never overwritten (V003 forbids
 * UPDATE and DELETE): if the attempt already has one, nothing is written and the result is empty. Values that would
 * need rounding to fit {@code NUMERIC(8,2)} are rejected, never rounded (rounding is decision D19). Not a Spring bean.
 */
public interface RegistroCalificacionPort {
    Optional<Calificacion> registrar(UUID idIntento, UUID idRubrica, ResultadoEvaluacion resultado, String versionPolitica);
}
