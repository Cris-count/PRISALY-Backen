package com.prisaly.m6.application.service;

import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.EvaluacionNoDisponibleException;
import com.prisaly.m6.application.port.EvaluacionCasoUso;
import com.prisaly.m6.application.port.RegistroCalificacionPort;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion;
import com.prisaly.m6.domain.strategy.EstrategiaEvaluacion;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import java.util.Map;
import java.util.Objects;

/**
 * Real subject of {@code EvaluacionProtectionProxy}: evaluates one finalized attempt with an explicitly selected
 * strategy and records the grade once. It contains no formula, weights, scale, rounding or release rule.
 *
 * <ol>
 *   <li><b>Blocked by default.</b> The strategy is chosen by identifier among the strategies supplied by the
 *       assembler. With no identifier, or one not supplied, it fails with {@link EvaluacionNoDisponibleException}
 *       before anything is read or written. Production supplies none until PF-01/DD-03 are ratified.</li>
 *   <li>{@link PrepararEvaluacionProtegida}: authorization, no previous grade, finalized attempt, coherent academic
 *       context and the requested rubric of the same case.</li>
 *   <li>{@link EntradaEvaluacionMapper} turns the prepared context into the immutable
 *       {@link EntradaEvaluacion} (DD-03 contract, C2); an incoherent context stops here.</li>
 *   <li>The selected strategy evaluates that input.</li>
 *   <li>{@link RegistroCalificacionPort}: insert-only, with the strategy identifier as {@code versionPolitica}. If a
 *       concurrent evaluation recorded a grade first, nothing is overwritten and the call fails.</li>
 * </ol>
 *
 * Not a Spring bean: assembling it in APP is part of enabling the official evaluation.
 */
public final class EvaluarIntentoService implements EvaluacionCasoUso {
    private static final int MAX_VERSION_POLITICA = 50;
    private final PrepararEvaluacionProtegida preparacion;
    private final EstrategiaEvaluacion estrategia;
    private final String versionPolitica;
    private final RegistroCalificacionPort registro;

    /**
     * @param disponibles explicitly supplied strategies by identifier (may be empty)
     * @param seleccionada identifier of the ratified strategy, or blank while none is ratified
     */
    public EvaluarIntentoService(PrepararEvaluacionProtegida preparacion, Map<String, EstrategiaEvaluacion> disponibles,
            String seleccionada, RegistroCalificacionPort registro) {
        this.preparacion = Objects.requireNonNull(preparacion, "preparacion");
        this.registro = Objects.requireNonNull(registro, "registro");
        Map<String, EstrategiaEvaluacion> estrategias = Map.copyOf(Objects.requireNonNull(disponibles, "disponibles"));
        String id = seleccionada == null ? "" : seleccionada.trim();
        if (id.codePointCount(0, id.length()) > MAX_VERSION_POLITICA) {
            throw new IllegalArgumentException("The strategy identifier must fit Calificacion.versionPolitica (50)");
        }
        this.estrategia = id.isEmpty() ? null : estrategias.get(id);
        this.versionPolitica = this.estrategia == null ? null : id;
    }

    @Override
    public ResultadoEvaluacion evaluar(SolicitudEvaluacion solicitud) {
        Objects.requireNonNull(solicitud, "solicitud");
        if (estrategia == null) { throw new EvaluacionNoDisponibleException(); }
        var preparado = Objects.requireNonNull(preparacion.preparar(solicitud), "The prepared context is required");
        EntradaEvaluacion entrada = EntradaEvaluacionMapper.mapear(solicitud, preparado);
        ResultadoEvaluacion resultado = Objects.requireNonNull(estrategia.evaluar(entrada),
                "The strategy must return a result");
        registro.registrar(solicitud.idIntento(), solicitud.idRubrica(), resultado, versionPolitica)
                .orElseThrow(() -> new IllegalStateException("The attempt was graded concurrently; nothing was overwritten"));
        return resultado;
    }
}
