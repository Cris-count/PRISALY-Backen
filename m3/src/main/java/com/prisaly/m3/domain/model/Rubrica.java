package com.prisaly.m3.domain.model;

import com.prisaly.m3.domain.rule.RubricaStructureValidation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rubric attached to a case. One rubric per case. Explicit associations form an
 * unordered set; legacy criterios remain a separate concept without a SQL mapping.
 * Optional JSON is opaque text, not a scoring formula. Its syntax is not validated here.
 */
public record Rubrica(String id, List<CriterioRubrica> criterios, String idCaso,
        String nombre, String descripcion, BigDecimal puntajeTotal,
        String definicionEvaluacion, Instant fechaCreacion, Set<RubricaPregunta> preguntas) {

    public Rubrica {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rubrica.id is required");
        }
        if (criterios == null) {
            throw new IllegalArgumentException("Rubrica.criterios is required");
        }
        criterios = List.copyOf(criterios);
        preguntas = Set.copyOf(preguntas);
        if (idCaso == null) {
            if (nombre != null || descripcion != null || puntajeTotal != null
                    || definicionEvaluacion != null || fechaCreacion != null || !preguntas.isEmpty()) {
                throw new IllegalArgumentException("Structural rubric metadata requires an explicit case");
            }
        } else {
            UUID rubricId = RubricaStructureValidation.requireUuid(id);
            RubricaStructureValidation.requireUuid(idCaso);
            if (nombre == null || nombre.codePointCount(0, nombre.length()) > 150) {
                throw new IllegalArgumentException("Rubrica.nombre is required and must not exceed 150 characters");
            }
            if (puntajeTotal != null) {
                RubricaStructureValidation.requirePositiveNumeric8_2(puntajeTotal);
            }
            Set<UUID> associationIds = new HashSet<>();
            Set<UUID> questionIds = new HashSet<>();
            for (RubricaPregunta pregunta : preguntas) {
                if (!rubricId.equals(RubricaStructureValidation.requireUuid(pregunta.idRubrica()))
                        || !associationIds.add(RubricaStructureValidation.requireUuid(pregunta.idRubricaPregunta()))
                        || !questionIds.add(RubricaStructureValidation.requireUuid(pregunta.idPregunta()))) {
                    throw new IllegalArgumentException("Rubric associations require matching rubric and unique row/question UUIDs");
                }
            }
        }
    }

    /** Legacy/transient only: no invented name, case reference, dates or SQL associations.
     * This constructor does NOT produce a complete persistable Rubrica row.
     */
    public Rubrica(String id, List<CriterioRubrica> criterios) {
        this(id, criterios, null, null, null, null, null, null, Set.of());
    }

    /** Structural representation may still lack a database-assigned creation date.
     * JSON is opaque document text, never serialized as a JSON string by this model.
     */
    public boolean hasExplicitStructure() {
        return idCaso != null;
    }

    public boolean isComplete() {
        // Preserve the approved legacy rule. Explicit associations do not imply
        // academic completeness: their equivalence/coverage remains pending DD-03.
        return !criterios.isEmpty();
    }
}
