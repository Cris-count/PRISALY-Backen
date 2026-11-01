package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoPublication;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** M3-INC-09B: structural metadata transport, with unresolved operations explicitly isolated. */
class CasoMetadataTest {

    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-01-03T04:05:06.654321Z");
    private static final Instant LOCKED = Instant.parse("2026-01-04T05:06:07.123456Z");
    private CaseAuthorizationPort authorization;
    private CasoApplicationService service;

    @BeforeEach
    void setUp() {
        authorization = mock(CaseAuthorizationPort.class);
        when(authorization.canModifyCase(anyString(), anyString())).thenReturn(true);
        when(authorization.canPublishCase(anyString(), anyString())).thenReturn(true);
        service = new CasoApplicationService(authorization);
    }

    @Test
    void schedulingAvailabilityMatchesStateAndActivityMatrixWithoutChangingCase() {
        for (CasoEstado state : List.of(CasoEstado.PUBLICADO, CasoEstado.BORRADOR, CasoEstado.ARCHIVADO)) {
            for (boolean active : List.of(true, false)) {
                Caso source = metadataCase(state, active, CREATED, UPDATED, LOCKED);
                Caso expected = metadataCase(state, active, CREATED, UPDATED, LOCKED);
                var scenarios = source.escenarios();
                var rubric = source.rubrica();

                boolean available = service.isAvailableForScheduling(source);

                assertEquals(state == CasoEstado.PUBLICADO && active, available,
                        "state=" + state + ", activo=" + active);
                assertEquals(state == CasoEstado.PUBLICADO, source.isPublished(),
                        "Publication status remains independent of activity");
                assertEquals(expected, source, "Availability must preserve all content and metadata");
                assertSame(scenarios, source.escenarios());
                assertSame(rubric, source.rubrica());
            }
        }
    }

    @Test
    void schedulingAvailabilityDoesNotRequireRubricQuestionsOrLockMetadata() {
        Caso source = new Caso("case", "Caso", "docente", 300, CasoEstado.PUBLICADO,
                List.of(), null, false, null, null, true, null, null, null);

        assertTrue(service.isAvailableForScheduling(source));
        assertTrue(source.escenarios().isEmpty());
        assertNull(source.rubrica());
        assertNull(source.fechaCreacion());
        assertNull(source.fechaActualizacion());
        assertNull(source.fechaBloqueoAcademico());
        assertFalse(source.contenidoEvaluableBloqueado());
    }

    @Test
    void builderCarriesExplicitNarrativesActivityAndDates() {
        Caso draft = service.createDraft("docente", completeBuilder()
                .withHistoria("Historia")
                .withDesarrolloSituacional("Desarrollo")
                .withActivo(false)
                .withFechaCreacion(CREATED)
                .withFechaActualizacion(UPDATED));

        assertEquals("Historia", draft.historia());
        assertEquals("Desarrollo", draft.desarrolloSituacional());
        assertFalse(draft.activo());
        assertEquals(CREATED, draft.fechaCreacion());
        assertEquals(UPDATED, draft.fechaActualizacion());
        assertNull(draft.fechaBloqueoAcademico());
        assertFalse(draft.contenidoEvaluableBloqueado());
        assertEquals(CasoEstado.BORRADOR, draft.estado());
    }

    @Test
    void transientConstructionDoesNotInventDatesOrOptionalText() {
        Caso draft = completeBuilder().buildDraft();
        assertNull(draft.historia());
        assertNull(draft.desarrolloSituacional());
        assertNull(draft.fechaCreacion()); // not a persisted row; assignment policy is pending
        assertNull(draft.fechaActualizacion());
        assertNull(draft.fechaBloqueoAcademico());
        assertTrue(draft.activo()); // existing V001 default, not a reactivation operation
    }

    @Test
    void publicationPreservesEveryMetadataValue() {
        Caso draft = metadataCase(CasoEstado.BORRADOR, false, CREATED, UPDATED, LOCKED);

        Caso published = service.publish("docente", draft);

        assertEquals(CasoEstado.PUBLICADO, published.estado());
        assertMetadataEquals(draft, published);
        assertEquals(CasoEstado.BORRADOR, draft.estado());
    }

    @Test
    void publicationPreservesNullMetadata() {
        Caso published = service.publish("docente", completeBuilder().buildDraft());
        assertNull(published.historia());
        assertNull(published.desarrolloSituacional());
        assertNull(published.fechaCreacion());
        assertNull(published.fechaActualizacion());
        assertNull(published.fechaBloqueoAcademico());
    }

