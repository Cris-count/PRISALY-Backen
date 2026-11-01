package com.prisaly.m3.domain.rule;

import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.Rubrica;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** M3-INC-09D: storage structure safeguards, without academic scoring policies. */
public final class RubricaStructureValidation {
    private RubricaStructureValidation() { }

    public static UUID requireUuid(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Rubric structure requires UUID identifiers");
        }
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("UUID must use the complete canonical representation");
        }
        return uuid;
    }

    public static void requirePositiveNumeric8_2(BigDecimal value) {
        // Check exact representability; keep the supplied object and scale unchanged.
        // Trailing zeros do not require rounding (12.500 represents exactly 12.50).
        if (value == null || value.signum() <= 0
                || value.compareTo(new BigDecimal("999999.99")) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Value must be positive and exactly representable as NUMERIC(8,2)");
        }
    }

    public static void assertBelongsToCase(String caseId, List<Escenario> escenarios, Rubrica rubrica) {
        if (rubrica == null || !rubrica.hasExplicitStructure()) {
            return;
        }
        if (!requireUuid(caseId).equals(requireUuid(rubrica.idCaso()))) {
            throw new IllegalArgumentException("Rubrica.idCaso must identify its containing case");
        }
        Set<UUID> questions = new HashSet<>();
        for (Escenario escenario : escenarios) {
            for (var pregunta : escenario.preguntas()) {
                if (!questions.add(requireUuid(pregunta.id()))) {
                    throw new IllegalArgumentException("Question identities must be unambiguous within the case");
                }
            }
        }
        for (var association : rubrica.preguntas()) {
            if (!questions.contains(requireUuid(association.idPregunta()))) {
                throw new IllegalArgumentException("Rubric question must belong to its containing case");
            }
        }
    }
}
