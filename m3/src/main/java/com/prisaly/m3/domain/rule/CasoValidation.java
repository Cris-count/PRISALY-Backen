package com.prisaly.m3.domain.rule;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;

/**
 * Pure validation rules that a case must pass before publication.
 */
public final class CasoValidation {

    public static final String CODE_CASE_INCOMPLETE = "CASE_INCOMPLETE";
    public static final String CODE_INVALID_ESTIMATED_TIME = "INVALID_ESTIMATED_TIME";
    public static final String CODE_INVALID_QUESTION = "INVALID_QUESTION";
    public static final String CODE_INCONSISTENT_OPTIONS = "INCONSISTENT_OPTIONS";
    public static final String CODE_INCOMPLETE_RUBRIC = "INCOMPLETE_RUBRIC";

    private CasoValidation() {
    }

    public static void assertValidEstimatedTime(int tiempoEstimadoSegundos) {
        if (tiempoEstimadoSegundos <= 0) {
            throw new CasoDomainException(
                    CODE_INVALID_ESTIMATED_TIME,
                    "Case estimated time in seconds must be greater than zero");
        }
    }

    public static void assertPublishable(Caso caso) {
        if (caso.questionCount() == 0) {
            throw new CasoDomainException(
                    CODE_CASE_INCOMPLETE,
                    "A case without questions cannot be published");
        }
        for (Escenario escenario : caso.escenarios()) {
            for (Pregunta pregunta : escenario.preguntas()) {
                assertValidSingleChoiceQuestion(pregunta);
            }
        }
        assertRubricComplete(caso.rubrica());
    }

    public static void assertValidSingleChoiceQuestion(Pregunta pregunta) {
        if (pregunta.opciones().isEmpty()) {
            throw new CasoDomainException(
                    CODE_INVALID_QUESTION,
                    "Question " + pregunta.id() + " has no answer options");
        }
        long correctCount = pregunta.correctOptionCount();
        if (correctCount == 0) {
            throw new CasoDomainException(
                    CODE_INCONSISTENT_OPTIONS,
                    "Question " + pregunta.id() + " must have exactly one correct option");
        }
        if (correctCount > 1) {
            throw new CasoDomainException(
                    CODE_INCONSISTENT_OPTIONS,
                    "Question " + pregunta.id() + " must have exactly one correct option");
        }
    }

    public static void assertRubricComplete(Rubrica rubrica) {
        if (rubrica == null || !rubrica.isComplete()) {
            throw new CasoDomainException(
                    CODE_INCOMPLETE_RUBRIC,
                    "A case with an incomplete rubric cannot be validated or published");
        }
    }
}
