package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;
import com.prisaly.m3.domain.model.RubricaPregunta;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoPublication;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M3-INC-09D. Explicit structural associations do not define DD-03 completeness. */
class RubricaStructureTest {
    private static final String CASE = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String RUBRIC = "bbbbbbbb-0000-0000-0000-000000000001";
    private static final String Q1 = "cccccccc-0000-0000-0000-000000000001";
    private static final String Q2 = "cccccccc-0000-0000-0000-000000000002";
    private static final String A1 = "dddddddd-0000-0000-0000-000000000001";
    private static final String A2 = "dddddddd-0000-0000-0000-000000000002";
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final String JSON = "{\"opaco\":[12.50,null,true,{\"texto\":\"á\\nβ\"}],\"otro\":{}}";

    @Test
    void explicitMetadataRetainsValuesWithoutCalculatingTotalOrTransformingJson() {
        Rubrica rubric = rubric(CREATED, List.of());
        assertEquals(RUBRIC, rubric.id());
        assertEquals(CASE, rubric.idCaso());
        assertEquals("Rúbrica suministrada", rubric.nombre());
        assertEquals("Descripción", rubric.descripcion());
        assertEquals(new BigDecimal("25.75"), rubric.puntajeTotal());
        assertEquals(JSON, rubric.definicionEvaluacion());
        assertEquals(CREATED, rubric.fechaCreacion());
        assertTrue(rubric.hasExplicitStructure());
        assertEquals(Set.of(Q1, Q2), rubric.preguntas().stream()
                .map(RubricaPregunta::idPregunta).collect(java.util.stream.Collectors.toSet()));
        assertEquals(new BigDecimal("12.50"), associationFor(rubric, Q1).valorPregunta());
        assertNull(associationFor(rubric, Q2).criterio());
        assertTrue(rubric.criterios().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> rubric.preguntas().clear());
    }

    @Test
    void optionalMetadataAndEmptyAssociationsRemainUnassigned() {
        Rubrica rubric = new Rubrica(RUBRIC, List.of(), CASE, "Nombre", null, null, null, null, Set.of());
        assertNull(rubric.descripcion());
        assertNull(rubric.puntajeTotal());
        assertNull(rubric.definicionEvaluacion());
        assertNull(rubric.fechaCreacion());
        assertTrue(rubric.preguntas().isEmpty());
        Caso copy = ContenidoEvaluableLock.createIndependentCopy(caseWith(rubric), UUID.randomUUID().toString());
        assertTrue(copy.rubrica().preguntas().isEmpty());
        assertNull(copy.rubrica().definicionEvaluacion());
    }

    @Test
    void opaqueJsonDocumentsAreNotParsedWrappedOrConfusedWithSqlNull() {
        for (String json : List.of("null", "[1,true,null]", "\"texto\"", JSON)) {
            Rubrica rubric = new Rubrica(RUBRIC, List.of(), CASE, "Name", null, null, json, null, Set.of());
            assertEquals(json, rubric.definicionEvaluacion());
            Caso copy = ContenidoEvaluableLock.createIndependentCopy(caseWith(rubric), UUID.randomUUID().toString());
            assertEquals(json, copy.rubrica().definicionEvaluacion());
        }
    }

    @Test
    void legacyConstructorRemainsExplicitlyTransientWithoutManufacturingRequiredName() {
        Rubrica rubric = new Rubrica("legacy", List.of(new CriterioRubrica("c", "Original", 10)));
        assertFalse(rubric.hasExplicitStructure());
        assertNull(rubric.nombre());
        assertNull(rubric.idCaso());
        assertNull(rubric.fechaCreacion());
        assertTrue(rubric.preguntas().isEmpty());
        assertTrue(rubric.isComplete());
    }

    @Test
    void requiredNameAndSqlLengthAreValidatedWithoutTrimmingOrInventingText() {
        assertThrows(IllegalArgumentException.class, () -> named(null));
        assertThrows(IllegalArgumentException.class, () -> named("a".repeat(151)));
        assertEquals("a".repeat(150), named("a".repeat(150)).nombre());
        // V001 specifies NOT NULL, not a nonblank CHECK.
        assertEquals("", named("").nombre());
        assertEquals("😀".repeat(150), named("😀".repeat(150)).nombre());
        assertThrows(IllegalArgumentException.class,
                () -> new Rubrica(RUBRIC, List.of(), null, "Name", null, null, null, null, Set.of()));
    }

