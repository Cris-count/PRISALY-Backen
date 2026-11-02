package com.prisaly.m5.application.inicio;

import java.time.Instant;
import java.util.Optional;

import com.prisaly.m5.application.ReferenciaConvocatoria;

/**
 * Puerto de M5 (lado consumidor): {@code "Intento"."limiteTemporal"} de un
 * intento nuevo. La columna es obligatoria (V001) y debe ser posterior a
 * {@code "inicio"} ({@code ck_intento_tiempos}, V002).
 *
 * <p>Solo se consulta en el primer inicio. Al reanudar, el límite persistido
 * se conserva y nunca se recalcula ni se amplía.
 *
 * <p>El cálculo está PENDIENTE (F3-04: fin de sesión y duración máxima
 * autorizada). La única implementación de producción es
 * {@link LimiteTemporalNoRatificado}.
 */
public interface LimiteTemporalInicio {

    /**
     * @param inicio instante de inicio, tomado del reloj de PostgreSQL
     * @return el límite; vacío o {@code null} si no hay cálculo ratificado
     */
    Optional<Instant> calcular(ReferenciaConvocatoria convocatoria, Instant inicio);
}
