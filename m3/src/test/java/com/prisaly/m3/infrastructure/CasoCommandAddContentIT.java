package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m3.application.update.CasoUpdateCommand.*;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import com.prisaly.m3.infrastructure.persistence.jpa.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** M3-INC-10D: official schema/triggers, no test-only database changes. */
@Timeout(60)
class CasoCommandAddContentIT {
    private static PostgreSQLContainer postgres;
    private static DriverManagerDataSource datasource;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static JdbcTemplate jdbc;
    private static CasoPersistenceAdapter inserts;
    private static CasoCommandUpdateAdapter updates;
    private static UUID teacher;
    private static volatile Gate gate;
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql", "V003__bd0052_integridad.sql",
            "V004__bd0052_integridad_intentos.sql", "V005__bd0052_cierre_integridad.sql",
            "V006__bd0052_proteccion_relaciones.sql", "V007__fase3_politicas_aprobadas.sql",
            "V008__bloqueo_caso_al_reasignar_sesion.sql", "V009__rf09_solo_casos_publicados.sql");

    @BeforeAll
    static void prepare() throws Exception {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path migrations = List.of(cwd.resolve("app/src/main/resources/db/migration"),
                cwd.resolve("../app/src/main/resources/db/migration").normalize()).stream()
                .filter(p -> MIGRATIONS.stream().allMatch(n -> Files.isRegularFile(p.resolve(n))))
                .findFirst().orElseThrow(() -> new IllegalStateException("Official V001-V009 not found"));
        postgres = new PostgreSQLContainer("postgres:16.15"); postgres.start();
        datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Properties properties = new Properties();
        properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "12");
        properties.setProperty("options", "-c statement_timeout=10000");
        datasource.setConnectionProperties(properties);
        try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
            for (String name : MIGRATIONS) { statement.execute(Files.readString(migrations.resolve(name), StandardCharsets.UTF_8)); }
        }
        jdbc = new JdbcTemplate(datasource);
        Integer role = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "DocenteAddM3", "Fixture 10D");
        teacher = UUID.randomUUID();
        jdbc.update("INSERT INTO public.\"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                teacher, role, "Fixture 10D", "add.m3@prisaly.test");
        var traced = new AbstractDataSource() {
            public Connection getConnection() throws SQLException { return trace(datasource.getConnection()); }
            public Connection getConnection(String user, String password) throws SQLException { return getConnection(); }
        };
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(traced); factory.setPackagesToScan(CasoJpaAggregateReader.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.globally_quoted_identifiers", true));
        factory.afterPropertiesSet();
        var emf = factory.getObject(); assertNotNull(emf);
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
    void addsQuestionWithExplicitAttributesAndOfficialDateWithoutChangingPriorContent() {
        var before = saved(CasoEstado.BORRADOR); var other = saved(CasoEstado.BORRADOR);
        UUID scenario = UUID.fromString(before.caso().escenarios().getFirst().id());
        UUID questionId = UUID.randomUUID();
        var after = add(before, new AgregarPreguntaCommand(questionId, scenario,
                " New question \uD83D\uDE00 ", " Help\n ", "Borrador", false, 19));
        var question = after.caso().escenarios().getFirst().preguntas().getLast();
        assertEquals(questionId.toString(), question.id()); assertEquals(" New question \uD83D\uDE00 ", question.enunciado());
        assertEquals(" Help\n ", question.textoAyuda()); assertEquals("Borrador", question.estadoEdicion());
        assertFalse(question.visible()); assertEquals(Integer.valueOf(19), question.orden());
        assertNotNull(question.fechaCreacion()); assertEquals(0, question.fechaCreacion().getNano() % 1_000);
        assertNull(question.fechaActualizacion()); assertTrue(question.opciones().isEmpty());
        assertEquals(scenario, jdbc.queryForObject("SELECT \"idEscenario\" FROM public.\"Pregunta\" WHERE \"idPregunta\"=?", UUID.class, questionId));
        assertEquals(jdbc.queryForObject("SELECT \"fechaCreacion\" FROM public.\"Pregunta\" WHERE \"idPregunta\"=?",
                java.sql.Timestamp.class, questionId).toInstant(), question.fechaCreacion());
        assertEquals(before.caso(), withoutQuestion(after.caso(), questionId));
        assertTrue(after.fingerprint().startsWith("m3-v2:")); assertNotEquals(before.fingerprint(), after.fingerprint());
        assertEquals(after, read(before)); assertEquals(other, read(other));
        var next = add(after, new AgregarPreguntaCommand(UUID.randomUUID(), scenario, "Another", null, "Lista", true, 31));
        var last = next.caso().escenarios().getFirst().preguntas().getLast();
        assertEquals("Lista", last.estadoEdicion()); assertTrue(last.visible()); assertNull(last.textoAyuda());
        assertTrue(last.opciones().isEmpty());
        assertEquals(List.of(3, 19, 31), next.caso().escenarios().getFirst().preguntas().stream().map(Pregunta::orden).toList());
        // A draft may be incomplete, but the existing publication rule remains unchanged.
        assertCode(CasoValidation.CODE_INVALID_QUESTION, () -> CasoValidation.assertValidSingleChoiceQuestion(last));
    }

    @Test
    void questionAddRejectsMissingForeignParentsStatesAndStalePreconditionWithoutWriting() {
        var draft = saved(CasoEstado.BORRADOR); var other = saved(CasoEstado.BORRADOR);
        assertCode(CasoCommandUpdateAdapter.CASE_NOT_FOUND, () -> updates.update(UUID.randomUUID(), draft.fingerprint(),
                List.of(questionCommand(draft, 19))));
        for (UUID parent : List.of(UUID.randomUUID(), UUID.fromString(other.caso().escenarios().getFirst().id()))) {
            assertCode(CasoCommandUpdateAdapter.ELEMENT_NOT_IN_CASE,
                    () -> add(draft, new AgregarPreguntaCommand(UUID.randomUUID(), parent, "Question", null, "Borrador", true, 19)));
        }
        for (CasoEstado state : List.of(CasoEstado.PUBLICADO, CasoEstado.ARCHIVADO)) {
            var source = saved(state);
            assertCode(state == CasoEstado.PUBLICADO ? CasoCommandUpdateAdapter.QUESTION_ADD_REQUIRES_DRAFT
                    : CasoValidation.CODE_ARCHIVED_CASE_OPERATION_PENDING, () -> add(source, questionCommand(source, 19)));
            assertEquals(source, read(source));
        }
        assertEquals(draft, read(draft)); assertEquals(other, read(other));
        var changed = add(draft, questionCommand(draft, 19));
        assertCode(CasoCommandUpdateAdapter.STALE_CASE, () -> add(draft, questionCommand(draft, 31)));
        assertEquals(changed, read(draft));
    }

    @Test
    void questionDuplicatesAndCompositeConstraintFailureRollBackEveryCommand() {
        var source = saved(CasoEstado.BORRADOR); var other = saved(CasoEstado.BORRADOR);
        UUID parent = UUID.fromString(source.caso().escenarios().getFirst().id());
        for (UUID id : List.of(UUID.fromString(source.caso().escenarios().getFirst().preguntas().getFirst().id()),
                UUID.fromString(other.caso().escenarios().getFirst().preguntas().getFirst().id()))) {
            assertCode(CasoCommandUpdateAdapter.QUESTION_ID_IN_USE,
                    () -> add(source, new AgregarPreguntaCommand(id, parent, "Question", null, "Borrador", true, 19)));
        }
        assertCode(CasoCommandUpdateAdapter.QUESTION_ORDER_IN_USE, () -> add(source, questionCommand(source, 3)));
        RuntimeException failure = assertThrows(RuntimeException.class, () -> updates.update(UUID.fromString(source.caso().id()),
                source.fingerprint(), List.of(new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must roll back"),
                        questionCommand(source, 19), questionCommand(source, 19))));
        assertTrue(hasSqlState(failure, "23505"));
        assertTrue(hasConstraint(failure, "uq_pregunta_idx1"));
        assertEquals(source, read(source)); assertEquals(other, read(other));
        UUID duplicate = UUID.randomUUID();
        assertCode(CasoCommandUpdateAdapter.QUESTION_ID_IN_USE, () -> updates.update(UUID.fromString(source.caso().id()),
                source.fingerprint(), List.of(new AgregarPreguntaCommand(duplicate, parent, "First", null, "Borrador", true, 19),
                        new AgregarPreguntaCommand(duplicate, parent, "Second", null, "Borrador", false, 31))));
        assertEquals(source, read(source));
    }

    @Test
    void questionAddCannotDependOnNewParentOrBeEditedWithinSameBatch() {
        var source = saved(CasoEstado.BORRADOR); UUID parent = UUID.randomUUID(), question = UUID.randomUUID();
        assertCode(CasoCommandUpdateAdapter.ELEMENT_NOT_IN_CASE, () -> updates.update(UUID.fromString(source.caso().id()),
                source.fingerprint(), List.of(new AgregarEscenarioCommand(parent, null, null, null, 19),
                        new AgregarPreguntaCommand(question, parent, "Question", null, "Borrador", true, 19))));
        var add = questionCommand(source, 19);
        assertCode(CasoCommandUpdateAdapter.ELEMENT_NOT_IN_CASE, () -> updates.update(UUID.fromString(source.caso().id()),
                source.fingerprint(), List.of(add, new ActualizarPreguntaCommand(add.idPregunta(), CampoPregunta.ENUNCIADO, "Dependent"))));
        assertEquals(source, read(source));
    }

    @Test
    void officialProgrammingRejectsQuestionAddAndSqlInsertWithoutManualLockChanges() throws Exception {
        var source = saved(CasoEstado.PUBLICADO);
        try (var c = datasource.getConnection()) { schedule(c, source); }
        var locked = read(source); assertNotNull(locked.caso().fechaBloqueoAcademico());
        assertCode(CasoCommandUpdateAdapter.STALE_CASE, () -> add(source, questionCommand(source, 19)));
        assertCode(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, () -> add(locked, questionCommand(locked, 19)));
        var failure = assertThrows(RuntimeException.class, () -> jdbc.update(
                "INSERT INTO public.\"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"orden\") VALUES (?,?,?,?)",
                UUID.randomUUID(), UUID.fromString(source.caso().escenarios().getFirst().id()), "Blocked", 19));
        assertTrue(hasSqlState(failure, "23514")); assertTrue(hasConstraint(failure, "tr_contenido_bloqueo_f303"));
        assertEquals(locked, read(source));
    }

    @Test
    void officialQuestionFkAndOrderConstraintsRemainActive() {
        var source = saved(CasoEstado.BORRADOR);
        var missing = assertThrows(RuntimeException.class, () -> jdbc.update(
                "INSERT INTO public.\"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"orden\") VALUES (?,?,?,?)",
                UUID.randomUUID(), UUID.randomUUID(), "Rejected FK fixture", 19));
        assertTrue(hasSqlState(missing, "23503")); assertTrue(hasConstraint(missing, "fk_pregunta_idescenario"));
        var order = assertThrows(RuntimeException.class, () -> jdbc.update(
                "INSERT INTO public.\"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"orden\") VALUES (?,?,?,?)",
                UUID.randomUUID(), UUID.fromString(source.caso().escenarios().getFirst().id()), "Rejected order fixture", 0));
        assertTrue(hasSqlState(order, "23514")); assertTrue(hasConstraint(order, "ck_pregunta_orden"));
        assertEquals(source, read(source));
    }

    @Test
    void concurrentQuestionAddsWithSameFingerprintHaveOneWinner() throws Exception {
        var source = saved(CasoEstado.BORRADOR);
        try (var workers = new Workers(); var pause = new Gate()) {
            gate = pause;
            Future<CasoUpdateResult> first = workers.submit(() -> add(source, questionCommand(source, 19)));
            assertTrue(pause.reached.await(5, TimeUnit.SECONDS));
            AtomicInteger secondPid = new AtomicInteger(); CountDownLatch ready = new CountDownLatch(1);
            Future<CasoUpdateResult> second = workers.submit(() -> {
                writerPid.set(new PidSignal(secondPid, ready));
                try { return add(source, questionCommand(source, 31)); } finally { writerPid.remove(); }
            });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS)); awaitBlocked(secondPid.get(), pause.pid.get());
            } finally { pause.close(); gate = null; }
            var committed = first.get(15, TimeUnit.SECONDS);
            var failure = assertThrows(ExecutionException.class, () -> second.get(15, TimeUnit.SECONDS));
            assertEquals(CasoCommandUpdateAdapter.STALE_CASE, assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
            assertEquals(committed, read(source));
            assertEquals(2, committed.caso().escenarios().getFirst().preguntas().size());
        } finally { gate = null; }
    }

    private static Caso withoutQuestion(Caso c, UUID question) {
        var scenarios = c.escenarios().stream().map(e -> new Escenario(e.id(), e.descripcion(),
                e.preguntas().stream().filter(p -> !p.id().equals(question.toString())).toList(),
                e.titulo(), e.contexto(), e.instrucciones(), e.orden(), e.activo())).toList();
        return new Caso(c.id(), c.titulo(), c.docenteId(), c.tiempoEstimadoSegundos(), c.estado(), scenarios,
                c.rubrica(), c.contenidoEvaluableBloqueado(), c.historia(), c.desarrolloSituacional(), c.activo(),
                c.fechaCreacion(), c.fechaActualizacion(), c.fechaBloqueoAcademico());
    }

    private static AgregarPreguntaCommand questionCommand(CasoUpdateResult source, int order) {
        return new AgregarPreguntaCommand(UUID.randomUUID(), UUID.fromString(source.caso().escenarios().getFirst().id()),
                "Question fixture", null, "Borrador", true, order);
    }

    private static CasoUpdateResult add(CasoUpdateResult before, AgregarPreguntaCommand command) {
        return updates.update(UUID.fromString(before.caso().id()), before.fingerprint(), List.of(command));
    }

    @Test
    void addsEmptyActiveScenarioPreservingAllPriorContentAndOtherCase() {
        var before = saved(CasoEstado.BORRADOR);
        var other = saved(CasoEstado.BORRADOR);
        UUID id = UUID.randomUUID(); String title = "\uD83D\uDE00".repeat(200);
        var after = add(before, new AgregarEscenarioCommand(id, title, "Context", "Instructions", 19));
        assertEquals(before.caso(), withoutScenario(after.caso(), id));
        var scenario = after.caso().escenarios().getLast();
        assertEquals(id.toString(), scenario.id()); assertEquals(title, scenario.titulo());
        assertEquals("Context", scenario.contexto()); assertEquals("Instructions", scenario.instrucciones());
        assertEquals(Integer.valueOf(19), scenario.orden()); assertTrue(scenario.activo());
        assertTrue(scenario.preguntas().isEmpty()); assertNull(scenario.descripcion());
        assertNotEquals(before.fingerprint(), after.fingerprint());
        assertEquals(after, read(before)); assertEquals(other, read(other));
        var nullable = add(after, new AgregarEscenarioCommand(UUID.randomUUID(), null, null, null, 31));
        var last = nullable.caso().escenarios().getLast();
        assertNull(last.titulo()); assertNull(last.contexto()); assertNull(last.instrucciones());
        assertTrue(last.activo()); assertTrue(last.preguntas().isEmpty());
    }

    @Test
    void rejectsMissingCasePublishedArchivedAndStalePreconditionWithoutMutation() {
        var draft = saved(CasoEstado.BORRADOR);
        assertCode(CasoCommandUpdateAdapter.CASE_NOT_FOUND, () -> updates.update(UUID.randomUUID(), draft.fingerprint(),
                List.of(command(19))));
        for (CasoEstado state : List.of(CasoEstado.PUBLICADO, CasoEstado.ARCHIVADO)) {
            var before = saved(state);
            assertCode(state == CasoEstado.PUBLICADO ? CasoCommandUpdateAdapter.SCENARIO_ADD_REQUIRES_DRAFT
                    : CasoValidation.CODE_ARCHIVED_CASE_OPERATION_PENDING, () -> add(before, command(19)));
            assertEquals(before, read(before));
        }
        var changed = add(draft, command(19));
        assertCode(CasoCommandUpdateAdapter.STALE_CASE, () -> add(draft, command(31)));
        assertEquals(changed, read(draft));
    }

    @Test
    void rejectsDuplicateUuidAndOrderAndRollsBackCompositeSqlFailure() {
        var before = saved(CasoEstado.BORRADOR); var other = saved(CasoEstado.BORRADOR);
        for (UUID id : List.of(UUID.fromString(before.caso().escenarios().getFirst().id()),
                UUID.fromString(other.caso().escenarios().getFirst().id()))) {
            assertCode(CasoCommandUpdateAdapter.SCENARIO_ID_IN_USE,
                    () -> add(before, new AgregarEscenarioCommand(id, null, null, null, 19)));
        }
        assertCode(CasoCommandUpdateAdapter.SCENARIO_ORDER_IN_USE, () -> add(before, command(2)));
        // Neither order exists initially: second INSERT fails UNIQUE after the first INSERT and root UPDATE.
        RuntimeException failure = assertThrows(RuntimeException.class, () -> updates.update(
                UUID.fromString(before.caso().id()), before.fingerprint(), List.of(
                        new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must roll back"), command(19), command(19))));
        assertTrue(hasSqlState(failure, "23505"));
        assertEquals(before, read(before)); assertEquals(other, read(other));
        UUID duplicateInBatch = UUID.randomUUID();
        assertCode(CasoCommandUpdateAdapter.SCENARIO_ID_IN_USE, () -> updates.update(UUID.fromString(before.caso().id()),
                before.fingerprint(), List.of(new AgregarEscenarioCommand(duplicateInBatch, null, null, null, 19),
                        new AgregarEscenarioCommand(duplicateInBatch, null, null, null, 31))));
        assertEquals(before, read(before));
    }

    @Test
    void rejectsDependentEditOfNewScenarioBeforeAnyMutation() {
        var before = saved(CasoEstado.BORRADOR); UUID id = UUID.randomUUID();
        assertCode(CasoCommandUpdateAdapter.ELEMENT_NOT_IN_CASE, () -> updates.update(UUID.fromString(before.caso().id()),
                before.fingerprint(), List.of(new AgregarEscenarioCommand(id, null, null, null, 19),
                        new ActualizarEscenarioCommand(id, CampoEscenario.TITULO, "Dependent"))));
        assertEquals(before, read(before));
    }

    @Test
    void twoAddsWithSameFingerprintHaveExactlyOneWinner() throws Exception {
        var before = saved(CasoEstado.BORRADOR);
        try (var workers = new Workers(); var pause = new Gate()) {
            gate = pause;
            Future<CasoUpdateResult> first = workers.submit(() -> add(before, command(19)));
            assertTrue(pause.reached.await(5, TimeUnit.SECONDS));
            AtomicInteger secondPid = new AtomicInteger(); CountDownLatch ready = new CountDownLatch(1);
            Future<CasoUpdateResult> second = workers.submit(() -> {
                // Observe the next writer using its own bound connection via the tracing hook.
                writerPid.set(new PidSignal(secondPid, ready));
                try { return add(before, command(31)); } finally { writerPid.remove(); }
            });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS)); awaitBlocked(secondPid.get(), pause.pid.get());
            } finally { pause.close(); gate = null; }
            var committed = first.get(15, TimeUnit.SECONDS);
            var failure = assertThrows(ExecutionException.class, () -> second.get(15, TimeUnit.SECONDS));
            assertEquals(CasoCommandUpdateAdapter.STALE_CASE, assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
            assertEquals(committed, read(before)); assertEquals(2, committed.caso().escenarios().size());
        } finally { gate = null; }
    }

    @Test
    void programmingWaitsForAddingDraftThenV009RejectsItWithoutLockingDraft() throws Exception {
        var before = saved(CasoEstado.BORRADOR);
        try (var workers = new Workers(); var pause = new Gate()) {
            gate = pause;
            Future<CasoUpdateResult> added = workers.submit(() -> add(before, command(19)));
            assertTrue(pause.reached.await(5, TimeUnit.SECONDS));
            AtomicInteger programPid = new AtomicInteger(); CountDownLatch ready = new CountDownLatch(1);
            Future<Void> programmed = workers.submit(() -> {
                try (var connection = datasource.getConnection()) {
                    programPid.set(pid(connection)); ready.countDown(); schedule(connection, before);
                }
                return null;
            });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS)); awaitBlocked(programPid.get(), pause.pid.get());
            } finally { pause.close(); gate = null; }
            var committed = added.get(15, TimeUnit.SECONDS);
            var failure = assertThrows(ExecutionException.class, () -> programmed.get(15, TimeUnit.SECONDS));
            assertTrue(hasSqlState(failure, "23514"));
            assertTrue(hasConstraint(failure, "tr_sesion_rf09_caso_programable"));
            assertEquals(committed, read(before)); assertNull(committed.caso().fechaBloqueoAcademico());
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT count(*) FROM public.\"Sesion\" WHERE \"idCaso\"=?",
                    Integer.class, UUID.fromString(before.caso().id())));
        } finally { gate = null; }
    }

    @Test
    void officialProgrammingLocksPublishedCaseAndRejectsAddWithCurrentFingerprint() throws Exception {
        var before = saved(CasoEstado.PUBLICADO);
        try (var connection = datasource.getConnection()) { schedule(connection, before); }
        var locked = read(before); assertNotNull(locked.caso().fechaBloqueoAcademico());
        assertCode(CasoCommandUpdateAdapter.STALE_CASE, () -> add(before, command(19)));
        assertCode(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, () -> add(locked, command(19)));
        var rejected = assertThrows(RuntimeException.class, () -> jdbc.update(
                "INSERT INTO public.\"Escenario\" (\"idEscenario\",\"idCaso\",\"orden\") VALUES (?,?,?)",
                UUID.randomUUID(), UUID.fromString(before.caso().id()), 19));
        assertTrue(hasSqlState(rejected, "23514"));
        assertTrue(hasConstraint(rejected, "tr_contenido_bloqueo_f303"));
        assertEquals(locked, read(before));
    }

    private static Caso withoutScenario(Caso c, UUID id) {
        return new Caso(c.id(), c.titulo(), c.docenteId(), c.tiempoEstimadoSegundos(), c.estado(),
                c.escenarios().stream().filter(e -> !e.id().equals(id.toString())).toList(), c.rubrica(),
                c.contenidoEvaluableBloqueado(), c.historia(), c.desarrolloSituacional(), c.activo(),
                c.fechaCreacion(), c.fechaActualizacion(), c.fechaBloqueoAcademico());
    }

    private static CasoUpdateResult saved(CasoEstado state) {
        String id = UUID.randomUUID().toString();
        var question = new Pregunta(UUID.randomUUID().toString(), "Original question", List.of(
                new OpcionRespuesta(UUID.randomUUID().toString(), "Correct", true, "Original explanation", 4)),
                null, "Lista", false, 3, CREATED, null);
        String rubricId = UUID.randomUUID().toString();
        var rubric = new Rubrica(rubricId, List.of(), id, "Original rubric", "Description", new BigDecimal("12.50"),
                "{\"opaque\":[true,null,12.50]}", CREATED,
                Set.of(new RubricaPregunta(UUID.randomUUID().toString(), rubricId, question.id(), new BigDecimal("12.50"), null)));
        inserts.insert(new Caso(id, "Original", teacher.toString(), 300, state,
                List.of(new Escenario(UUID.randomUUID().toString(), "Original scenario", "Context", null, 2, false, List.of(question))),
                rubric, false, "History", "Development", true, CREATED, null, null));
        return updates.findById(UUID.fromString(id)).orElseThrow();
    }

    private static AgregarEscenarioCommand command(int order) {
        return new AgregarEscenarioCommand(UUID.randomUUID(), null, null, null, order);
    }
    private static CasoUpdateResult add(CasoUpdateResult before, AgregarEscenarioCommand command) {
        return updates.update(UUID.fromString(before.caso().id()), before.fingerprint(), List.of(command));
    }
    private static CasoUpdateResult read(CasoUpdateResult before) {
        return updates.findById(UUID.fromString(before.caso().id())).orElseThrow();
    }
    private static void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(CasoDomainException.class, action::run).code());
    }
    private static boolean hasSqlState(Throwable failure, String state) {
        for (Throwable c = failure; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql && state.equals(sql.getSQLState())) { return true; }
        }
        return false;
    }
    private static boolean hasConstraint(Throwable failure, String constraint) {
        for (Throwable c = failure; c != null; c = c.getCause()) {
            if (c instanceof org.postgresql.util.PSQLException sql && sql.getServerErrorMessage() != null
                    && constraint.equals(sql.getServerErrorMessage().getConstraint())) { return true; }
        }
        return false;
    }
    private static void schedule(Connection c, CasoUpdateResult before) throws SQLException {
        try (var s = c.prepareStatement("""
                INSERT INTO public."Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                    "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,?,clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 hours',300)
                """)) {
            s.setObject(1, UUID.randomUUID()); s.setObject(2, UUID.fromString(before.caso().id()));
            s.setObject(3, teacher); s.setString(4, "Fixture 10D"); s.executeUpdate();
        }
    }
    private static int pid(Connection c) throws SQLException {
        try (var s = c.createStatement(); var rows = s.executeQuery("SELECT pg_backend_pid()")) {
            assertTrue(rows.next()); return rows.getInt(1);
        }
    }
    private static void awaitBlocked(int waiter, int blocker) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blocker, waiter))) { return; }
            Thread.onSpinWait();
        }
        fail("Expected backend " + waiter + " blocked by " + blocker);
    }
    private record PidSignal(AtomicInteger pid, CountDownLatch ready) { }
    private static final ThreadLocal<PidSignal> writerPid = new ThreadLocal<>();
    private static Connection trace(Connection raw) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a) -> {
            try {
                Object result = m.invoke(raw,a);
                if (m.getName().equals("prepareStatement") && a != null && a[0] instanceof String sql) {
                    PreparedStatement statement = (PreparedStatement) result;
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (sp,sm,sa) -> {
                        if (sm.getName().equals("executeQuery") && sql.contains("FOR UPDATE")) {
                            PidSignal signal = writerPid.get();
                            if (signal != null) { signal.pid().set(pid(raw)); signal.ready().countDown(); }
                        }
                        Gate current = gate;
                        if (current != null && sm.getName().equals("executeUpdate")
                                && (sql.stripLeading().startsWith("INSERT INTO public.\"Escenario\"")
                                    || sql.stripLeading().startsWith("INSERT INTO public.\"Pregunta\""))) {
                            current.pid.set(pid(raw)); current.reached.countDown();
                            if (!current.release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Insert gate timed out"); }
                        }
                        try { return sm.invoke(statement,sa); } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
                }
                return result;
            } catch (InvocationTargetException e) { throw e.getCause(); }
        });
    }
    private static final class Gate implements AutoCloseable {
        final CountDownLatch reached = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger pid = new AtomicInteger();
        public void close() { release.countDown(); }
    }
    private static final class Workers implements AutoCloseable {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final List<Future<?>> futures = new ArrayList<>();
        <T> Future<T> submit(Callable<T> task) { Future<T> f = executor.submit(task); futures.add(f); return f; }
        public void close() throws InterruptedException {
            futures.forEach(f -> f.cancel(true)); executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }
}