    @Test
    void lockPreservesMetadataWithoutGeneratingTimestamp() {
        Caso source = metadataCase(CasoEstado.PUBLICADO, false, CREATED, UPDATED, null);

        Caso locked = service.lockAfterFirstSessionScheduled(source);

        assertTrue(locked.contenidoEvaluableBloqueado());
        assertNull(locked.fechaBloqueoAcademico());
        assertEquals(source.historia(), locked.historia());
        assertEquals(source.desarrolloSituacional(), locked.desarrolloSituacional());
        assertEquals(source.activo(), locked.activo());
        assertEquals(CREATED, locked.fechaCreacion());
        assertEquals(UPDATED, locked.fechaActualizacion());
        assertEquals(CasoEstado.PUBLICADO, locked.estado());
        assertFalse(source.contenidoEvaluableBloqueado());
    }

    @Test
    void knownLockDateImpliesLockAndIsNeverReplaced() {
        Caso source = metadataCase(CasoEstado.PUBLICADO, true, CREATED, UPDATED, LOCKED);

        assertTrue(source.contenidoEvaluableBloqueado());
        assertSame(source, service.lockAfterFirstSessionScheduled(source));
        assertEquals(LOCKED, source.fechaBloqueoAcademico());
        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> service.replaceEvaluableContent("docente", source, source));
        assertEquals(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, error.code());
    }

    @Test
    void replacementPreservesMetadataAndCarriesSuppliedNarratives() {
        Caso original = metadataCase(CasoEstado.BORRADOR, false, CREATED, UPDATED, null);
        Caso replacement = completeBuilder().withTitulo("Titulo nuevo")
                .withHistoria("Historia nueva").withDesarrolloSituacional("Desarrollo nuevo")
                .withActivo(false).withFechaCreacion(CREATED).withFechaActualizacion(UPDATED).buildDraft();

        Caso result = service.replaceEvaluableContent("docente", original, replacement);

        assertSame(replacement, result);
        assertEquals("Historia nueva", result.historia());
        assertEquals("Desarrollo nuevo", result.desarrolloSituacional());
        assertFalse(result.activo());
        assertEquals(CREATED, result.fechaCreacion());
        assertEquals(UPDATED, result.fechaActualizacion());
        assertEquals("Historia", original.historia());
    }

    @Test
    void replacementCannotBecomeAnActivityOrAuditMetadataOperation() {
        Caso original = metadataCase(CasoEstado.BORRADOR, false, CREATED, UPDATED, null);
        List<Caso> replacements = List.of(
                completeBuilder().withActivo(true).withFechaCreacion(CREATED).withFechaActualizacion(UPDATED).buildDraft(),
                completeBuilder().withActivo(false).withFechaCreacion(null).withFechaActualizacion(UPDATED).buildDraft(),
                completeBuilder().withActivo(false).withFechaCreacion(CREATED).withFechaActualizacion(null).buildDraft(),
                metadataCase(CasoEstado.BORRADOR, false, CREATED, UPDATED, LOCKED));
        for (Caso replacement : replacements) {
            CasoDomainException error = assertThrows(CasoDomainException.class,
                    () -> service.replaceEvaluableContent("docente", original, replacement));
            assertEquals(CasoApplicationService.CODE_CASE_METADATA_CHANGE_PENDING, error.code());
        }
    }

    @Test
    void archivedCanBeRepresentedButExistingOperationsCannotMutateOrRepublishIt() {
        Caso archived = metadataCase(CasoEstado.ARCHIVADO, false, CREATED, UPDATED, null);
        assertEquals(CasoEstado.ARCHIVADO, archived.estado());
        assertFalse(archived.isPublished());
        assertFalse(service.isAvailableForScheduling(archived));
        List<Runnable> operations = List.of(
                () -> service.publish("docente", archived),
                () -> CasoValidation.assertPublishable(archived),
                () -> service.replaceEvaluableContent("docente", archived, archived),
                () -> service.lockAfterFirstSessionScheduled(archived),
                () -> service.createIndependentCopy("docente", archived, "copy"));
        for (Runnable operation : operations) {
            CasoDomainException error = assertThrows(CasoDomainException.class, operation::run);
            assertEquals(CasoValidation.CODE_ARCHIVED_CASE_OPERATION_PENDING, error.code());
        }
        assertEquals(CREATED, archived.fechaCreacion());
        assertEquals(CasoEstado.ARCHIVADO, archived.estado());
    }

    @Test
    void replacementCannotIntroduceArchivedState() {
        Caso original = metadataCase(CasoEstado.BORRADOR, true, null, null, null);
        Caso archivedReplacement = metadataCase(CasoEstado.ARCHIVADO, true, null, null, null);
        CasoDomainException error = assertThrows(CasoDomainException.class,
                () -> service.replaceEvaluableContent("docente", original, archivedReplacement));
        assertEquals(CasoValidation.CODE_ARCHIVED_CASE_OPERATION_PENDING, error.code());
    }

    @Test
    void archivedSafeguardDoesNotBypassAuthorization() {
        Caso archived = metadataCase(CasoEstado.ARCHIVADO, false, CREATED, UPDATED, null);
        when(authorization.canPublishCase("sin-permiso", archived.id())).thenReturn(false);
        when(authorization.canModifyCase("sin-permiso", archived.id())).thenReturn(false);
        for (Runnable operation : List.<Runnable>of(
                () -> service.publish("sin-permiso", archived),
                () -> service.replaceEvaluableContent("sin-permiso", archived, archived),
                () -> service.createIndependentCopy("sin-permiso", archived, "copy"))) {
            CasoDomainException error = assertThrows(CasoDomainException.class, operation::run);
            assertEquals(CasoApplicationService.CODE_FORBIDDEN, error.code());
        }
    }

    @Test
    void transientCopyPreservesNarrativesAndActivityButNeverSourceLockDate() {
        Caso source = metadataCase(CasoEstado.PUBLICADO, false, null, null, LOCKED);

        Caso copy = service.createIndependentCopy("docente", source, "copy");

        assertEquals(source.historia(), copy.historia());
        assertEquals(source.desarrolloSituacional(), copy.desarrolloSituacional());
        assertEquals(source.activo(), copy.activo());
        assertEquals(source.titulo(), copy.titulo());
        assertEquals(source.docenteId(), copy.docenteId());
        assertEquals(source.tiempoEstimadoSegundos(), copy.tiempoEstimadoSegundos());
        assertNull(copy.fechaCreacion());
        assertNull(copy.fechaActualizacion());
        assertNull(copy.fechaBloqueoAcademico());
        assertFalse(copy.contenidoEvaluableBloqueado());
        assertEquals(CasoEstado.BORRADOR, copy.estado());
        assertNotEquals(source.escenarios().getFirst().id(), copy.escenarios().getFirst().id());
        assertEquals(LOCKED, source.fechaBloqueoAcademico());
    }

    @Test
    void copyOfDatedSourceIsExplicitlyPendingRatherThanInventingMetadata() {
        for (Caso source : List.of(
                metadataCase(CasoEstado.PUBLICADO, true, CREATED, null, null),
                metadataCase(CasoEstado.PUBLICADO, true, null, UPDATED, null))) {
            CasoDomainException error = assertThrows(CasoDomainException.class,
                    () -> service.createIndependentCopy("docente", source, "copy"));
            assertEquals(ContenidoEvaluableLock.CODE_COPY_METADATA_POLICY_PENDING, error.code());
        }
    }

    @Test
    void originalPublicationAndTimeValidationsRemainInForce() {
        Caso incomplete = new CasoBuilder().withId("case").withTitulo("Caso")
                .withDocenteId("docente").withTiempoEstimadoSegundos(300)
                .withHistoria("Historia").withFechaCreacion(CREATED).buildDraft();
        CasoDomainException incompleteError = assertThrows(CasoDomainException.class,
                () -> service.publish("docente", incomplete));
        assertEquals(CasoValidation.CODE_CASE_INCOMPLETE, incompleteError.code());
        Caso published = service.publish("docente", completeBuilder().buildDraft());
        CasoDomainException publishedError = assertThrows(CasoDomainException.class,
                () -> CasoPublication.publish(published));
        assertEquals(CasoPublication.CODE_ALREADY_PUBLISHED, publishedError.code());
        CasoDomainException timeError = assertThrows(CasoDomainException.class,
                () -> completeBuilder().withTiempoEstimadoSegundos(0).buildDraft());
        assertEquals(CasoValidation.CODE_INVALID_ESTIMATED_TIME, timeError.code());
    }

    private static Caso metadataCase(CasoEstado state, boolean active,
            Instant created, Instant updated, Instant lockDate) {
        Caso base = completeBuilder().buildDraft();
        return new Caso(base.id(), base.titulo(), base.docenteId(), base.tiempoEstimadoSegundos(),
                state, base.escenarios(), base.rubrica(), false,
                "Historia", "Desarrollo", active, created, updated, lockDate);
    }

    private static CasoBuilder completeBuilder() {
        return new CasoBuilder().withId("case").withTitulo("Caso").withDocenteId("docente")
                .withTiempoEstimadoSegundos(300).withRubricaId("rubrica")
                .addCriterio("criterio", "Criterio", 10)
                .addEscenario("escenario", "Descripcion")
                .addPreguntaSeleccionUnica("pregunta", "Enunciado", List.of(
                        new OpcionRespuesta("opcion-a", "A", true),
                        new OpcionRespuesta("opcion-b", "B", false))).and();
    }

    private static void assertMetadataEquals(Caso expected, Caso actual) {
        assertEquals(expected.historia(), actual.historia());
        assertEquals(expected.desarrolloSituacional(), actual.desarrolloSituacional());
        assertEquals(expected.activo(), actual.activo());
        assertEquals(expected.fechaCreacion(), actual.fechaCreacion());
        assertEquals(expected.fechaActualizacion(), actual.fechaActualizacion());
        assertEquals(expected.fechaBloqueoAcademico(), actual.fechaBloqueoAcademico());
        assertEquals(expected.contenidoEvaluableBloqueado(), actual.contenidoEvaluableBloqueado());
    }
}
