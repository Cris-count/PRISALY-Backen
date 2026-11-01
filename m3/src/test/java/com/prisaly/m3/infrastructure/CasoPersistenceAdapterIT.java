package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.CriterioRubrica;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.model.Rubrica;
import com.prisaly.m3.domain.model.RubricaPregunta;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateReader;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoPersistenceAdapter;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** M3-INC-10A: official schema, real SQL constraints and atomic aggregate inserts. */
class CasoPersistenceAdapterIT {
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql",
            "V003__bd0052_integridad.sql", "V004__bd0052_integridad_intentos.sql",
            "V005__bd0052_cierre_integridad.sql", "V006__bd0052_proteccion_relaciones.sql",
            "V007__fase3_politicas_aprobadas.sql", "V008__bloqueo_caso_al_reasignar_sesion.sql",
            "V009__rf09_solo_casos_publicados.sql");
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-01-03T04:05:06.654321Z");
    private static final String JSON = """
            {"texto":"psicología", "nested":{"nulo":null,"booleano":true},
             "lista":[12.50,"dos",false],"numero":12345678901234567890.123456789}
            """;
    private static PostgreSQLContainer postgres;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static CasoRepositoryPort repository;
    private static JpaTransactionManager transactions;
    private static UUID teacher;

    @BeforeAll
    static void prepareDatabase() throws Exception {
        Path migrations = canonicalMigrationDirectory();
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            for (String migration : MIGRATIONS) {
                // Same unchanged scripts as the existing ITs, not Flyway-history validation.
                statement.execute(Files.readString(migrations.resolve(migration), StandardCharsets.UTF_8));
            }
        }
        var datasource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(datasource);
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion")
                VALUES ('DocentePersistenciaM3', 'Fixture M3-INC-10A') RETURNING "idRol"
                """, Integer.class);
        teacher = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo") VALUES (?, ?, ?, ?)
                """, teacher, role, "Docente fixture", "persistence.m3@prisaly.test");
        context = new AnnotationConfigApplicationContext();
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, () -> {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(datasource);
            factory.setPackagesToScan(CasoJpaAggregateReader.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.globally_quoted_identifiers", true, "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        });
        context.registerBean(JpaTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.register(CasoJpaAggregateReader.class, CasoPersistenceAdapter.class);
        context.refresh();
        repository = context.getBean(CasoRepositoryPort.class);
        transactions = context.getBean(JpaTransactionManager.class);
    }

    @AfterAll
    static void closeResources() {
        try {
            if (context != null) { context.close(); }
        } finally {
            if (postgres != null) { postgres.stop(); }
        }
    }

    @Test
    void insertsBareCasesWithOfficialCreationDefaultAndReadsAllStates() {
        for (CasoEstado state : CasoEstado.values()) {
            Caso input = root(id(), teacher.toString(), List.of(), null, state, null);
            repository.insert(input);
            Caso loaded = repository.findById(input.id()).orElseThrow();
            assertEquals(input.id(), loaded.id());
            assertEquals(input.docenteId(), loaded.docenteId());
            assertEquals(input.titulo(), loaded.titulo());
            assertEquals(state, loaded.estado());
            assertEquals(300, loaded.tiempoEstimadoSegundos());
            assertTrue(loaded.activo());
            assertTrue(loaded.escenarios().isEmpty());
            assertNull(loaded.rubrica());
            assertNull(loaded.historia());
            assertNull(loaded.desarrolloSituacional());
            assertNull(loaded.fechaActualizacion());
            assertNull(loaded.fechaBloqueoAcademico());
            assertFalse(loaded.contenidoEvaluableBloqueado());
            assertNotNull(loaded.fechaCreacion());
            assertNull(input.fechaCreacion(), "Immutable input is not assigned a fictitious date");
        }
        assertTrue(repository.findById(id()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repository.findById("invalid"));
    }

    @Test
    void roundTripsAllSixTablesIncludingPhysicalGapsNullsDecimalsDatesAndJson() {
        Caso input = full(JSON);
        repository.insert(input);
        Caso loaded = repository.findById(input.id()).orElseThrow();
        assertEquals(input.id(), loaded.id());
        assertEquals(input.docenteId(), loaded.docenteId());
        assertEquals(input.historia(), loaded.historia());
        assertEquals(input.desarrolloSituacional(), loaded.desarrolloSituacional());
        assertEquals(CREATED, loaded.fechaCreacion());
        assertEquals(UPDATED, loaded.fechaActualizacion());
        assertEquals(input.escenarios(), loaded.escenarios());
        assertEquals(List.of(2, 19), loaded.escenarios().stream().map(Escenario::orden).toList());
        assertFalse(loaded.escenarios().getFirst().activo());
        Pregunta question = loaded.escenarios().getFirst().preguntas().getFirst();
        assertFalse(question.visible());
        assertEquals("Lista", question.estadoEdicion());
        assertEquals(List.of(4, 13), question.opciones().stream().map(OpcionRespuesta::orden).toList());
        assertEquals(input.rubrica().id(), loaded.rubrica().id());
        assertEquals(input.id(), loaded.rubrica().idCaso());
        assertEquals(input.rubrica().nombre(), loaded.rubrica().nombre());
        assertEquals(input.rubrica().descripcion(), loaded.rubrica().descripcion());
        assertEquals(CREATED, loaded.rubrica().fechaCreacion());
        assertEquals(new BigDecimal("25.75"), loaded.rubrica().puntajeTotal());
        assertEquals(input.rubrica().preguntas(), loaded.rubrica().preguntas());
        assertTrue(loaded.rubrica().criterios().isEmpty());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)",
                Boolean.class, JSON, loaded.rubrica().definicionEvaluacion()));
        assertEquals("object", jdbc.queryForObject("""
                SELECT jsonb_typeof("definicionEvaluacion") FROM public."Rubrica" WHERE "idRubrica" = ?
                """, String.class, UUID.fromString(input.rubrica().id())));
    }

    @Test
    void nullableRubricAndQuestionCreationDefaultRemainDistinctFromJsonNull() {
        for (String json : new String[] {null, "null", "[true,null,12.50]", "\"texto\""}) {
            String caseId = id();
            Pregunta question = new Pregunta(id(), "Question", List.of(), null, "Borrador", true, 11, null, null);
            Rubrica rubric = new Rubrica(id(), List.of(), caseId, "Optional", null, null, json, null, Set.of());
            Caso input = root(caseId, teacher.toString(), List.of(
                    new Escenario(id(), null, null, null, 9, true, List.of(question))), rubric, CasoEstado.BORRADOR, null);
            repository.insert(input);
            Caso loaded = repository.findById(caseId).orElseThrow();
            assertNull(loaded.rubrica().descripcion());
            assertNull(loaded.rubrica().puntajeTotal());
            assertTrue(loaded.rubrica().preguntas().isEmpty());
            assertNotNull(loaded.rubrica().fechaCreacion());
            assertNotNull(loaded.escenarios().getFirst().preguntas().getFirst().fechaCreacion());
            if (json == null) {
                assertNull(loaded.rubrica().definicionEvaluacion());
            } else {
                assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)",
                        Boolean.class, json, loaded.rubrica().definicionEvaluacion()));
            }
        }
    }

    @Test
    void invalidJsonFailsAfterChildInsertsAndRollsBackEveryTableWithoutAffectingOtherCase() {
        Caso other = full(JSON);
        repository.insert(other);
        Caso baseline = repository.findById(other.id()).orElseThrow();
        Caso invalid = full("{invalid-json");
        assertSqlState("22P02", () -> repository.insert(invalid));
        assertAbsent(invalid);
        assertEquals(baseline, repository.findById(other.id()).orElseThrow());
    }

    @Test
    void childCollisionCannotOverwriteAnotherCaseAndRollsBackNewRoot() {
        Caso other = full(JSON);
        repository.insert(other);
        Caso baseline = repository.findById(other.id()).orElseThrow();
        var existingOption = other.escenarios().getFirst().preguntas().getFirst().opciones().getFirst();
        Pregunta question = new Pregunta(id(), "Collision", List.of(existingOption), null, "Borrador", true, 2, CREATED, null);
        Caso invalid = root(id(), teacher.toString(), List.of(
                new Escenario(id(), null, null, null, 1, true, List.of(question))), null, CasoEstado.BORRADOR, CREATED);
        assertSqlState("23505", () -> repository.insert(invalid));
        assertTrue(repository.findById(invalid.id()).isEmpty());
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("""
                SELECT count(*) FROM public."Pregunta" WHERE "idPregunta" = ?
                """, Integer.class, UUID.fromString(question.id())));
        assertEquals(baseline, repository.findById(other.id()).orElseThrow());
    }

    @Test
    void associationCollisionRollsBackRootChildrenAndRubricAlreadyInserted() {
        Caso other = full(JSON);
        repository.insert(other);
        Caso baseline = repository.findById(other.id()).orElseThrow();
        List<Integer> counts = academicRowCounts();
        Caso candidate = full(JSON);
        Rubrica r = candidate.rubrica();
        String questionId = candidate.escenarios().getFirst().preguntas().getFirst().id();
        Rubrica conflicting = new Rubrica(r.id(), List.of(), r.idCaso(), r.nombre(), r.descripcion(),
                r.puntajeTotal(), r.definicionEvaluacion(), r.fechaCreacion(), Set.of(
                        new RubricaPregunta(lockedAssociationId(other), r.id(), questionId,
                                new BigDecimal("12.50"), "Collision fixture")));
        Caso invalid = new Caso(candidate.id(), candidate.titulo(), candidate.docenteId(),
                candidate.tiempoEstimadoSegundos(), candidate.estado(), candidate.escenarios(), conflicting,
                false, candidate.historia(), candidate.desarrolloSituacional(), candidate.activo(),
                candidate.fechaCreacion(), candidate.fechaActualizacion(), null);
        assertSqlState("23505", () -> repository.insert(invalid));
        assertTrue(repository.findById(invalid.id()).isEmpty());
        assertEquals(counts, academicRowCounts(), "All six table counts must remain unchanged after the final-stage failure");
        assertEquals(baseline, repository.findById(other.id()).orElseThrow());
    }

    @Test
    void foreignKeysAndUniqueOrdersRemainEnforcedAndFailuresRollback() {
        Caso unknownTeacher = root(id(), id(), List.of(), null, CasoEstado.BORRADOR, CREATED);
        assertSqlState("23503", () -> repository.insert(unknownTeacher));
        assertAbsent(unknownTeacher);
        Caso duplicateOrder = root(id(), teacher.toString(), List.of(
                new Escenario(id(), null, null, null, 2, true, List.of()),
                new Escenario(id(), null, null, null, 2, true, List.of())), null, CasoEstado.BORRADOR, CREATED);
        assertSqlState("23505", () -> repository.insert(duplicateOrder));
        assertAbsent(duplicateOrder);
        Pregunta twoCorrect = new Pregunta(id(), "Two correct", List.of(
                new OpcionRespuesta(id(), "A", true, null, 4),
                new OpcionRespuesta(id(), "B", true, null, 13)), null, "Borrador", true, 2, CREATED, null);
        Caso invalid = root(id(), teacher.toString(), List.of(
                new Escenario(id(), null, null, null, 2, true, List.of(twoCorrect))), null, CasoEstado.BORRADOR, CREATED);
        assertSqlState("23505", () -> repository.insert(invalid));
        assertAbsent(invalid);
    }

    @Test
    void insertJoinsApplicationTransactionAndReaderIntentionallyCannotSeeUncommittedInsert() {
        Caso input = full(JSON);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            repository.insert(input);
            assertTrue(repository.findById(input.id()).isEmpty(), "REQUIRES_NEW reader has independent visibility");
            status.setRollbackOnly();
        });
        assertAbsent(input);
    }

    @Test
    void officialSessionTriggersLockCaseAndProtectAllAcademicChildren() {
        Caso input = root(id(), teacher.toString(), List.of(), null, CasoEstado.PUBLICADO, CREATED);
        repository.insert(input);
        jdbc.update("""
                INSERT INTO public."Sesion" ("idSesion", "idCaso", "idDocenteProgramador",
                    "nombreEspacioAcademico", "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(input.id()), teacher, "Fixture F3-03",
                Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")),
                Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")), 300);
        Caso locked = repository.findById(input.id()).orElseThrow();
        assertTrue(locked.contenidoEvaluableBloqueado());
        assertNotNull(locked.fechaBloqueoAcademico());
        assertSqlState("23514", () -> jdbc.update("""
                INSERT INTO public."Escenario" ("idEscenario", "idCaso", "orden") VALUES (?, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(input.id()), 1));
        assertThrows(IllegalArgumentException.class, () -> repository.insert(locked));
        assertEquals(locked, repository.findById(input.id()).orElseThrow());
        Caso richDraft = full(JSON);
        // Insert an already-published representation without invoking a new transition.
        Caso rich = new Caso(richDraft.id(), richDraft.titulo(), richDraft.docenteId(), richDraft.tiempoEstimadoSegundos(),
                CasoEstado.PUBLICADO, richDraft.escenarios(), richDraft.rubrica(), false,
                richDraft.historia(), richDraft.desarrolloSituacional(), richDraft.activo(),
                richDraft.fechaCreacion(), richDraft.fechaActualizacion(), null);
        repository.insert(rich);
        jdbc.update("""
                INSERT INTO public."Sesion" ("idSesion", "idCaso", "idDocenteProgramador",
                    "nombreEspacioAcademico", "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(rich.id()), teacher, "Fixture completo F3-03",
                Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")),
                Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")), 300);
        Caso baseline = repository.findById(rich.id()).orElseThrow();
        var scenario = rich.escenarios().getFirst();
        var question = scenario.preguntas().getFirst();
        List<String> statements = List.of(
                "UPDATE public.\"Caso\" SET \"historia\" = 'No permitido' WHERE \"idCaso\" = ?",
                "UPDATE public.\"Escenario\" SET \"contexto\" = 'No permitido' WHERE \"idEscenario\" = ?",
                "UPDATE public.\"Pregunta\" SET \"textoAyuda\" = 'No permitido' WHERE \"idPregunta\" = ?",
                "UPDATE public.\"OpcionRespuesta\" SET \"explicacion\" = 'No permitido' WHERE \"idOpcion\" = ?",
                "UPDATE public.\"Rubrica\" SET \"descripcion\" = 'No permitido' WHERE \"idRubrica\" = ?",
                "UPDATE public.\"RubricaPregunta\" SET \"criterio\" = 'No permitido' WHERE \"idRubricaPregunta\" = ?");
        List<String> identifiers = List.of(rich.id(), scenario.id(), question.id(),
                question.opciones().getFirst().id(), rich.rubrica().id(), lockedAssociationId(rich));
        for (int i = 0; i < statements.size(); i++) {
            String sql = statements.get(i);
            UUID identifier = UUID.fromString(identifiers.get(i));
            assertSqlState("23514", () -> jdbc.update(sql, identifier));
        }
        assertEquals(baseline, repository.findById(rich.id()).orElseThrow());
    }

    @Test
    void transversalRubricTriggerAndQuestionUniquenessRemainActive() {
        Caso first = full(JSON);
        Caso second = full(JSON);
        repository.insert(first);
        repository.insert(second);
        assertSqlState("23514", () -> jdbc.update("""
                INSERT INTO public."RubricaPregunta" ("idRubricaPregunta", "idRubrica", "idPregunta", "valorPregunta")
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(first.rubrica().id()),
                UUID.fromString(second.escenarios().getFirst().preguntas().getFirst().id()), BigDecimal.ONE));
        assertSqlState("23505", () -> jdbc.update("""
                INSERT INTO public."RubricaPregunta" ("idRubricaPregunta", "idRubrica", "idPregunta", "valorPregunta")
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(first.rubrica().id()),
                UUID.fromString(first.escenarios().getFirst().preguntas().getFirst().id()), BigDecimal.ONE));
    }

    @Test
    void refusesLossyLegacyRepresentationsUnsupportedUpdateAndSubmicrosecondDates() {
        Caso legacyScenario = root(id(), teacher.toString(), List.of(new Escenario(id(), "Legacy", List.of())),
                null, CasoEstado.BORRADOR, CREATED);
        assertThrows(IllegalArgumentException.class, () -> repository.insert(legacyScenario));
        assertAbsent(legacyScenario);
        Caso legacyRubric = root(id(), teacher.toString(), List.of(),
                new Rubrica(id(), List.of(new CriterioRubrica(id(), "Legacy", 1))), CasoEstado.BORRADOR, CREATED);
        assertThrows(IllegalArgumentException.class, () -> repository.insert(legacyRubric));
        assertAbsent(legacyRubric);
        Caso precise = root(id(), teacher.toString(), List.of(), null,
                CasoEstado.BORRADOR, Instant.parse("2026-01-02T03:04:05.123456789Z"));
        assertThrows(IllegalArgumentException.class, () -> repository.insert(precise));
        assertAbsent(precise);
        assertThrows(UnsupportedOperationException.class, () -> repository.update(precise));
    }

    private static String lockedAssociationId(Caso caso) {
        return caso.rubrica().preguntas().iterator().next().idRubricaPregunta();
    }

    private static Caso full(String json) {
        String caseId = id();
        String rubricId = id();
        Pregunta first = new Pregunta(id(), "Enunciado", List.of(
                new OpcionRespuesta(id(), "Correcta", true, "Explicación", 4),
                new OpcionRespuesta(id(), "Alternativa", false, null, 13)), "Ayuda", "Lista", false, 3, CREATED, UPDATED);
        Pregunta second = new Pregunta(id(), "Otro enunciado", List.of(), null, "Borrador", true, 11, CREATED, null);
        Rubrica rubric = new Rubrica(rubricId, List.of(), caseId, "Rúbrica explícita", "Descripción",
                new BigDecimal("25.75"), json, CREATED, Set.of(
                        new RubricaPregunta(id(), rubricId, first.id(), new BigDecimal("12.50"), "Criterio"),
                        new RubricaPregunta(id(), rubricId, second.id(), new BigDecimal("7.25"), null)));
        return new Caso(caseId, "Caso completo", teacher.toString(), 300, CasoEstado.BORRADOR,
                List.of(new Escenario(id(), "Título", "Contexto", "Instrucciones", 2, false, List.of(first, second)),
                        new Escenario(id(), null, null, null, 19, true, List.of())), rubric, false,
                "Historia", "Desarrollo", true, CREATED, UPDATED, null);
    }

    private static Caso root(String caseId, String owner, List<Escenario> scenarios, Rubrica rubric,
            CasoEstado state, Instant created) {
        return new Caso(caseId, "Caso", owner, 300, state, scenarios, rubric, false,
                null, null, true, created, null, null);
    }

    private static void assertAbsent(Caso caso) {
        assertTrue(repository.findById(caso.id()).isEmpty());
        List<String> tables = new ArrayList<>(List.of("Caso", "Escenario", "Pregunta", "OpcionRespuesta"));
        List<List<UUID>> ids = new ArrayList<>();
        ids.add(List.of(UUID.fromString(caso.id())));
        ids.add(caso.escenarios().stream().map(e -> UUID.fromString(e.id())).toList());
        ids.add(caso.escenarios().stream().flatMap(e -> e.preguntas().stream()).map(p -> UUID.fromString(p.id())).toList());
        ids.add(caso.escenarios().stream().flatMap(e -> e.preguntas().stream()).flatMap(p -> p.opciones().stream())
                .map(o -> UUID.fromString(o.id())).toList());
        List<String> columns = new ArrayList<>(List.of("idCaso", "idEscenario", "idPregunta", "idOpcion"));
        if (caso.rubrica() != null) {
            tables.add("Rubrica"); columns.add("idRubrica");
            ids.add(List.of(UUID.fromString(caso.rubrica().id())));
            tables.add("RubricaPregunta"); columns.add("idRubricaPregunta");
            ids.add(caso.rubrica().preguntas().stream().map(a -> UUID.fromString(a.idRubricaPregunta())).toList());
        }
        for (int i = 0; i < tables.size(); i++) {
            for (UUID id : ids.get(i)) {
                assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                        "SELECT count(*) FROM public.\"" + tables.get(i) + "\" WHERE \"" + columns.get(i) + "\" = ?",
                        Integer.class, id));
            }
        }
    }

    private static void assertSqlState(String expected, Runnable action) {
        RuntimeException failure = assertThrows(RuntimeException.class, action::run);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && expected.equals(sql.getSQLState())) {
                return;
            }
        }
        throw new AssertionError("Expected SQLSTATE " + expected, failure);
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static List<Integer> academicRowCounts() {
        return List.of("Caso", "Escenario", "Pregunta", "OpcionRespuesta", "Rubrica", "RubricaPregunta")
                .stream().map(table -> jdbc.queryForObject(
                        "SELECT count(*) FROM public.\"" + table + "\"", Integer.class)).toList();
    }

    private static Path canonicalMigrationDirectory() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path directory : List.of(cwd.resolve("app/src/main/resources/db/migration"),
                cwd.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (MIGRATIONS.stream().allMatch(name -> Files.isRegularFile(directory.resolve(name)))) {
                return directory;
            }
        }
        throw new IllegalStateException("Official V001-V009 not found from user.dir=" + cwd);
    }
}
