package com.prisaly.m6.domain.strategy;

import com.prisaly.m6.api.SolicitudEvaluacion;
/**
 * Patrón Strategy. El caso de uso no calcula la nota: recibe una estrategia ya elegida
 * y le pasa la misma solicitud. Aquí no hay algoritmo académico ni estrategia por defecto;
 * quien llama debe entregar una implementación explícita cuando el contrato esté aprobado.
 */
@FunctionalInterface
public interface EstrategiaEvaluacion {
    ResultadoEvaluacion evaluar(SolicitudEvaluacion solicitud);
}
