package com.prisaly.m6.application.port;

import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;

/** Internal operation shared by the protection proxy and a future real service. */
@FunctionalInterface
public interface EvaluacionCasoUso {
    ResultadoEvaluacion evaluar(SolicitudEvaluacion solicitud);
}
