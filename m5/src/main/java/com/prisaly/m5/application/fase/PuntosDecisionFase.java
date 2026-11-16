package com.prisaly.m5.application.fase;

import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Puntos de decisión del Incremento B que B2a alcanza, separados del resto de la lógica. Cada método se invoca solo en
 * la situación exacta que la decisión debe resolver; terminar normalmente significa «permitido» y lanzar una excepción
 * significa «rechazado». El único valor de producción es {@link #noRatificados()}, que no elige: falla cerrado.
 *
 * <p>Ratificar una decisión es sustituir esa implementación por otra (con su prueba), en un único lugar
 * ({@code ConfiguracionParticipacion}). No es una propiedad configurable en tiempo de ejecución.
 */
public interface PuntosDecisionFase {

    /** D-SIM-3: el intento está {@code EnCurso} en exploración y su {@code limiteTemporal} ya venció. */
    void entradaAlCuestionarioTrasElLimite(ReferenciaIntento intento);

    /** D-SIM-10: se pide la narrativa de un intento con fases ya {@code Finalizado}. */
    void narrativaDeIntentoFinalizado(ReferenciaIntento intento);

    static PuntosDecisionFase noRatificados() {
        return new PuntosDecisionFase() {
            @Override
            public void entradaAlCuestionarioTrasElLimite(ReferenciaIntento intento) {
                throw new DecisionFaseNoRatificadaException(DecisionFasePendiente.D_SIM_3);
            }

            @Override
            public void narrativaDeIntentoFinalizado(ReferenciaIntento intento) {
                throw new DecisionFaseNoRatificadaException(DecisionFasePendiente.D_SIM_10);
            }
        };
    }
}
