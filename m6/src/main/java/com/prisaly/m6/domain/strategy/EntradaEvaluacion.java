package com.prisaly.m6.domain.strategy;

import com.prisaly.m6.api.SolicitudEvaluacion;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Validated, immutable input of {@link EstrategiaEvaluacion} (DD-03, contract only; DC-CLAUDE-BE-COMP-03 C2).
 *
 * <p>It carries persisted data as found, without deciding anything academic:
 * <ul>
 *   <li>The two weighting origins are delivered side by side and uninterpreted: the per-question
 *       {@code RubricaPregunta.valorPregunta} ({@link PreguntaEvaluable#valorPregunta()}, empty when the
 *       question has no association, because M3 does not require one) and the rubric's opaque
 *       {@code definicionEvaluacion} document ({@link #definicionEvaluacionRubrica()}, PD-06).</li>
 *   <li>Every question of the case is listed in the order supplied by the academic context, with the
 *       scenario's {@code activo} flag and the question's {@code visible} flag and {@code estadoEdicion}
 *       (DC-57: it was lost before); no question is filtered out.</li>
 *   <li>The selected option is empty when the question was not answered.</li>
 * </ul>
 * No score, weight, scale, rounding, eligibility or "last attempt" rule is implied by this type.
 */
public record EntradaEvaluacion(SolicitudEvaluacion solicitud, UUID idCaso, List<PreguntaEvaluable> preguntas,
        Optional<String> definicionEvaluacionRubrica) {

    public EntradaEvaluacion {
        Objects.requireNonNull(solicitud, "solicitud");
        Objects.requireNonNull(idCaso, "idCaso");
        preguntas = List.copyOf(Objects.requireNonNull(preguntas, "preguntas"));
        Objects.requireNonNull(definicionEvaluacionRubrica, "definicionEvaluacionRubrica");
        var vistas = new HashSet<UUID>();
        for (PreguntaEvaluable pregunta : preguntas) {
            if (!vistas.add(pregunta.idPregunta())) {
                throw new IllegalArgumentException("Duplicated question in the evaluation input");
            }
        }
    }

    /** One question of the case as persisted, with its optional association weight and the recorded answer. */
    public record PreguntaEvaluable(UUID idPregunta, UUID idEscenario, boolean escenarioActivo, boolean visible,
            String estadoEdicion, Optional<BigDecimal> valorPregunta, List<OpcionEvaluable> opciones,
            Optional<UUID> opcionSeleccionada) {

        /** Exact editing-state catalogue of M3 ({@code Pregunta.estadoEdicion}, V001). */
        public static final String LISTA = "Lista";
        public static final String BORRADOR = "Borrador";

        public PreguntaEvaluable {
            Objects.requireNonNull(idPregunta, "idPregunta");
            Objects.requireNonNull(idEscenario, "idEscenario");
            if (!LISTA.equals(Objects.requireNonNull(estadoEdicion, "estadoEdicion"))
                    && !BORRADOR.equals(estadoEdicion)) {
                throw new IllegalArgumentException("Unknown question editing state");
            }
            Objects.requireNonNull(valorPregunta, "valorPregunta");
            opciones = List.copyOf(Objects.requireNonNull(opciones, "opciones"));
            Objects.requireNonNull(opcionSeleccionada, "opcionSeleccionada");
            var ids = new HashSet<UUID>();
            for (OpcionEvaluable opcion : opciones) {
                if (!ids.add(opcion.idOpcion())) {
                    throw new IllegalArgumentException("Duplicated option in the evaluation input");
                }
            }
            if (opcionSeleccionada.isPresent() && !ids.contains(opcionSeleccionada.get())) {
                throw new IllegalArgumentException("The selected option does not belong to the question");
            }
        }

        /**
         * DC-BE-M6-CORRECCION-QA-57: the question could be delivered to the student. Same condition as M3's
         * {@code ContenidoCasoEstudiantilPort} ({@code ContenidoCasoEstudiantilJdbcAdapter}): scenario active,
         * question visible and in editing state {@code Lista}. Pinned against the real M3 adapter by
         * {@code AccesibilidadM3M6ContratoIT}. It is a persisted fact, not a grading decision.
         */
        public boolean accesibleAlEstudiante() {
            return escenarioActivo && visible && LISTA.equals(estadoEdicion);
        }
    }

    /** Option reference and its persisted {@code esCorrecta} flag; texts are not part of the evaluation input. */
    public record OpcionEvaluable(UUID idOpcion, boolean esCorrecta) {
        public OpcionEvaluable {
            Objects.requireNonNull(idOpcion, "idOpcion");
        }
    }
}
