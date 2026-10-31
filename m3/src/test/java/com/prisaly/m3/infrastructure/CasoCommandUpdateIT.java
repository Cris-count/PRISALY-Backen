package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m3.application.port.CasoUpdatePort;
import com.prisaly.m3.application.update.CasoUpdateCommand;
import com.prisaly.m3.application.update.CasoUpdateCommand.*;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import com.prisaly.m3.infrastructure.persistence.jpa.*;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** M3-INC-10B. Real lock waits observed in PostgreSQL; no sleeps or Mockito concurrency. */
class CasoCommandUpdateIT {
    private static PostgreSQLContainer postgres;
    private static DriverManagerDataSource datasource;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static JdbcTemplate jdbc;
    private static CasoPersistenceAdapter inserts;
    private static CasoUpdatePort updates;
    private static UUID teacher;
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00.123456Z");
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql", "V003__bd0052_integridad.sql",
            "V004__bd0052_integridad_intentos.sql", "V005__bd0052_cierre_integridad.sql",
            "V006__bd0052_proteccion_relaciones.sql", "V007__fase3_politicas_aprobadas.sql",
            "V008__bloqueo_caso_al_reasignar_sesion.sql", "V009__rf09_solo_casos_publicados.sql");

    @BeforeAll
    static void prepare() throws Exception {
        Path migrations = migrationDirectory();
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
            for (String name : MIGRATIONS) { statement.execute(Files.readString(migrations.resolve(name), StandardCharsets.UTF_8)); }
        }
        jdbc = new JdbcTemplate(datasource);
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion") VALUES ('DocenteUpdateM3','Fixture 10B') RETURNING "idRol"
                """, Integer.class);
        teacher = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo") VALUES (?, ?, ?, ?)
                """, teacher, role, "Teacher", "update.m3@prisaly.test");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(datasource);
        factory.setPackagesToScan(CasoJpaAggregateReader.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.globally_quoted_identifiers", true));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        assertNotNull(emf);
        var manager = new JpaTransactionManager(emf);
        var reader = new CasoJpaAggregateReader(emf);
        inserts = new CasoPersistenceAdapter(emf, reader, manager);
        updates = new CasoCommandUpdateAdapter(emf, reader, manager);
    }

    @AfterAll
    static void close() {
        try { if (factory != null) { factory.destroy(); } }
        finally { if (postgres != null) { postgres.stop(); } }
    }

    @Test
    void singleFieldUpdatePreservesAllOmittedContentAssociationsMetadataAndOtherCases() {
        var before = saved();
        var other = saved();
        var after = update(before, new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Nuevo"));
        assertEquals("Nuevo", after.caso().titulo());
        assertEquals(before.caso().id(), after.caso().id());
        assertEquals(before.caso().docenteId(), after.caso().docenteId());
        assertEquals(before.caso().historia(), after.caso().historia());
        assertEquals(before.caso().desarrolloSituacional(), after.caso().desarrolloSituacional());
        assertEquals(before.caso().escenarios(), after.caso().escenarios());
        assertEquals(before.caso().rubrica(), after.caso().rubrica());
        assertEquals(before.caso().fechaCreacion(), after.caso().fechaCreacion());
        assertEquals(before.caso().fechaActualizacion(), after.caso().fechaActualizacion());
        assertNotEquals(before.fingerprint(), after.fingerprint());
        assertEquals(other, read(other));
        assertEquals(after, read(after));
    }

    @Test
    void explicitNullAndNestedCommandsPreserveSiblingsAndPhysicalOrderGaps() {
        var before = saved();
        UUID scenarioId = UUID.fromString(before.caso().escenarios().getFirst().id());
        UUID questionId = UUID.fromString(before.caso().escenarios().getFirst().preguntas().getFirst().id());
        var after = update(before, new ActualizarDatosCasoCommand(CampoCaso.HISTORIA, null),
                new ActualizarEscenarioCommand(scenarioId, CampoEscenario.CONTEXTO, null),
                new ActualizarPreguntaCommand(questionId, CampoPregunta.TEXTO_AYUDA, null));
        assertNull(after.caso().historia());
        var scenario = after.caso().escenarios().getFirst();
        assertNull(scenario.contexto());
        assertNull(scenario.preguntas().getFirst().textoAyuda());
        assertEquals(List.of(2, 19), after.caso().escenarios().stream().map(Escenario::orden).toList());
        assertEquals(List.of(4, 13), scenario.preguntas().getFirst().opciones().stream().map(OpcionRespuesta::orden).toList());
        assertEquals(before.caso().escenarios().getLast(), after.caso().escenarios().getLast());
        assertEquals(before.caso().rubrica(), after.caso().rubrica());
    }

    @Test
    void supplementaryUnicodeLimitsRoundTripThroughCommandsPostgreSqlAndDomain() {
        var before = saved();
        String name = "😀".repeat(150);
        String title = "😀".repeat(200);
        UUID scenarioId = UUID.fromString(before.caso().escenarios().getFirst().id());
        var after = update(before, new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, name),
                new ActualizarEscenarioCommand(scenarioId, CampoEscenario.TITULO, title));
        assertEquals(name, after.caso().titulo());
        assertEquals(title, after.caso().escenarios().getFirst().titulo());
        assertEquals(after, read(after), "The JPA read and transactional reconstruction must agree");
        assertEquals(Integer.valueOf(150), jdbc.queryForObject(
                "SELECT char_length(\"nombre\") FROM public.\"Caso\" WHERE \"idCaso\" = ?", Integer.class,
                UUID.fromString(before.caso().id())));
        assertEquals(Integer.valueOf(200), jdbc.queryForObject(
                "SELECT char_length(\"titulo\") FROM public.\"Escenario\" WHERE \"idEscenario\" = ?", Integer.class, scenarioId));
        assertEquals(before.caso().rubrica(), after.caso().rubrica());
        assertEquals(before.caso().fechaCreacion(), after.caso().fechaCreacion());
        assertEquals(before.caso().fechaActualizacion(), after.caso().fechaActualizacion());
        assertThrows(IllegalArgumentException.class,
                () -> new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, name + "😀"));
        assertThrows(IllegalArgumentException.class,
                () -> new ActualizarEscenarioCommand(scenarioId, CampoEscenario.TITULO, title + "😀"));
        assertEquals(after, read(after), "Rejected overlong commands must not mutate stored content");
    }

    @Test
    void unchangedAssignmentAndEmptyCommandsDoNotIssueRowUpdates() {
        var before = saved();
        long xmin = rootXmin(before);
        assertEquals(before, update(before));
        assertEquals(before, update(before, new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, before.caso().titulo())));
        assertEquals(xmin, rootXmin(before), "A no-op must not create a new row version");
    }

    @Test
    void deletesOnlyExplicitOptionAndPreservesItsQuestionAndRubricReferences() {
        var before = saved();
        var question = before.caso().escenarios().getFirst().preguntas().getFirst();
        var option = question.opciones().getLast(); // Incorrect option: no new grading/completeness policy.
        var after = update(before, new EliminarOpcionCommand(UUID.fromString(option.id())));
        var copiedQuestion = after.caso().escenarios().getFirst().preguntas().getFirst();
        assertEquals(question.id(), copiedQuestion.id());
        assertEquals(List.of(question.opciones().getFirst()), copiedQuestion.opciones());
        assertEquals(before.caso().rubrica(), after.caso().rubrica());
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT count(*) FROM public.\"OpcionRespuesta\" WHERE \"idOpcion\" = ?",
                Integer.class, UUID.fromString(option.id())));
    }

    @Test
    void rejectsUnknownCasesUnknownElementsAndForeignElementsBeforeMutation() {
        var before = saved();
        var other = saved();
        assertCode(CasoCommandUpdateAdapter.CASE_NOT_FOUND, () -> updates.update(UUID.randomUUID(), before.fingerprint(), List.of()));
        assertCode(CasoCommandUpdateAdapter.ELEMENT_NOT_IN_CASE, () -> update(before,
                new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must roll back"),
                new ActualizarPreguntaCommand(UUID.randomUUID(), CampoPregunta.ENUNCIADO, "Unknown")));
        assertCode(CasoCommandUpdateAdapter.ELEMENT_NOT_IN_CASE, () -> update(before,
                new EliminarOpcionCommand(UUID.fromString(other.caso().escenarios().getFirst().preguntas().getFirst().opciones().getLast().id()))));
        assertEquals(before, read(before));
        assertEquals(other, read(other));
    }

    @Test
    void rejectsStalePreconditionWithoutSilentlyRestoringOldValues() {
        var before = saved();
        var current = update(before, new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "User A"));
        assertCode(CasoCommandUpdateAdapter.STALE_CASE, () -> update(before,
                new ActualizarDatosCasoCommand(CampoCaso.HISTORIA, "User B")));
        assertEquals(current, read(before));
    }

    @Test
    void semanticJsonCanonicalizationAgreesWithPostgreSql16JsonbEquality() {
        String high = "12345678901234567890.12345678901234567890123456789";
        String[][] pairs = {
                {"1", "1.0"}, {"1.0", "1.00"}, {"1e2", "100.000"}, {"0", "-0.000"},
                {"{\"b\":2,\"a\":1}", "{\"a\":1.00,\"b\":2.0}"},
                {"{\"x\":{\"b\":2,\"a\":[1,null,true]}}", "{\"x\":{\"a\":[1.0,null,true],\"b\":2.00}}"},
                {"{\"x\":false,\"x\":1}", "{\"x\":1.0}"}, {high, high + "000"},
                {"[1,2]", "[2,1]"}, {"1", "\"1\""}, {"null", "{}"}, {"{}", "{\"x\":null}"},
                {high, "12345678901234567890.12345678901234567890123456788"},
                {"\"😀\"", "\"\\uD83D\\uDE00\""}, {"true", "\"true\""}
        };
        var seed = saved();
        for (String[] pair : pairs) {
            Boolean equivalent = jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)",
                    Boolean.class, pair[0], pair[1]);
            assertNotNull(equivalent);
            replaceJsonFixture(seed, pair[0]);
            var first = read(seed);
            replaceJsonFixture(seed, pair[1]);
            var second = read(seed);
            assertEquals(equivalent.booleanValue(), JsonbCanonicalizer.canonicalize(pair[0]).equals(JsonbCanonicalizer.canonicalize(pair[1])));
            assertEquals(equivalent.booleanValue(), first.fingerprint().equals(second.fingerprint()));
            assertEquals(equivalent.booleanValue(), JsonbCanonicalizer.canonicalize(first.caso().rubrica().definicionEvaluacion())
                    .equals(JsonbCanonicalizer.canonicalize(second.caso().rubrica().definicionEvaluacion())));
        }
        replaceJsonFixture(seed, null);
        var sqlNull = read(seed);
        replaceJsonFixture(seed, "null");
        var jsonNull = read(seed);
        assertNull(sqlNull.caso().rubrica().definicionEvaluacion());
        assertEquals("null", jsonNull.caso().rubrica().definicionEvaluacion());
        assertNotEquals(sqlNull.fingerprint(), jsonNull.fingerprint());
    }

    @Test
    void v2PreconditionAcceptsEquivalentJsonRewritesAndRejectsOldVersionsWithoutMutation() {
        var seed = saved();
        replaceJsonFixture(seed, "{\"n\":1.0}");
        var before = read(seed);
        replaceJsonFixture(seed, "{\"n\":1.00}");
        assertTrue(before.fingerprint().startsWith("m3-v2:"));
        var after = update(before, new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Actualizado con v2"));
        assertEquals("Actualizado con v2", after.caso().titulo());
        assertNotEquals(before.fingerprint(), after.fingerprint());
        for (String prefix : List.of("m3-v1:", "m3-v99:")) {
            assertCode(CasoCommandUpdateAdapter.STALE_CASE, () -> updates.update(UUID.fromString(after.caso().id()),
                    prefix + after.fingerprint().substring("m3-v2:".length()),
                    List.of(new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must not write"))));
            assertEquals(after, read(after));
        }
    }

    private static void replaceJsonFixture(CasoUpdateResult source, String json) {
        // Isolated unlocked fixture mutation; the official V007 trigger remains enabled.
        jdbc.update("UPDATE public.\"Rubrica\" SET \"definicionEvaluacion\" = CAST(? AS jsonb) WHERE \"idRubrica\" = ?",
                json, UUID.fromString(source.caso().rubrica().id()));
    }

    @Test
    void sqlFailureRollsBackEarlierCommandsAndLeavesOtherCasesUntouched() {
        var before = saved();
        var other = saved();
        UUID second = UUID.fromString(before.caso().escenarios().getLast().id());
        RuntimeException failure = assertThrows(RuntimeException.class, () -> update(before,
                new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must roll back"),
                new ActualizarEscenarioCommand(second, CampoEscenario.ORDEN, 2)));
        assertTrue(hasSqlState(failure, "23505"));
        assertEquals(before, read(before));
        assertEquals(other, read(other));
    }

    @Test
    void twoConcurrentUsersWithSamePreconditionHaveExactlyOneWinner() throws Exception {
        var before = saved();
        try (var locker = datasource.getConnection(); ExecutorService executor = Executors.newFixedThreadPool(2)) {
            lock(locker, before);
            var started = new CountDownLatch(2);
            Future<CasoUpdateResult> first = executor.submit(() -> { started.countDown(); return update(before,
                    new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "User A")); });
            Future<CasoUpdateResult> second = executor.submit(() -> { started.countDown(); return update(before,
                    new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "User B")); });
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                awaitBlockedWriters(2);
            } finally { locker.commit(); }
            int winners = 0;
            int conflicts = 0;
            for (var future : List.of(first, second)) {
                try { future.get(15, TimeUnit.SECONDS); winners++; }
                catch (ExecutionException failure) {
                    assertEquals(CasoCommandUpdateAdapter.STALE_CASE,
                            assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
                    conflicts++;
                }
            }
            assertEquals(1, winners);
            assertEquals(1, conflicts);
            assertTrue(Set.of("User A", "User B").contains(read(before).caso().titulo()));
        }
    }

    @Test
    void concurrentQuestionAndRubricChangesInvalidateTheWholeAggregatePrecondition() throws Exception {
        for (boolean rubric : List.of(false, true)) {
            var before = saved();
            try (var external = datasource.getConnection(); ExecutorService executor = Executors.newSingleThreadExecutor()) {
                lock(external, before); // The external writer follows the documented root-first protocol.
                String sql = rubric ? "UPDATE public.\"Rubrica\" SET \"descripcion\" = ? WHERE \"idRubrica\" = ?"
                        : "UPDATE public.\"Pregunta\" SET \"enunciado\" = ? WHERE \"idPregunta\" = ?";
                UUID target = UUID.fromString(rubric ? before.caso().rubrica().id()
                        : before.caso().escenarios().getFirst().preguntas().getFirst().id());
                try (var statement = external.prepareStatement(sql)) {
                    statement.setString(1, "Concurrent child value"); statement.setObject(2, target); statement.executeUpdate();
                }
                Future<CasoUpdateResult> pending = executor.submit(() -> update(before,
                        new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must not overwrite")));
                try { awaitBlockedWriters(1); } finally { external.commit(); }
                ExecutionException failure = assertThrows(ExecutionException.class, () -> pending.get(15, TimeUnit.SECONDS));
                assertEquals(CasoCommandUpdateAdapter.STALE_CASE,
                        assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
                var current = read(before);
                assertEquals(before.caso().titulo(), current.caso().titulo());
                assertEquals("Concurrent child value", rubric ? current.caso().rubrica().descripcion()
                        : current.caso().escenarios().getFirst().preguntas().getFirst().enunciado());
            }
        }
    }

    @Test
    void concurrentProgrammingInvalidatesPreconditionThenBlocksCommandsWithCurrentFingerprint() throws Exception {
        var before = saved();
        try (var session = datasource.getConnection(); ExecutorService executor = Executors.newSingleThreadExecutor()) {
            session.setAutoCommit(false);
            schedule(session, before); // Official V007/V009 obtain and retain the Caso lock.
            Future<CasoUpdateResult> pending = executor.submit(() -> update(before,
                    new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must not overwrite")));
            try { awaitBlockedWriters(1); } finally { session.commit(); }
            ExecutionException failure = assertThrows(ExecutionException.class, () -> pending.get(15, TimeUnit.SECONDS));
            assertEquals(CasoCommandUpdateAdapter.STALE_CASE,
                    assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
        }
        var locked = read(before);
        assertTrue(locked.caso().contenidoEvaluableBloqueado());
        assertNotNull(locked.caso().fechaBloqueoAcademico());
        assertCode(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, () -> update(locked,
                new ActualizarPreguntaCommand(UUID.fromString(locked.caso().escenarios().getFirst().preguntas().getFirst().id()),
                        CampoPregunta.ENUNCIADO, "Blocked")));
        assertCode(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, () -> update(locked,
                new EliminarOpcionCommand(UUID.fromString(locked.caso().escenarios().getFirst().preguntas().getFirst().opciones().getLast().id()))));
        assertEquals(locked, read(locked));
        // The referenced-option FK remains validated, restrictive and active. A valid
        // Respuesta requires a session; F3-03 wins before a referenced-option DELETE.
        assertEquals("r", jdbc.queryForObject("""
                SELECT confdeltype::text FROM pg_constraint
                WHERE conname = 'fk_respuesta_idopcionseleccionada_idpregunta' AND convalidated
                """, String.class));
    }

    private static void awaitBlockedWriters(int minimum) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND pid <> pg_backend_pid()
                      AND cardinality(pg_blocking_pids(pid)) > 0 AND query LIKE '%FOR UPDATE%'
                    """, Integer.class);
            if (count != null && count >= minimum) { return; }
            Thread.onSpinWait(); // Poll an observed database condition; no timing-based interleaving.
        }
        fail("Expected independent PostgreSQL writer connections blocked on Caso");
    }

    private static void lock(Connection connection, CasoUpdateResult source) throws SQLException {
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement("SELECT \"idCaso\" FROM public.\"Caso\" WHERE \"idCaso\" = ? FOR UPDATE")) {
            statement.setObject(1, UUID.fromString(source.caso().id()));
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
        }
    }

    private static void schedule(Connection connection, CasoUpdateResult source) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO public."Sesion" ("idSesion", "idCaso", "idDocenteProgramador", "nombreEspacioAcademico",
                    "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos") VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, UUID.fromString(source.caso().id()));
            statement.setObject(3, teacher); statement.setString(4, "Fixture 10B");
            statement.setTimestamp(5, Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")));
            statement.setTimestamp(6, Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")));
            statement.setInt(7, 300); statement.executeUpdate();
        }
    }

    private static CasoUpdateResult saved() {
        String caseId = UUID.randomUUID().toString();
        String questionId = UUID.randomUUID().toString();
        String rubricId = UUID.randomUUID().toString();
        var question = new Pregunta(questionId, "Pregunta", List.of(
                new OpcionRespuesta(UUID.randomUUID().toString(), "Correcta", true, "Explicación", 4),
                new OpcionRespuesta(UUID.randomUUID().toString(), "Incorrecta", false, null, 13)),
                "Ayuda", "Lista", true, 3, CREATED, null);
        var rubric = new Rubrica(rubricId, List.of(), caseId, "Rubrica", "Descripción", new BigDecimal("12.50"),
                "{\"opaco\":[true,null,12.50]}", CREATED, Set.of(new RubricaPregunta(UUID.randomUUID().toString(),
                        rubricId, questionId, new BigDecimal("12.50"), null)));
        var caso = new Caso(caseId, "Original", teacher.toString(), 300, CasoEstado.PUBLICADO,
                List.of(new Escenario(UUID.randomUUID().toString(), "Título", "Contexto", null, 2, true, List.of(question)),
                        new Escenario(UUID.randomUUID().toString(), null, null, null, 19, false, List.of())),
                rubric, false, "Historia", "Desarrollo", true, CREATED, null, null);
        inserts.insert(caso);
        return updates.findById(UUID.fromString(caseId)).orElseThrow();
    }

    private static CasoUpdateResult update(CasoUpdateResult before, CasoUpdateCommand... commands) {
        return updates.update(UUID.fromString(before.caso().id()), before.fingerprint(), List.of(commands));
    }

    private static CasoUpdateResult read(CasoUpdateResult before) {
        return updates.findById(UUID.fromString(before.caso().id())).orElseThrow();
    }

    private static long rootXmin(CasoUpdateResult before) {
        return jdbc.queryForObject("SELECT xmin::text::bigint FROM public.\"Caso\" WHERE \"idCaso\" = ?",
                Long.class, UUID.fromString(before.caso().id())); // Test observation only; not the concurrency token.
    }

    private static void assertCode(String expected, Runnable action) {
        assertEquals(expected, assertThrows(CasoDomainException.class, action::run).code());
    }

    private static boolean hasSqlState(Throwable failure, String state) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) { return true; }
        }
        return false;
    }

    private static Path migrationDirectory() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path directory : List.of(cwd.resolve("app/src/main/resources/db/migration"),
                cwd.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (MIGRATIONS.stream().allMatch(name -> Files.isRegularFile(directory.resolve(name)))) { return directory; }
        }
        throw new IllegalStateException("Official V001-V009 not found");
    }
}
