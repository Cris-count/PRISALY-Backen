package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;

/**
 * Puntos de decisión académica del Incremento B, separados del resto de la lógica de fases. Cada método se invoca solo
 * en la situación exacta que su decisión resuelve: terminar normalmente significa «permitido» y lanzar una excepción
 * significa «rechazado, sin escribir nada».
 *
 * <p>Dirección Central resolvió D-SIM-2, D-SIM-3, D-SIM-4 y D-SIM-10 (DC-BE-SIM-B2B-B3-32). La única implementación
 * de producción es {@link PoliticasFaseAprobadas}, y solo {@code ConfiguracionParticipacion} la ensambla. No es una
 * propiedad configurable en tiempo de ejecución. D-SIM-5 (criterio de completitud) no tiene punto aquí: la completitud
 * es solo informativa.
 */
public interface PuntosDecisionFase {

    /**
     * D-SIM-2. Se pidieron las preguntas (contenido académico o completitud) de un intento con fases que nunca entró al
     * cuestionario: sigue en {@code EXPLORACION} en curso, o finalizó desde la exploración.
     */
    void preguntasSinCuestionario(ReferenciaIntento intento, EstadoIntento estado);

    /**
     * D-SIM-3. El intento está {@code EnCurso} en exploración y la escritura atómica de la entrada no se aplicó porque, en
     * su propio instante de PostgreSQL, {@code limiteTemporal} ya había vencido. Debe rechazar: la entrada tras el límite
     * no puede persistirse (QA33-03).
     */
    void entradaAlCuestionarioTrasElLimite(ReferenciaIntento intento);

    /** D-SIM-4. Se pide finalizar un intento {@code EnCurso} que sigue en {@code EXPLORACION}. */
    void finalizacionDesdeExploracion(ReferenciaIntento intento);

    /** D-SIM-10. Se pide la narrativa de un intento con fases ya {@code Finalizado}. */
    void narrativaDeIntentoFinalizado(ReferenciaIntento intento);
}
