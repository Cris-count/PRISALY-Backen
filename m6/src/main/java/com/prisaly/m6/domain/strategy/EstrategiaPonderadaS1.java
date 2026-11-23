package com.prisaly.m6.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Ratified weighted strategy S1 (DC-BE-M6-DESBLOQUEO-IMPLEMENTACION-55, ACTA-M6-POLITICAS-55, resolutions M6-R1 to
 * M6-R5 and M6-R11). Before DC-55 this class was the unratified candidate {@code EstrategiaPonderadaCandidata}.
 * It is not a Spring bean: only the switch-guarded APP assembly supplies it to {@code EvaluarIntentoService}.
 *
 * <ul>
 *   <li>Per-question weight configured by the teacher: {@code RubricaPregunta.valorPregunta}.</li>
 *   <li>Correct answer = 100% of the weight; incorrect or unanswered = 0.</li>
 *   <li>Scale 0–5: {@code 5 × obtained weight / total weight}, rounded once with {@link RoundingMode#HALF_UP}
 *       to two decimals.</li>
 *   <li>Formative evaluation, recorded in the detail document.</li>
 *   <li>One immutable grade per attempt is guaranteed by the insert-only writer and V003, not here.</li>
 * </ul>
 *
 * <p>Preconditions, checked <b>before</b> computing and in this order; any failure throws
 * {@link EvaluacionRechazadaException} and no result exists, so no invalid grade can be recorded:
 * <ul>
 *   <li>{@code SIN_PREGUNTAS}: the input has no questions.</li>
 *   <li>{@code PREGUNTAS_NO_ACCESIBLES} (correction C1, M6-R5; DC-BE-M6-CORRECCION-QA-57): some question was not
 *       accessible to the student ({@link EntradaEvaluacion.PreguntaEvaluable#accesibleAlEstudiante()}: not visible,
 *       in editing state {@code Borrador}, or in an inactive scenario). M3 delivers a case only if <b>all</b> its
 *       questions are accessible, so in that case no content reached the student and the rubric would require
 *       evaluating inaccessible content. It is <b>never</b> silently excluded from the numerator or the
 *       denominator: the whole evaluation is rejected.</li>
 *   <li>{@code PESOS_INCOMPLETOS}: some question has no {@code RubricaPregunta} weight (incomplete rubric).</li>
 *   <li>{@code PESOS_INCONSISTENTES}: some weight is not positive.</li>
 *   <li>{@code CLAVE_INCONSISTENTE}: some question does not have exactly one correct option.</li>
 * </ul>
 * The rubric's JSONB {@code definicionEvaluacion} is deliberately ignored: criteria are not interpreted as weights.
 */
public final class EstrategiaPonderadaS1 implements EstrategiaEvaluacion {

    /** Identifier recorded as {@code Calificacion.versionPolitica} (≤ 50). */
    public static final String IDENTIFICADOR = "ponderada-s1-v1";

    private static final BigDecimal ESCALA_MAXIMA = new BigDecimal("5");

    @Override
    public ResultadoEvaluacion evaluar(EntradaEvaluacion entrada) {
        Objects.requireNonNull(entrada, "entrada");
        validar(entrada);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal obtenido = BigDecimal.ZERO;
        StringBuilder detalle = new StringBuilder();
        for (var pregunta : entrada.preguntas()) {
            BigDecimal peso = pregunta.valorPregunta().orElseThrow();
            boolean respondida = pregunta.opcionSeleccionada().isPresent();
            boolean correcta = respondida && pregunta.opciones().stream()
                    .anyMatch(o -> o.esCorrecta() && o.idOpcion().equals(pregunta.opcionSeleccionada().get()));
            total = total.add(peso);
            if (correcta) { obtenido = obtenido.add(peso); }
            if (detalle.length() > 0) { detalle.append(','); }
            detalle.append("{\"idPregunta\":\"").append(pregunta.idPregunta())
                    .append("\",\"peso\":").append(peso.toPlainString())
                    .append(",\"respondida\":").append(respondida)
                    .append(",\"correcta\":").append(correcta)
                    .append(",\"pesoObtenido\":").append((correcta ? peso : BigDecimal.ZERO).toPlainString())
                    .append('}');
        }
        BigDecimal valor = obtenido.multiply(ESCALA_MAXIMA).divide(total, 2, RoundingMode.HALF_UP);
        String documento = "{\"estrategia\":\"" + IDENTIFICADOR + "\",\"ratificada\":true"
                + ",\"caracter\":\"formativa\""
                + ",\"escala\":\"0-5\",\"redondeo\":\"HALF_UP,2\""
                + ",\"pesoTotal\":" + total.toPlainString()
                + ",\"pesoObtenido\":" + obtenido.toPlainString()
                + ",\"preguntas\":[" + detalle + "]}";
        return new ResultadoEvaluacion(valor, documento);
    }

    private static void validar(EntradaEvaluacion entrada) {
        if (entrada.preguntas().isEmpty()) {
            throw new EvaluacionRechazadaException("SIN_PREGUNTAS", "The evaluation input has no questions");
        }
        // C1: accessibility is checked for every question first, so it is reported even if weights are also missing.
        // DC-57: accessibility is M3's delivery condition (active scenario, visible, Lista). M3 delivers a case
        // all-or-nothing, so one inaccessible question means no question of the case reached the student.
        for (var pregunta : entrada.preguntas()) {
            if (!pregunta.accesibleAlEstudiante()) {
                throw new EvaluacionRechazadaException("PREGUNTAS_NO_ACCESIBLES",
                        "Question " + pregunta.idPregunta() + " was not accessible to the student");
            }
        }
        for (var pregunta : entrada.preguntas()) {
            if (pregunta.valorPregunta().isEmpty()) {
                throw new EvaluacionRechazadaException("PESOS_INCOMPLETOS",
                        "Question " + pregunta.idPregunta() + " has no RubricaPregunta weight");
            }
            if (pregunta.valorPregunta().get().signum() <= 0) {
                throw new EvaluacionRechazadaException("PESOS_INCONSISTENTES",
                        "Question " + pregunta.idPregunta() + " has a non-positive weight");
            }
        }
        // Weights are checked for every question first, so an incomplete rubric is always reported as such.
        for (var pregunta : entrada.preguntas()) {
            long correctas = pregunta.opciones().stream().filter(EntradaEvaluacion.OpcionEvaluable::esCorrecta).count();
            if (correctas != 1) {
                throw new EvaluacionRechazadaException("CLAVE_INCONSISTENTE",
                        "Question " + pregunta.idPregunta() + " must have exactly one correct option, has " + correctas);
            }
        }
    }
}
