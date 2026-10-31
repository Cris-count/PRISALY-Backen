package com.prisaly.m3.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m3.application.update.CasoUpdateCommand.*;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CasoUpdateContractTest {
    private static final UUID CASE = UUID.randomUUID();
    private static final UUID SCENARIO = UUID.randomUUID();
    private static final UUID QUESTION = UUID.randomUUID();
    private static final UUID RUBRIC = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void commandsRepresentSingleExplicitAssignmentsIncludingNullWithoutAnOptionalAggregateDto() {
        var command = new ActualizarDatosCasoCommand(CampoCaso.HISTORIA, null);
        assertEquals(CampoCaso.HISTORIA, command.campo());
        assertNull(command.valor());
        assertEquals("Nuevo", new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Nuevo").valor());
        assertNull(new ActualizarEscenarioCommand(SCENARIO, CampoEscenario.CONTEXTO, null).valor());
        assertNull(new ActualizarPreguntaCommand(QUESTION, CampoPregunta.TEXTO_AYUDA, null).valor());
    }

    @Test
    void requiredFieldsTypesAndPositiveOrdersAreValidatedWithoutCoercion() {
        assertThrows(IllegalArgumentException.class, () -> new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, null));
        assertThrows(IllegalArgumentException.class, () -> new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, " "));
        assertThrows(IllegalArgumentException.class, () -> new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "a".repeat(151)));
        assertEquals("😀".repeat(150), new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "😀".repeat(150)).valor());
        assertThrows(IllegalArgumentException.class, () -> new ActualizarDatosCasoCommand(CampoCaso.TIEMPO_ESTIMADO_SEGUNDOS, "300"));
        assertThrows(IllegalArgumentException.class, () -> new ActualizarDatosCasoCommand(CampoCaso.TIEMPO_ESTIMADO_SEGUNDOS, 0));
        assertThrows(IllegalArgumentException.class, () -> new ActualizarPreguntaCommand(QUESTION, CampoPregunta.ENUNCIADO, null));
        assertThrows(IllegalArgumentException.class, () -> new ActualizarPreguntaCommand(QUESTION, CampoPregunta.ORDEN, -1));
        assertThrows(NullPointerException.class, () -> new EliminarOpcionCommand(null));
        assertThrows(NullPointerException.class, () -> new ActualizarEscenarioCommand(null, CampoEscenario.TITULO, "Text"));
    }

    @Test
    void scenarioTitleUsesSameUnicodeCharacterLimitInCommandAndDomain() {
        for (String title : List.of("a".repeat(200), "😀".repeat(200), "a".repeat(199) + "😀")) {
            var command = new ActualizarEscenarioCommand(SCENARIO, CampoEscenario.TITULO, title);
            var scenario = new Escenario(SCENARIO.toString(), title, null, null, 19, true, List.of());
            assertEquals(200, title.codePointCount(0, title.length()));
            assertEquals(command.valor(), scenario.titulo());
        }
        assertEquals(400, "😀".repeat(200).length(), "UTF-16 length must not become the SQL character limit");
        assertNull(new Escenario(SCENARIO.toString(), null, null, null, 19, true, List.of()).titulo());
        assertNull(new ActualizarEscenarioCommand(SCENARIO, CampoEscenario.TITULO, null).valor());
    }

    @Test
    void scenarioTitleExceedingSqlLimitIsRejectedByBothLayers() {
        for (String invalid : List.of("a".repeat(201), "😀".repeat(201), "a".repeat(200) + "😀")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ActualizarEscenarioCommand(SCENARIO, CampoEscenario.TITULO, invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> new Escenario(SCENARIO.toString(), invalid, null, null, 19, true, List.of()));
        }
    }

    @Test
    void caseNameUsesSame150CharacterLimitInCommandAndDomain() {
        for (String title : List.of("a".repeat(150), "😀".repeat(150), "a".repeat(149) + "😀")) {
            var command = new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, title);
            var caso = new Caso(CASE.toString(), title, "teacher", 300, CasoEstado.BORRADOR, List.of(), null, false);
            assertEquals(150, title.codePointCount(0, title.length()));
            assertEquals(command.valor(), caso.titulo());
        }
        for (String invalid : List.of("a".repeat(151), "😀".repeat(151), "a".repeat(150) + "😀")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> new Caso(CASE.toString(), invalid, "teacher", 300, CasoEstado.BORRADOR, List.of(), null, false));
        }
    }

    @Test
    void sqlShapedSnapshotReconstructsSupplementaryUnicodeTitlesWithoutLoss() {
        var original = snapshot();
        String name = "😀".repeat(150);
        String title = "😀".repeat(200);
        var c = original.caso();
        var e = original.escenarios().getFirst();
        var snapshot = new CasoJpaAggregateSnapshot(new CasoRow(c.idCaso(), c.idDocente(), name, c.historia(),
                c.desarrolloSituacional(), c.tiempoEstimadoSegundos(), c.estado(), c.activo(), c.fechaBloqueoAcademico(),
                c.fechaCreacion(), c.fechaActualizacion()), List.of(new EscenarioRow(e.idEscenario(), e.idCaso(), title,
                        e.contexto(), e.instrucciones(), e.orden(), e.activo())), original.preguntas(), original.opciones(),
                original.rubrica(), original.rubricaPreguntas());
        Caso loaded = CasoPersistenceMapper.toDomain(snapshot);
        assertEquals(name, loaded.titulo());
        assertEquals(title, loaded.escenarios().getFirst().titulo());
        assertEquals(original.caso().fechaCreacion(), loaded.fechaCreacion());
        assertEquals(Integer.valueOf(original.escenarios().getFirst().orden()), loaded.escenarios().getFirst().orden());
    }

    @Test
    void membershipRejectsUnknownOrForeignElementsWithoutInferringThemFromOrder() {
        var snapshot = snapshot();
        assertDoesNotThrow(() -> CasoCommandUpdateAdapter.validateMembership(snapshot,
                new ActualizarPreguntaCommand(QUESTION, CampoPregunta.ENUNCIADO, "Nuevo")));
        CasoDomainException failure = assertThrows(CasoDomainException.class,
                () -> CasoCommandUpdateAdapter.validateMembership(snapshot,
                        new ActualizarPreguntaCommand(UUID.randomUUID(), CampoPregunta.ENUNCIADO, "Nuevo")));
        assertEquals(CasoCommandUpdateAdapter.ELEMENT_NOT_IN_CASE, failure.code());
        assertThrows(CasoDomainException.class, () -> CasoCommandUpdateAdapter.validateMembership(snapshot,
                new ActualizarEscenarioCommand(UUID.randomUUID(), CampoEscenario.ORDEN, 13)));
        assertThrows(CasoDomainException.class, () -> CasoCommandUpdateAdapter.validateMembership(snapshot,
                new EliminarOpcionCommand(UUID.randomUUID())));
    }

    @Test
    void fingerprintIsIndependentOfAccidentalRowOrderingButIncludesPhysicalOrder() {
        var original = snapshot();
        var extra = new EscenarioRow(UUID.randomUUID(), CASE, null, null, null, 19, false);
        var first = new CasoJpaAggregateSnapshot(original.caso(), List.of(original.escenarios().getFirst(), extra),
                original.preguntas(), original.opciones(), original.rubrica(), original.rubricaPreguntas());
        var reversed = new CasoJpaAggregateSnapshot(original.caso(), List.of(extra, original.escenarios().getFirst()),
                original.preguntas(), original.opciones(), original.rubrica(), original.rubricaPreguntas());
        assertEquals(CasoAggregateFingerprint.of(first), CasoAggregateFingerprint.of(reversed));
        var changed = new CasoJpaAggregateSnapshot(original.caso(), List.of(new EscenarioRow(extra.idEscenario(), CASE,
                null, null, null, 20, false), original.escenarios().getFirst()), original.preguntas(),
                original.opciones(), original.rubrica(), original.rubricaPreguntas());
        assertNotEquals(CasoAggregateFingerprint.of(first), CasoAggregateFingerprint.of(changed));
    }

    @Test
    void fingerprintIncludesEachTableMetadataNullsAndExplicitReferences() {
        var original = snapshot();
        String fingerprint = CasoAggregateFingerprint.of(original);
        assertTrue(fingerprint.matches("m3-v2:[0-9a-f]{64}"));
        var c = original.caso();
        assertDifferent(original, new CasoJpaAggregateSnapshot(new CasoRow(c.idCaso(), c.idDocente(), c.nombre(), "",
                c.desarrolloSituacional(), c.tiempoEstimadoSegundos(), c.estado(), c.activo(), c.fechaBloqueoAcademico(),
                c.fechaCreacion(), c.fechaActualizacion()), original.escenarios(), original.preguntas(), original.opciones(),
                original.rubrica(), original.rubricaPreguntas()));
        var p = original.preguntas().getFirst();
        assertDifferent(original, new CasoJpaAggregateSnapshot(c, original.escenarios(), List.of(new PreguntaRow(
                p.idPregunta(), p.idEscenario(), p.enunciado(), "Ayuda nueva", p.estadoEdicion(), p.visible(), p.orden(),
                p.fechaCreacion(), p.fechaActualizacion())), original.opciones(), original.rubrica(), original.rubricaPreguntas()));
        var o = original.opciones().getFirst();
        assertDifferent(original, new CasoJpaAggregateSnapshot(c, original.escenarios(), original.preguntas(), List.of(
                new OpcionRespuestaRow(o.idOpcion(), o.idPregunta(), "Texto nuevo", o.esCorrecta(), o.explicacion(), o.orden())),
                original.rubrica(), original.rubricaPreguntas()));
        var r = original.rubrica();
        assertDifferent(original, new CasoJpaAggregateSnapshot(c, original.escenarios(), original.preguntas(), original.opciones(),
                new RubricaRow(r.idRubrica(), r.idCaso(), r.nombre(), r.descripcion(), r.puntajeTotal(), "null", r.fechaCreacion()),
                original.rubricaPreguntas()));
        var a = original.rubricaPreguntas().iterator().next();
        assertDifferent(original, new CasoJpaAggregateSnapshot(c, original.escenarios(), original.preguntas(), original.opciones(),
                r, Set.of(new RubricaPreguntaRow(a.idRubricaPregunta(), a.idRubrica(), UUID.randomUUID(), a.valorPregunta(), a.criterio()))));
        assertDifferent(original, new CasoJpaAggregateSnapshot(c, original.escenarios(), original.preguntas(), original.opciones(),
                r, Set.of(new RubricaPreguntaRow(a.idRubricaPregunta(), a.idRubrica(), a.idPregunta(), a.valorPregunta(), "Cambio"))));
    }

    @Test
    void fingerprintAlsoCanonicalizesQuestionOptionAndAssociationRecoveryOrder() {
        var s = snapshot();
        var p = s.preguntas().getFirst();
        var q = new PreguntaRow(UUID.randomUUID(), p.idEscenario(), "Otra", null, "Lista", false, 19, CREATED, null);
        var o = new OpcionRespuestaRow(UUID.randomUUID(), q.idPregunta(), "Otra", false, null, 13);
        var a = new RubricaPreguntaRow(UUID.randomUUID(), RUBRIC, q.idPregunta(), new BigDecimal("7.25"), null);
        var first = new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), List.of(p, q),
                List.of(s.opciones().getFirst(), o), s.rubrica(), Set.of(s.rubricaPreguntas().iterator().next(), a));
        var second = new CasoJpaAggregateSnapshot(s.caso(), s.escenarios(), first.preguntas().reversed(),
                first.opciones().reversed(), s.rubrica(), Set.copyOf(first.rubricaPreguntas()));
        assertEquals(CasoAggregateFingerprint.of(first), CasoAggregateFingerprint.of(second));
    }

    @Test
    void staleAndLockedPreconditionsAreRejectedWithoutInventingAnAuditDateVersion() {
        var original = snapshot();
        var command = new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Nuevo");
        assertEquals(CasoCommandUpdateAdapter.STALE_CASE, assertThrows(CasoDomainException.class,
                () -> CasoCommandUpdateAdapter.validateExpected(original, "obsolete", List.of(command))).code());
        var c = original.caso();
        var locked = new CasoJpaAggregateSnapshot(new CasoRow(c.idCaso(), c.idDocente(), c.nombre(), c.historia(),
                c.desarrolloSituacional(), c.tiempoEstimadoSegundos(), c.estado(), c.activo(), CREATED,
                c.fechaCreacion(), c.fechaActualizacion()), original.escenarios(), original.preguntas(), original.opciones(),
                original.rubrica(), original.rubricaPreguntas());
        assertNotEquals(CasoAggregateFingerprint.of(original), CasoAggregateFingerprint.of(locked));
        assertEquals(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, assertThrows(CasoDomainException.class,
                () -> CasoCommandUpdateAdapter.validateExpected(locked, CasoAggregateFingerprint.of(locked), List.of(command))).code());
        assertDoesNotThrow(() -> CasoCommandUpdateAdapter.validateExpected(original,
                CasoAggregateFingerprint.of(original), List.of(command)));
    }

    private static void assertDifferent(CasoJpaAggregateSnapshot a, CasoJpaAggregateSnapshot b) {
        assertNotEquals(CasoAggregateFingerprint.of(a), CasoAggregateFingerprint.of(b));
    }

    private static CasoJpaAggregateSnapshot snapshot() {
        return new CasoJpaAggregateSnapshot(new CasoRow(CASE, UUID.randomUUID(), "Caso", null, null, 300,
                "Borrador", true, null, CREATED, null),
                List.of(new EscenarioRow(SCENARIO, CASE, null, "Contexto", null, 2, true)),
                List.of(new PreguntaRow(QUESTION, SCENARIO, "Pregunta", null, "Lista", true, 3, CREATED, null)),
                List.of(new OpcionRespuestaRow(UUID.randomUUID(), QUESTION, "Opción", true, null, 4)),
                new RubricaRow(RUBRIC, CASE, "Rubrica", null, new BigDecimal("12.50"), null, CREATED),
                Set.of(new RubricaPreguntaRow(UUID.randomUUID(), RUBRIC, QUESTION, new BigDecimal("12.50"), null)));
    }
}