    @Test
    void decimalBoundsPreserveExactValuesAndNeverRound() {
        for (String value : List.of("0.01", "12.50", "7.25", "999999.99", "12.500", "1E+5")) {
            BigDecimal supplied = new BigDecimal(value);
            RubricaPregunta association = association(A1, Q1, supplied, null);
            assertSame(supplied, association.valorPregunta());
            Rubrica rubric = new Rubrica(RUBRIC, List.of(), CASE, "Name", null, supplied, null, null, Set.of());
            assertSame(supplied, rubric.puntajeTotal());
        }
        for (String value : List.of("0", "-0.01", "12.501", "0.001", "1000000", "999999.999")) {
            BigDecimal invalid = new BigDecimal(value);
            assertThrows(IllegalArgumentException.class, () -> association(A1, Q1, invalid, null));
            assertThrows(IllegalArgumentException.class,
                    () -> new Rubrica(RUBRIC, List.of(), CASE, "Name", null, invalid, null, null, Set.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> association(A1, Q1, null, null));
    }

    @Test
    void explicitIdentifiersMustBeCompleteUuidsWithoutRegeneratingThem() {
        assertEquals(A1, association(A1, Q1, BigDecimal.ONE, null).idRubricaPregunta());
        assertThrows(IllegalArgumentException.class, () -> association("invalid", Q1, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> association(A1, "1-1-1-1-1", BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RubricaPregunta(A1, "invalid", Q1, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Rubrica("invalid", List.of(), CASE, "Name", null, null, null, null, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Rubrica(RUBRIC, List.of(), "invalid", "Name", null, null, null, null, Set.of()));
    }

    @Test
    void associationRequiresMatchingRubricAndUniqueRowAndQuestionIdentities() {
        RubricaPregunta first = association(A1, Q1, BigDecimal.ONE, null);
        assertThrows(IllegalArgumentException.class, () -> withAssociations(Set.of(first,
                association(A2, Q1, new BigDecimal("2"), null))));
        assertThrows(IllegalArgumentException.class, () -> withAssociations(Set.of(first,
                association(A1, Q2, new BigDecimal("2"), null))));
        assertThrows(IllegalArgumentException.class, () -> withAssociations(Set.of(
                new RubricaPregunta(A1, UUID.randomUUID().toString(), Q1, BigDecimal.ONE, null))));
    }

    @Test
    void aggregateRejectsWrongCaseExternalQuestionAndAmbiguousQuestionIdentity() {
        assertThrows(IllegalArgumentException.class, () -> caseWith(
                new Rubrica(RUBRIC, List.of(), UUID.randomUUID().toString(), "Name",
                        null, null, null, null, Set.of())));
        assertThrows(IllegalArgumentException.class, () -> caseWith(withAssociations(Set.of(
                association(A1, UUID.randomUUID().toString(), BigDecimal.ONE, null)))));
        Rubrica rubric = rubric(null, List.of());
        Pregunta q = question(Q1);
        assertThrows(IllegalArgumentException.class, () -> new Caso(CASE, "Case", "Teacher", 300,
                CasoEstado.BORRADOR, List.of(new Escenario("e", "Scenario", List.of(q, q))), rubric, false));
    }

    @Test
    void builderTransportsExplicitRubricAndRejectsAmbiguousMixWithLegacyFields() {
        Rubrica rubric = rubric(null, List.of());
        CasoBuilder builder = builder(rubric);
        assertSame(rubric, builder.buildDraft().rubrica());
        assertThrows(CasoDomainException.class, () -> builder.withRubricaId("legacy").buildDraft());
        assertThrows(CasoDomainException.class,
                () -> builder(rubric).addCriterio("c", "Legacy", 1).buildDraft());
    }

    @Test
    void publicationAndLockRetainMetadataAndApprovedLegacyCompletenessRule() {
        Rubrica rubric = rubric(CREATED, List.of(new CriterioRubrica("legacy", "Existing criterion", 1)));
        Caso original = caseWith(rubric);
        Caso published = CasoPublication.publish(original);
        assertEquals(CasoEstado.PUBLICADO, published.estado());
        assertSame(rubric, published.rubrica());
        Caso locked = ContenidoEvaluableLock.lockAfterFirstSessionScheduled(published);
        assertSame(rubric, locked.rubrica());
        assertTrue(locked.contenidoEvaluableBloqueado());
        assertNull(locked.fechaBloqueoAcademico());
        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> ContenidoEvaluableLock.assertEditable(locked));
        assertEquals(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, error.code());
        // Associations alone cannot silently become an approved completeness policy.
        CasoDomainException incomplete = assertThrows(CasoDomainException.class,
                () -> CasoPublication.publish(caseWith(rubric(null, List.of()))));
        assertEquals(CasoValidation.CODE_INCOMPLETE_RUBRIC, incomplete.code());
    }

    @Test
    void copyRemapsEachExplicitReferenceWithoutInferringAssociationByOrder() {
        Rubrica rubric = rubric(null, List.of(new CriterioRubrica("legacy", "Independent concept", 2)));
        Caso original = ContenidoEvaluableLock.lockAfterFirstSessionScheduled(caseWith(rubric));
        String newCaseId = UUID.randomUUID().toString();
        Caso copy = ContenidoEvaluableLock.createIndependentCopy(original, newCaseId);
        Caso second = ContenidoEvaluableLock.createIndependentCopy(original, UUID.randomUUID().toString());
        assertEquals(newCaseId, copy.id());
        assertEquals(newCaseId, copy.rubrica().idCaso());
        assertNotEquals(rubric.id(), copy.rubrica().id());
        assertEquals(rubric.nombre(), copy.rubrica().nombre());
        assertEquals(rubric.descripcion(), copy.rubrica().descripcion());
        assertEquals(rubric.puntajeTotal(), copy.rubrica().puntajeTotal());
        assertEquals(JSON, copy.rubrica().definicionEvaluacion());
        assertNull(copy.rubrica().fechaCreacion());
        assertEquals(original.docenteId(), copy.docenteId());
        assertEquals(original.tiempoEstimadoSegundos(), copy.tiempoEstimadoSegundos());
        assertEquals(CasoEstado.BORRADOR, copy.estado());
        assertFalse(copy.contenidoEvaluableBloqueado());
        assertTrue(original.contenidoEvaluableBloqueado());
        assertSame(rubric, original.rubrica());
        assertEquals(2, copy.rubrica().preguntas().size());
        Set<String> ids = new HashSet<>();
        ids.add(copy.rubrica().id());
        for (int i = 0; i < original.escenarios().getFirst().preguntas().size(); i++) {
            Pregunta sourceQuestion = original.escenarios().getFirst().preguntas().get(i);
            Pregunta copiedQuestion = copy.escenarios().getFirst().preguntas().get(i);
            RubricaPregunta sourceAssociation = associationFor(rubric, sourceQuestion.id());
            RubricaPregunta copiedAssociation = associationFor(copy.rubrica(), copiedQuestion.id());
            assertNotSame(sourceAssociation, copiedAssociation);
            assertNotEquals(sourceAssociation.idRubricaPregunta(), copiedAssociation.idRubricaPregunta());
            assertNotEquals(copiedAssociation.idRubricaPregunta(),
                    associationFor(second.rubrica(), second.escenarios().getFirst().preguntas().get(i).id())
                            .idRubricaPregunta());
            assertEquals(copy.rubrica().id(), copiedAssociation.idRubrica());
            assertEquals(sourceAssociation.valorPregunta(), copiedAssociation.valorPregunta());
            assertEquals(sourceAssociation.criterio(), copiedAssociation.criterio());
            assertTrue(ids.add(copiedAssociation.idRubricaPregunta()));
            assertTrue(ids.add(copiedQuestion.id()));
            UUID.fromString(copiedAssociation.idRubricaPregunta());
            UUID.fromString(copiedQuestion.id());
            assertNotEquals(sourceQuestion.id(), copiedQuestion.id());
        }
        assertNotSame(rubric.criterios().getFirst(), copy.rubrica().criterios().getFirst());
        assertEquals(rubric.criterios().getFirst().puntaje(), copy.rubrica().criterios().getFirst().puntaje());
        assertEquals(Set.of(Q1, Q2), rubric.preguntas().stream().map(RubricaPregunta::idPregunta)
                .collect(java.util.stream.Collectors.toSet()));
        List<String> copiedIds = academicIds(copy);
        assertEquals(copiedIds.size(), new HashSet<>(copiedIds).size());
        copiedIds.forEach(UUID::fromString);
        assertTrue(Collections.disjoint(academicIds(original), copiedIds));
        assertTrue(Collections.disjoint(copiedIds, academicIds(second)));
    }

    @Test
    void copyRejectsUnapprovedRubricDatePolicyInsteadOfLosingOrInventingDate() {
        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> ContenidoEvaluableLock.createIndependentCopy(caseWith(rubric(CREATED, List.of())),
                        UUID.randomUUID().toString()));
        assertEquals(ContenidoEvaluableLock.CODE_COPY_METADATA_POLICY_PENDING, error.code());
    }

    @Test
    void uuidCaseDifferencesCannotBypassCopyRootIdentityCheck() {
        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> ContenidoEvaluableLock.createIndependentCopy(caseWith(rubric(null, List.of())),
                        CASE.toUpperCase(java.util.Locale.ROOT)));
        assertEquals(ContenidoEvaluableLock.CODE_COPY_CASE_ID_EQUALS_SOURCE, error.code());
    }

    @Test
    void authorizationStillPrecedesCopyValidation() {
        CasoApplicationService service = new CasoApplicationService(new CaseAuthorizationPort() {
            public boolean canModifyCase(String user, String id) { return false; }
            public boolean canPublishCase(String user, String id) { return false; }
        });
        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> service.createIndependentCopy("user", caseWith(rubric(CREATED, List.of())), CASE));
        assertEquals(CasoApplicationService.CODE_FORBIDDEN, error.code());
    }

    private static Rubrica named(String name) {
        return new Rubrica(RUBRIC, List.of(), CASE, name, null, null, null, null, Set.of());
    }

    private static Rubrica withAssociations(Set<RubricaPregunta> associations) {
        return new Rubrica(RUBRIC, List.of(), CASE, "Name", null, null, null, null, associations);
    }

    private static Rubrica rubric(Instant created, List<CriterioRubrica> legacy) {
        // Deliberately unrelated total: the model must not invent a sum formula.
        return new Rubrica(RUBRIC, legacy, CASE, "Rúbrica suministrada", "Descripción",
                new BigDecimal("25.75"), JSON, created, Set.of(
                        association(A2, Q1, new BigDecimal("12.50"), "Criterio explícito"),
                        association(A1, Q2, new BigDecimal("7.25"), null)));
    }

    private static RubricaPregunta association(String id, String question, BigDecimal value, String criterion) {
        return new RubricaPregunta(id, RUBRIC, question, value, criterion);
    }

    private static RubricaPregunta associationFor(Rubrica rubric, String questionId) {
        return rubric.preguntas().stream().filter(a -> a.idPregunta().equals(questionId)).findFirst().orElseThrow();
    }

    private static Pregunta question(String id) {
        return new Pregunta(id, "Question " + id, List.of(new OpcionRespuesta("option-" + id, "Correct", true)));
    }

    private static CasoBuilder builder(Rubrica rubric) {
        return new CasoBuilder().withId(CASE).withTitulo("Case").withDocenteId("Teacher")
                .withTiempoEstimadoSegundos(300).withRubrica(rubric)
                .addEscenario("scenario", "Scenario").addPreguntaSeleccionUnica(question(Q1))
                .addPreguntaSeleccionUnica(question(Q2)).and();
    }

    private static Caso caseWith(Rubrica rubric) {
        return builder(rubric).buildDraft();
    }

    private static List<String> academicIds(Caso caso) {
        List<String> ids = new ArrayList<>();
        ids.add(caso.id());
        for (Escenario escenario : caso.escenarios()) {
            ids.add(escenario.id());
            for (Pregunta pregunta : escenario.preguntas()) {
                ids.add(pregunta.id());
                pregunta.opciones().forEach(opcion -> ids.add(opcion.id()));
            }
        }
        ids.add(caso.rubrica().id());
        caso.rubrica().criterios().forEach(criterio -> ids.add(criterio.id()));
        caso.rubrica().preguntas().forEach(pregunta -> ids.add(pregunta.idRubricaPregunta()));
        return ids;
    }
}
