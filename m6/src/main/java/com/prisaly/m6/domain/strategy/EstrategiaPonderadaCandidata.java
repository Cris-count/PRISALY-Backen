package com.prisaly.m6.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * <b>Candidate</b> weighted strategy (DC-CLAUDE-BE-EJECUCION-04, priority B). The rules below are the
 * evaluation <b>proposal under test</b> supplied by Dirección Central, <b>not ratified academic policy</b>
 * (PF-01, D19, D20 and REG-NC remain pending). It is not a Spring bean, it is not supplied to
 * {@code EvaluarIntentoService} by any production assembler, and its identifier is not selected anywhere.
 *
 * <p>Proposal implemented:
 * <ul>
 *   <li>Per-question weight: {@code RubricaPregunta.valorPregunta} ({@link EntradaEvaluacion.PreguntaEvaluable#valorPregunta()}).</li>
 *   <li>Correct answer = 100% of the weight; incorrect or unanswered = 0.</li>
 *   <li>Scale 0–5: {@code 5 × obtained weight / total weight}, rounded once with {@link RoundingMode#HALF_UP}
 *       to two decimals.</li>
 *   <li>One immutable grade per attempt is guaranteed by the insert-only writer and V003, not here.</li>
 * </ul>
 *
 * <p>Preconditions, checked <b>before</b> computing; any failure throws {@link EvaluacionRechazadaException}
 * and no result exists, so no invalid grade can be recorded:
 * <ul>
 *   <li>{@code SIN_PREGUNTAS}: the input has no questions.</li>
 *   <li>{@code PESOS_INCOMPLETOS}: some question has no {@code RubricaPregunta} weight. Every question in the
 *       input counts; none is excluded by its {@code visible}/{@code escenarioActivo} flags, because no rule to
 *       exclude them is ratified, so an incomplete rubric is refused rather than guessed.</li>
 *   <li>{@code PESOS_INCONSISTENTES}: some weight is not positive.</li>
 *   <li>{@code CLAVE_INCONSISTENTE}: some question does not have exactly one correct option.</li>
 * </ul>
 * The rubric's JSONB {@code definicionEvaluacion} is deliberately ignored: criteria are not interpreted as
 * equivalent weights.
 */
public final class EstrategiaPonderadaCandidata implements EstrategiaEvaluacion {

    /** Identifier recorded as {@code Calificacion.versionPolitica} if an assembler ever selects it (≤ 50). */
    public static final String IDENTIFICADOR = "ponderada-candidata-no-ratificada-v0";

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
        String documento = "{\"estrategia\":\"" + IDENTIFICADOR + "\",\"ratificada\":false"
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
