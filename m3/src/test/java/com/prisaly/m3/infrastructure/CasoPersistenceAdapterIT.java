package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoPublicationAdapter;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoCommandUpdateAdapter;
import com.prisaly.m3.domain.rule.CasoDomainException;
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
    /** HTTP composition with controlled policies, not the productive M1/APP assembly. */
    private static org.springframework.test.web.servlet.MockMvc controlledHttp() {
        var policy = new com.prisaly.m3.application.port.AutorizacionConsultaCasoPort() {
            public Decision verificarAcceso(UUID user, UUID caso) { return Decision.AUTORIZADO; }
            public Decision verificarVisibilidad(UUID user, UUID caso, CasoEstado state, boolean active) {
                return Decision.AUTORIZADO;
            }
        };
        var write = new CaseAuthorizationPort() {
            public boolean canCreateCase(String user, String owner) { return false; }
            public boolean canModifyCase(String user, String caso) { return teacher.toString().equals(user); }
            public boolean canPublishCase(String user, String caso) { return false; }
        };
        return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.prisaly.m3.presentation.CasoGestionController(() -> teacher, repository,
                        java.util.Optional.of(policy), java.util.Optional.of(write),
                        java.util.Optional.of(h -> h.getFirst("X-Test-Fingerprint"))))
                .setControllerAdvice(new com.prisaly.m3.presentation.CasoGestionExceptionHandler()).build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchHttp(
            UUID id, String fingerprint, String body) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/v1/casos/" + id)
                .contentType("application/json").header("X-Test-Fingerprint", fingerprint).content(body);
    }

    @Test
    void httpPatchCommitsBeforeResponseAndGetReturnsOnlySafeProjection() throws Exception {
        var before = savedReplacement();
        UUID id = UUID.fromString(before.caso().id());
        var mvc = controlledHttp();
        mvc.perform(patchHttp(id, before.fingerprint(), "{\"titulo\":\"Guardado por HTTP\",\"historia\":null}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        var after = updateReader().findById(id).orElseThrow();
        assertEquals("Guardado por HTTP", after.caso().titulo());
        assertNull(after.caso().historia());
        assertEquals(before.caso().docenteId(), after.caso().docenteId());
        assertEquals(before.caso().fechaCreacion(), after.caso().fechaCreacion());
        assertEquals(before.caso().fechaActualizacion(), after.caso().fechaActualizacion());
        assertEquals(before.caso().fechaBloqueoAcademico(), after.caso().fechaBloqueoAcademico());
        assertEquals(before.caso().estado(), after.caso().estado());
        assertEquals(before.caso().activo(), after.caso().activo());
        assertEquals(before.caso().escenarios(), after.caso().escenarios());
        assertEquals(before.caso().rubrica(), after.caso().rubrica());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/casos/" + id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.titulo").value("Guardado por HTTP"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(4)));
        mvc.perform(patchHttp(id, before.fingerprint(), "{\"titulo\":\"Obsoleto\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        assertEquals(after, updateReader().findById(id).orElseThrow());
    }

    @Test
    void twoConcurrentHttpPatchesWithSameFingerprintHaveExactlyOneWinner() throws Exception {
        var before = savedReplacement();
        UUID id = UUID.fromString(before.caso().id());
        var mvc = controlledHttp();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (String title : List.of("HTTP writer A", "HTTP writer B")) {
                tasks.add(executor.submit(() -> {
                    assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    return mvc.perform(patchHttp(id, before.fingerprint(), "{\"titulo\":\"" + title + "\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var task : tasks) { statuses.add(task.get(20, java.util.concurrent.TimeUnit.SECONDS)); }
            statuses.sort(Integer::compareTo);
            assertEquals(List.of(204, 409), statuses);
            var after = updateReader().findById(id).orElseThrow();
            assertTrue(Set.of("HTTP writer A", "HTTP writer B").contains(after.caso().titulo()));
            assertEquals(before.caso().escenarios(), after.caso().escenarios());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void httpPatchRollsBackEarlierCommandWhenLaterDatabaseWriteFails() throws Exception {
        var before = savedReplacement();
        UUID id = UUID.fromString(before.caso().id());
        // Fault injection only in the disposable test database; official migrations remain untouched.
        jdbc.execute("""
                CREATE FUNCTION public.m3_http_test_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW."historia" = 'HTTP rollback fault' THEN RAISE EXCEPTION 'injected private failure'; END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER m3_http_test_failure BEFORE UPDATE ON public."Caso"
                FOR EACH ROW EXECUTE FUNCTION public.m3_http_test_failure()
                """);
        try {
            controlledHttp().perform(patchHttp(id, before.fingerprint(),
                    "{\"titulo\":\"Must roll back\",\"historia\":\"HTTP rollback fault\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("injected"))));
            assertEquals(before, updateReader().findById(id).orElseThrow());
        } finally {
            jdbc.execute("DROP TRIGGER m3_http_test_failure ON public.\"Caso\"");
            jdbc.execute("DROP FUNCTION public.m3_http_test_failure()");
        }
    }

    @Test
    void httpPatchCannotEditAfterOfficialSchedulingTriggerLocksContent() throws Exception {
        Caso input = root(id(), teacher.toString(), List.of(), null, CasoEstado.PUBLICADO, CREATED);
        repository.insert(input);
        UUID id = UUID.fromString(input.id());
        jdbc.update("""
                INSERT INTO public."Sesion" ("idSesion", "idCaso", "idDocenteProgramador",
                  "nombreEspacioAcademico", "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, teacher, "HTTP lock fixture",
                Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")),
                Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")), 300);
        var before = updateReader().findById(id).orElseThrow();
        controlledHttp().perform(patchHttp(id, before.fingerprint(), "{\"titulo\":\"Forbidden\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        assertEquals(before, updateReader().findById(id).orElseThrow());
    }

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
    void publicationRejectsStructuralRubricAndPreservesEveryTableAndFingerprint() {
        Caso saved = creationService().createDraft("fixture", persistentDraftBuilder(teacher.toString(), JSON));
        var before = updateReader().findById(UUID.fromString(saved.id())).orElseThrow();
        List<Integer> counts = academicRowCounts();
        assertPublicationCode("INCOMPLETE_RUBRIC", () -> publication(true).publish(teacher.toString(),
                UUID.fromString(saved.id()), before.fingerprint()));
        assertEquals(before, updateReader().findById(UUID.fromString(saved.id())).orElseThrow());
        assertEquals(counts, academicRowCounts());
        assertFalse(creationService().isAvailableForScheduling(before.caso()));
    }

    @Test
    void publicationAuthorizationPrecedesStalePreconditionAndDoesNotWrite() {
        Caso saved = creationService().createDraft("fixture", persistentDraftBuilder(teacher.toString(), JSON));
        var before = updateReader().findById(UUID.fromString(saved.id())).orElseThrow();
        assertPublicationCode("FORBIDDEN", () -> publication(false).publish(teacher.toString(),
                UUID.fromString(saved.id()), "m3-v2:" + "0".repeat(64)));
        assertEquals(before, updateReader().findById(UUID.fromString(saved.id())).orElseThrow());
    }

    @Test
    void incompleteRubricRollsBackJoinedWorkWithoutChangingCommittedAggregates() {
        Caso target = creationService().createDraft("fixture", persistentDraftBuilder(teacher.toString(), JSON));
        Caso other = creationService().createDraft("fixture", persistentDraftBuilder(teacher.toString(), JSON));
        UUID targetId = UUID.fromString(target.id());
        UUID otherId = UUID.fromString(other.id());
        var targetBefore = updateReader().findById(targetId).orElseThrow();
        var otherBefore = updateReader().findById(otherId).orElseThrow();
        List<Integer> counts = academicRowCounts();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var em = org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            em.unwrap(org.hibernate.Session.class).doWork(connection -> {
                try (var statement = connection.prepareStatement(
                        "UPDATE public.\"Caso\" SET \"historia\" = ? WHERE \"idCaso\" = ?")) {
                    statement.setString(1, "Pending caller work must roll back on academic rejection");
                    statement.setObject(2, otherId);
                    assertEquals(1, statement.executeUpdate());
                }
            });
            // The target has not changed: this must reach rubric validation, not STALE_CASE.
            assertPublicationCode("INCOMPLETE_RUBRIC", () -> publication(true).publish(
                    teacher.toString(), targetId, targetBefore.fingerprint()));
            assertTrue(status.isRollbackOnly(), "Academic rejection must mark the joined transaction rollback-only");
            // Explicit outer rollback avoids treating UnexpectedRollbackException as test evidence.
            status.setRollbackOnly();
        });

        assertEquals(targetBefore, updateReader().findById(targetId).orElseThrow());
        assertEquals(otherBefore, updateReader().findById(otherId).orElseThrow());
        assertEquals(counts, academicRowCounts());
    }

    @Test
    void publicationRejectsMissingCaseStaleFingerprintAndNonDraftStates() {
        assertPublicationCode("CASE_NOT_FOUND", () -> publication(true).publish(teacher.toString(),
                UUID.randomUUID(), "m3-v2:" + "0".repeat(64)));
        for (CasoEstado state : CasoEstado.values()) {
            Caso input = root(id(), teacher.toString(), List.of(), null, state, CREATED);
            repository.insert(input); // Existing insert contract permits physical state fixtures; no transition is asserted.
            var before = updateReader().findById(UUID.fromString(input.id())).orElseThrow();
            assertPublicationCode("STALE_CASE", () -> publication(true).publish(teacher.toString(),
                    UUID.fromString(input.id()), "m3-v2:" + "0".repeat(64)));
            String expected = switch (state) {
                case BORRADOR -> "CASE_INCOMPLETE";
                case PUBLICADO -> "ALREADY_PUBLISHED";
                case ARCHIVADO -> "ARCHIVED_CASE_OPERATION_PENDING";
            };
            assertPublicationCode(expected, () -> publication(true).publish(teacher.toString(),
                    UUID.fromString(input.id()), before.fingerprint()));
            assertEquals(before, updateReader().findById(UUID.fromString(input.id())).orElseThrow());
        }
    }

    @Test
    void publicationRejectsOfficiallyScheduledCaseBeforeAlreadyPublished() {
        Caso input = root(id(), teacher.toString(), List.of(), null, CasoEstado.PUBLICADO, CREATED);
        repository.insert(input);
        jdbc.update("""
                INSERT INTO public."Sesion" ("idSesion", "idCaso", "idDocenteProgramador",
                    "nombreEspacioAcademico", "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(input.id()), teacher, "Publication F3-03 fixture",
                Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")),
                Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")), 300);
        var before = updateReader().findById(UUID.fromString(input.id())).orElseThrow();
        assertNotNull(before.caso().fechaBloqueoAcademico());
        assertPublicationCode("EVALUABLE_CONTENT_LOCKED", () -> publication(true).publish(teacher.toString(),
                UUID.fromString(input.id()), before.fingerprint()));
        assertEquals(before, updateReader().findById(UUID.fromString(input.id())).orElseThrow());
        assertTrue(creationService().isAvailableForScheduling(before.caso()));
    }

    @Test
    void requiredPublicationSeesOuterChangesUsesSameConnectionAndRollsBackTogether() {
        Caso saved = creationService().createDraft("fixture", persistentDraftBuilder(teacher.toString(), JSON));
        UUID caseId = UUID.fromString(saved.id());
        var before = updateReader().findById(caseId).orElseThrow();
        var observed = new java.util.concurrent.atomic.AtomicBoolean();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var em = org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(
                    context.getBean(EntityManagerFactory.class));
            var session = em.unwrap(org.hibernate.Session.class);
            long outerPid = session.doReturningWork(c -> {
                try (var s = c.prepareStatement("UPDATE public.\"Caso\" SET \"historia\" = ? WHERE \"idCaso\" = ?")) {
                    s.setString(1, "Outer pending history"); s.setObject(2, caseId); s.executeUpdate();
                }
                try (var s = c.createStatement(); var r = s.executeQuery("SELECT pg_backend_pid()")) {
                    r.next(); return r.getLong(1);
                }
            });
            var auth = new CaseAuthorizationPort() {
                public boolean canCreateCase(String user, String owner) { return false; }
                public boolean canModifyCase(String user, String id) { return false; }
                public boolean canPublishCase(String user, String id) {
                    assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    session.doWork(c -> {
                        try (var s = c.createStatement(); var r = s.executeQuery(
                                "SELECT pg_backend_pid(), current_setting('transaction_isolation')")) {
                            r.next(); assertEquals(outerPid, r.getLong(1));
                            assertEquals("read committed", r.getString(2));
                        }
                    });
                    observed.set(true);
                    return true;
                }
            };
            // The uncommitted outer history makes the old precondition stale: proves joined visibility.
            assertPublicationCode("STALE_CASE", () -> publication(auth).publish(teacher.toString(), caseId, before.fingerprint()));
            assertTrue(status.isRollbackOnly(), "Participating failure must mark the outer transaction rollback-only");
            status.setRollbackOnly();
        });
        assertTrue(observed.get());
        assertEquals(before, updateReader().findById(caseId).orElseThrow());
    }

    @Test
    void publicationRejectsIncompatibleOuterIsolationWithoutAuthorization() {
        var outer = new TransactionTemplate(transactions);
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var auth = org.mockito.Mockito.mock(CaseAuthorizationPort.class);
        outer.executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class, () -> publication(auth).publish(teacher.toString(),
                    UUID.randomUUID(), "m3-v2:" + "0".repeat(64)));
            status.setRollbackOnly();
        });
        org.mockito.Mockito.verifyNoInteractions(auth);
    }

    @Test
    void publicationWaitsForRootLockAndReadsCommittedChildChangeBeforeCheckingFingerprint() throws Exception {
        Caso saved = creationService().createDraft("fixture", persistentDraftBuilder(teacher.toString(), JSON));
        UUID caseId = UUID.fromString(saved.id());
        var before = updateReader().findById(caseId).orElseThrow();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var pid = new java.util.concurrent.CompletableFuture<Integer>();
        java.util.concurrent.Future<?> pending = null;
        try (var blocker = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            blocker.setAutoCommit(false);
            int blockerPid;
            try (var s = blocker.prepareStatement("SELECT pg_backend_pid() FROM public.\"Caso\" WHERE \"idCaso\" = ? FOR UPDATE")) {
                s.setObject(1, caseId);
                try (var r = s.executeQuery()) { assertTrue(r.next()); blockerPid = r.getInt(1); }
            }
            pending = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var em = org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(
                        context.getBean(EntityManagerFactory.class));
                em.unwrap(org.hibernate.Session.class).doWork(c -> {
                    try (var s = c.createStatement()) {
                        s.execute("SET LOCAL lock_timeout = '10s'");
                        s.execute("SET LOCAL statement_timeout = '12s'");
                        try (var r = s.executeQuery("SELECT pg_backend_pid()")) { r.next(); pid.complete(r.getInt(1)); }
                    }
                });
                publication(true).publish(teacher.toString(), caseId, before.fingerprint());
            }));
            int waiter = pid.get(5, java.util.concurrent.TimeUnit.SECONDS);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(7);
            boolean blocked = false;
            while (System.nanoTime() < deadline && !blocked) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",
                        Boolean.class, blockerPid, waiter));
                Thread.onSpinWait();
            }
            assertTrue(blocked, "PostgreSQL must observe publication waiting on the root lock");
            try (var s = blocker.prepareStatement("UPDATE public.\"Pregunta\" SET \"textoAyuda\" = ? WHERE \"idPregunta\" = ?")) {
                s.setString(1, "Committed concurrent help");
                s.setObject(2, UUID.fromString(saved.escenarios().getFirst().preguntas().getFirst().id()));
                assertEquals(1, s.executeUpdate());
            }
            blocker.commit();
            java.util.concurrent.Future<?> completed = pending;
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> completed.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("STALE_CASE", assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
            var after = updateReader().findById(caseId).orElseThrow();
            assertEquals(CasoEstado.BORRADOR, after.caso().estado());
            assertEquals("Committed concurrent help", after.caso().escenarios().getFirst()
                    .preguntas().getFirst().textoAyuda());
            assertFalse(before.fingerprint().equals(after.fingerprint()));
        } finally {
            if (pending != null) { pending.cancel(true); }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private static CasoCommandUpdateAdapter updateReader() {
        return new CasoCommandUpdateAdapter(context.getBean(EntityManagerFactory.class),
                context.getBean(CasoJpaAggregateReader.class), transactions);
    }

    private static CasoPublicationPort publication(boolean allowed) {
        return publication(new CaseAuthorizationPort() {
            public boolean canCreateCase(String user, String owner) { return false; }
            public boolean canModifyCase(String user, String id) { return false; }
            public boolean canPublishCase(String user, String id) { return allowed; }
        });
    }

    private static CasoPublicationPort publication(CaseAuthorizationPort authorization) {
        return new CasoPublicationAdapter(context.getBean(EntityManagerFactory.class), transactions, authorization);
    }

    private static void assertPublicationCode(String code, Runnable action) {
        assertEquals(code, assertThrows(CasoDomainException.class, action::run).code());
    }

    @Test
    void teacherQueryProjectsRealPersistedCaseWithoutChangingAcademicContent() {
        Caso input = full(JSON);
        repository.insert(input);
        Caso before = repository.findById(input.id()).orElseThrow();
        var policy = new com.prisaly.m3.application.port.AutorizacionConsultaCasoPort() {
            public Decision verificarAcceso(UUID usuario, UUID caso) {
                return teacher.equals(usuario) && UUID.fromString(input.id()).equals(caso)
                        ? Decision.AUTORIZADO : Decision.DENEGADO;
            }
            public Decision verificarVisibilidad(UUID usuario, UUID caso, CasoEstado estado, boolean activo) {
                // Controlled test policy only, not production visibility authorization.
                return Decision.AUTORIZADO;
            }
        };
        var query = new com.prisaly.m3.application.service.CasoDocenteConsultaService(() -> teacher, policy, repository);
        var response = com.prisaly.m3.presentation.dto.CasoDocenteConsultaResponse.from(query.consultar(input.id()));
        assertEquals(UUID.fromString(input.id()), response.idCaso());
        assertEquals(input.titulo(), response.titulo());
        assertEquals(input.tiempoEstimadoSegundos(), response.tiempoEstimadoSegundos());
        assertEquals(input.estado().name(), response.estado());
        assertEquals(before, repository.findById(input.id()).orElseThrow());
        var denied = new com.prisaly.m3.application.service.CasoDocenteConsultaService(
                () -> UUID.randomUUID(), policy, repository);
        assertEquals(com.prisaly.m3.application.service.CasoDocenteConsultaService.ACCESO_DENEGADO,
                assertThrows(CasoDomainException.class, () -> denied.consultar(input.id())).code());
    }

    @Test
    void serviceCreatesCompleteDraftAndReadsItAfterInsertCommit() {
        CasoBuilder builder = persistentDraftBuilder(teacher.toString(), JSON);
        Caso expected = builder.buildDraft();
        Caso returned = creationService().createDraft("fixture", builder);
        assertEquals(expected, returned);
        Caso loaded = repository.findById(returned.id()).orElseThrow();
        assertEquals(expected.id(), loaded.id());
        assertEquals(expected.docenteId(), loaded.docenteId());
        assertEquals(expected.titulo(), loaded.titulo());
        assertEquals(expected.tiempoEstimadoSegundos(), loaded.tiempoEstimadoSegundos());
        assertEquals(expected.historia(), loaded.historia());
        assertEquals(expected.desarrolloSituacional(), loaded.desarrolloSituacional());
        assertEquals(expected.activo(), loaded.activo());
        assertEquals(expected.fechaCreacion(), loaded.fechaCreacion());
        assertEquals(expected.fechaActualizacion(), loaded.fechaActualizacion());
        assertEquals(expected.fechaBloqueoAcademico(), loaded.fechaBloqueoAcademico());
        assertEquals(expected.escenarios(), loaded.escenarios());
        Rubrica rubric = expected.rubrica();
        assertEquals(new Rubrica(rubric.id(), rubric.criterios(), rubric.idCaso(), rubric.nombre(),
                rubric.descripcion(), rubric.puntajeTotal(), loaded.rubrica().definicionEvaluacion(),
                rubric.fechaCreacion(), rubric.preguntas()), loaded.rubrica());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)",
                Boolean.class, rubric.definicionEvaluacion(), loaded.rubrica().definicionEvaluacion()));
        assertEquals(CasoEstado.BORRADOR, loaded.estado());
        assertFalse(loaded.contenidoEvaluableBloqueado());
        assertFalse(creationService().isAvailableForScheduling(loaded));
    }

    @Test
    void servicePreservesOfficialDateDefaultWithoutRereadingInsideCallerTransaction() {
        CasoBuilder builder = new CasoBuilder().withId(id()).withDocenteId(teacher.toString())
                .withTitulo("Draft default date").withTiempoEstimadoSegundos(300);
        String caseId = builder.buildDraft().id();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Caso returned = creationService().createDraft("fixture", builder);
            assertNull(returned.fechaCreacion());
            assertTrue(repository.findById(caseId).isEmpty(), "Independent reader cannot see uncommitted work");
        });
        assertNotNull(repository.findById(caseId).orElseThrow().fechaCreacion());
    }

    @Test
    void servicePropagatesForeignKeyAndUniqueFailuresWithoutChangingExistingCase() {
        CasoBuilder unknownTeacher = persistentDraftBuilder(id(), JSON);
        assertSqlState("23503", () -> creationService().createDraft("fixture", unknownTeacher));
        assertAbsent(unknownTeacher.buildDraft());
        CasoBuilder builder = persistentDraftBuilder(teacher.toString(), JSON);
        Caso saved = creationService().createDraft("fixture", builder);
        Caso baseline = repository.findById(saved.id()).orElseThrow();
        List<Integer> counts = academicRowCounts();
        assertSqlState("23505", () -> creationService().createDraft("fixture", builder));
        assertEquals(counts, academicRowCounts());
        assertEquals(baseline, repository.findById(saved.id()).orElseThrow());
    }

    @Test
    void serviceRollsBackRootAndChildrenWhenRubricJsonIsInvalid() {
        CasoBuilder builder = persistentDraftBuilder(teacher.toString(), "{invalid-json");
        List<Integer> counts = academicRowCounts();
        assertSqlState("22P02", () -> creationService().createDraft("fixture", builder));
        assertAbsent(builder.buildDraft());
        assertEquals(counts, academicRowCounts());
    }

    @Test
    void serviceInsertParticipatesInCallerRollback() {
        CasoBuilder builder = persistentDraftBuilder(teacher.toString(), JSON);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            creationService().createDraft("fixture", builder);
            status.setRollbackOnly();
        });
        assertAbsent(builder.buildDraft());
    }

    @Test
    void serviceRejectsLegacyBuilderRubricWithoutDiscardingCriteria() {
        CasoBuilder builder = new CasoBuilder().withId(id()).withDocenteId(teacher.toString())
                .withTitulo("Legacy rubric").withTiempoEstimadoSegundos(300)
                .withRubricaId(id()).addCriterio(id(), "Legacy criterion", 10);
        Caso input = builder.buildDraft();
        assertThrows(IllegalArgumentException.class, () -> creationService().createDraft("fixture", builder));
        assertAbsent(input);
        assertEquals(1, input.rubrica().criterios().size());
    }

    private static CasoApplicationService creationService() {
        // Local fixture authorization only: no claim of effective M1 integration.
        return new CasoApplicationService(new CaseAuthorizationPort() {
            public boolean canCreateCase(String user, String owner) { return true; }
            public boolean canModifyCase(String user, String caseId) { return false; }
            public boolean canPublishCase(String user, String caseId) { return false; }
        }, repository);
    }

    private static CasoBuilder persistentDraftBuilder(String owner, String json) {
        String caseId = id();
        String rubricId = id();
        Pregunta question = new Pregunta(id(), "Question", List.of(
                new OpcionRespuesta(id(), "Correct", true, "Explanation", 4),
                new OpcionRespuesta(id(), "Alternative", false, null, 13)),
                "Help", "Borrador", true, 3, CREATED, UPDATED);
        Rubrica rubric = new Rubrica(rubricId, List.of(), caseId, "Structural rubric", null,
                new BigDecimal("12.50"), json, CREATED, Set.of(
                        new RubricaPregunta(id(), rubricId, question.id(), new BigDecimal("12.50"), null)));
        return new CasoBuilder().withId(caseId).withDocenteId(owner).withTitulo("Persistent draft")
                .withTiempoEstimadoSegundos(300).withHistoria("History")
                .withDesarrolloSituacional("Development").withActivo(false)
                .withFechaCreacion(CREATED).withFechaActualizacion(UPDATED).withRubrica(rubric)
                .addEscenario(id(), "Scenario", "Context", "Instructions", 7, false)
                .addPreguntaSeleccionUnica(question).and();
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
    void refusesLossyLegacyRepresentationsUpdateWithoutPreconditionAndSubmicrosecondDates() {
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
        assertThrows(IllegalArgumentException.class, () -> repository.update(precise));
    }

    @Test
    void authorizedReplacementPersistsExistingFieldsWithoutRecreatingRelationships() {
        var before = savedReplacement();
        Caso target = editedReplacement(before.caso(), "Updated root", "Updated scenario", "Updated question");
        List<Integer> counts = academicRowCounts();
        authorizedUpdater().update(UUID.fromString(target.id()), before.fingerprint(), target);
        Caso after = repository.findById(target.id()).orElseThrow();
        assertEquals(target, after);
        assertEquals(before.caso().docenteId(), after.docenteId());
        assertEquals(before.caso().fechaCreacion(), after.fechaCreacion());
        assertEquals(before.caso().fechaActualizacion(), after.fechaActualizacion());
        assertEquals(before.caso().fechaBloqueoAcademico(), after.fechaBloqueoAcademico());
        assertEquals(before.caso().rubrica(), after.rubrica());
        assertEquals(before.caso().escenarios().getFirst().preguntas().getFirst().opciones(),
                after.escenarios().getFirst().preguntas().getFirst().opciones());
        assertEquals(counts, academicRowCounts());
    }

    @Test
    void replacementRejectsOwnerLifecycleAndAuditChangesWithoutMutation() {
        var before = savedReplacement();
        Caso c = before.caso();
        for (Caso invalid : List.of(
                new Caso(c.id(), c.titulo(), id(), c.tiempoEstimadoSegundos(), c.estado(), c.escenarios(), c.rubrica(),
                        false, c.historia(), c.desarrolloSituacional(), c.activo(), c.fechaCreacion(), c.fechaActualizacion(), null),
                new Caso(c.id(), c.titulo(), c.docenteId(), c.tiempoEstimadoSegundos(), CasoEstado.PUBLICADO, c.escenarios(), c.rubrica(),
                        false, c.historia(), c.desarrolloSituacional(), c.activo(), c.fechaCreacion(), c.fechaActualizacion(), null),
                new Caso(c.id(), c.titulo(), c.docenteId(), c.tiempoEstimadoSegundos(), c.estado(), c.escenarios(), c.rubrica(),
                        false, c.historia(), c.desarrolloSituacional(), !c.activo(), c.fechaCreacion(), c.fechaActualizacion(), null),
                new Caso(c.id(), c.titulo(), c.docenteId(), c.tiempoEstimadoSegundos(), c.estado(), c.escenarios(), c.rubrica(),
                        false, c.historia(), c.desarrolloSituacional(), c.activo(), c.fechaCreacion(), UPDATED.plusSeconds(1), null))) {
            assertThrows(IllegalArgumentException.class, () -> repository.update(invalid, before.fingerprint()));
            assertEquals(before, updateReader().findById(UUID.fromString(c.id())).orElseThrow());
        }
    }

    @Test
    void replacementRejectsForeignScenarioAndImplicitOptionDeletion() {
        var before = savedReplacement();
        Caso c = before.caso();
        var s = c.escenarios().getFirst();
        var scenarios = new ArrayList<>(c.escenarios());
        scenarios.set(0, new Escenario(id(), s.titulo(), s.contexto(), s.instrucciones(), s.orden(), s.activo(), s.preguntas()));
        var foreign = replacementContent(c, c.titulo(), c.historia(), scenarios);
        assertThrows(IllegalArgumentException.class, () -> repository.update(foreign, before.fingerprint()));
        var question = s.preguntas().getFirst();
        var questions = new ArrayList<>(s.preguntas());
        questions.set(0, new Pregunta(question.id(), question.enunciado(), List.of(), question.textoAyuda(),
                question.estadoEdicion(), question.visible(), question.orden(), question.fechaCreacion(), question.fechaActualizacion()));
        scenarios.set(0, new Escenario(s.id(), s.titulo(), s.contexto(), s.instrucciones(), s.orden(), s.activo(), questions));
        var deletion = replacementContent(c, c.titulo(), c.historia(), scenarios);
        assertThrows(IllegalArgumentException.class, () -> repository.update(deletion, before.fingerprint()));
        assertEquals(before, updateReader().findById(UUID.fromString(c.id())).orElseThrow());
    }

    @Test
    void failedLaterScenarioWriteRollsBackTheEarlierRootWrite() {
        var before = savedReplacement();
        var target = editedReplacement(before.caso(), "Must roll back", "Invalid\u0000scenario", "Never persisted");
        List<Integer> counts = academicRowCounts();
        assertThrows(RuntimeException.class, () -> repository.update(target, before.fingerprint()));
        assertEquals(before, updateReader().findById(UUID.fromString(target.id())).orElseThrow());
        assertEquals(counts, academicRowCounts());
    }

    @Test
    void staleReplacementNeverOverwritesAnAlreadyCommittedChange() {
        var before = savedReplacement();
        var winner = replacementContent(before.caso(), "Winner", before.caso().historia(), before.caso().escenarios());
        repository.update(winner, before.fingerprint());
        var stale = replacementContent(before.caso(), "Stale writer", before.caso().historia(), before.caso().escenarios());
        assertEquals(CasoCommandUpdateAdapter.STALE_CASE, assertThrows(CasoDomainException.class,
                () -> repository.update(stale, before.fingerprint())).code());
        assertEquals(winner, repository.findById(winner.id()).orElseThrow());
    }

    @Test
    void simultaneousReplacementsWithOneFingerprintHaveExactlyOneWinner() throws Exception {
        var before = savedReplacement();
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var results = new ArrayList<java.util.concurrent.Future<String>>();
            for (String title : List.of("Concurrent A", "Concurrent B")) {
                results.add(executor.submit(() -> {
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) { throw new IllegalStateException("Start timeout"); }
                    try {
                        repository.update(replacementContent(before.caso(), title, before.caso().historia(), before.caso().escenarios()), before.fingerprint());
                        return "UPDATED";
                    } catch (CasoDomainException failure) { return failure.code(); }
                }));
            }
            start.countDown();
            var outcomes = new ArrayList<String>();
            for (var result : results) { outcomes.add(result.get(30, java.util.concurrent.TimeUnit.SECONDS)); }
            assertEquals(1L, outcomes.stream().filter("UPDATED"::equals).count());
            assertEquals(1L, outcomes.stream().filter(CasoCommandUpdateAdapter.STALE_CASE::equals).count());
            assertTrue(Set.of("Concurrent A", "Concurrent B").contains(repository.findById(before.caso().id()).orElseThrow().titulo()));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void replacementRequiresExistingCaseAndExplicitFingerprint() {
        var before = savedReplacement();
        assertThrows(IllegalArgumentException.class, () -> repository.update(before.caso()));
        assertThrows(IllegalArgumentException.class, () -> repository.update(before.caso(), null));
        Caso missing = root(id(), teacher.toString(), List.of(), null, CasoEstado.BORRADOR, CREATED);
        assertEquals(CasoCommandUpdateAdapter.CASE_NOT_FOUND, assertThrows(CasoDomainException.class,
                () -> repository.update(missing, before.fingerprint())).code());
        assertEquals(before, updateReader().findById(UUID.fromString(before.caso().id())).orElseThrow());
    }

    @Test
    void repositoryReplacementCannotBypassAnAcademicLockOrArchivedState() {
        for (CasoEstado state : List.of(CasoEstado.ARCHIVADO, CasoEstado.PUBLICADO)) {
            Caso input = root(id(), teacher.toString(), List.of(), null, state, CREATED);
            repository.insert(input);
            if (state == CasoEstado.PUBLICADO) {
                // Establish the lock through the official scheduling trigger, never by changing audit metadata.
                jdbc.update("""
                        INSERT INTO public."Sesion" ("idSesion", "idCaso", "idDocenteProgramador",
                            "nombreEspacioAcademico", "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), UUID.fromString(input.id()), teacher, "Replacement lock fixture",
                        Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")),
                        Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")), 300);
            }
            var before = updateReader().findById(UUID.fromString(input.id())).orElseThrow();
            var changed = replacementContent(before.caso(), "Forbidden", null, List.of());
            assertThrows(CasoDomainException.class, () -> repository.update(changed, before.fingerprint()));
            assertEquals(before, updateReader().findById(UUID.fromString(input.id())).orElseThrow());
        }
    }

    @Test
    void persistedPublicationRejectsANonOwnerBeforeAcademicValidation() {
        var before = savedReplacement();
        assertPublicationCode("FORBIDDEN", () -> publication(true).publish(UUID.randomUUID().toString(),
                UUID.fromString(before.caso().id()), before.fingerprint()));
        assertEquals(before, updateReader().findById(UUID.fromString(before.caso().id())).orElseThrow());
    }

    @Test
    void identityAwarePublicationStillRejectsStructuralCompletenessWithoutWriting() {
        Caso saved = creationService().createDraft("fixture", persistentDraftBuilder(teacher.toString(), JSON));
        var before = updateReader().findById(UUID.fromString(saved.id())).orElseThrow();
        var service = new com.prisaly.m3.application.service.CasoPersistentPublicationService(publication(true), () -> teacher);
        List<Integer> counts = academicRowCounts();
        assertPublicationCode("INCOMPLETE_RUBRIC", () -> service.publish(UUID.fromString(before.caso().id()), before.fingerprint()));
        assertEquals(before, updateReader().findById(UUID.fromString(before.caso().id())).orElseThrow());
        assertEquals(counts, academicRowCounts());
    }

    @Test
    void m4VisibilityProtocolRequiresBothPublishedAndActiveWithoutChangingEither() {
        for (CasoEstado state : CasoEstado.values()) {
            for (boolean active : List.of(false, true)) {
                Caso base = root(id(), teacher.toString(), List.of(), null, state, CREATED);
                Caso input = new Caso(base.id(), base.titulo(), base.docenteId(), base.tiempoEstimadoSegundos(),
                        state, List.of(), null, false, null, null, active, CREATED, null, null);
                repository.insert(input);
                // Exact predicate consumed by M4's existing SQL adapter, not a new M4 provider.
                boolean visible = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT \"estado\" = 'Publicado' AND \"activo\" FROM public.\"Caso\" WHERE \"idCaso\" = ?",
                        Boolean.class, UUID.fromString(input.id())));
                assertEquals(state == CasoEstado.PUBLICADO && active, visible);
                assertEquals(visible, creationService().isAvailableForScheduling(repository.findById(input.id()).orElseThrow()));
                assertEquals(input, repository.findById(input.id()).orElseThrow());
            }
        }
    }

    @Test
    void m4SchedulingLockCommittedWhileReplacementWaitsInvalidatesItsFingerprint() throws Exception {
        // Already-published storage fixture, not a successful publication transition or institutional grant.
        Caso input = root(id(), teacher.toString(), List.of(), null, CasoEstado.PUBLICADO, CREATED);
        repository.insert(input);
        UUID caseId = UUID.fromString(input.id());
        var before = updateReader().findById(caseId).orElseThrow();
        Caso stale = replacementContent(before.caso(), "Must not overwrite M4", null, List.of());
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<?> pending = null;
        try (var scheduling = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            scheduling.setAutoCommit(false);
            int blockerPid;
            try (var statement = scheduling.prepareStatement(
                    "SELECT \"estado\", \"activo\", pg_backend_pid() FROM public.\"Caso\" WHERE \"idCaso\" = ? FOR UPDATE")) {
                statement.setObject(1, caseId);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("Publicado", rows.getString(1));
                    assertTrue(rows.getBoolean(2));
                    blockerPid = rows.getInt(3);
                }
            }
            pending = executor.submit(() -> repository.update(stale, before.fingerprint()));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            boolean blocked = false;
            while (!blocked && System.nanoTime() < deadline) {
                Long waiters = jdbc.queryForObject(
                        "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))", Long.class, blockerPid);
                blocked = waiters != null && waiters > 0;
                Thread.onSpinWait();
            }
            assertTrue(blocked, "PostgreSQL must observe the replacement waiting for M4's root lock");
            try (var statement = scheduling.prepareStatement("""
                    INSERT INTO public."Sesion" ("idSesion", "idCaso", "idDocenteProgramador",
                        "nombreEspacioAcademico", "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, UUID.randomUUID()); statement.setObject(2, caseId); statement.setObject(3, teacher);
                statement.setString(4, "M4 scheduling protocol fixture");
                statement.setTimestamp(5, Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")));
                statement.setTimestamp(6, Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")));
                statement.setInt(7, 300);
                assertEquals(1, statement.executeUpdate());
            }
            // solicitarBloqueoAcademico confirms the official trigger's write in the scheduling transaction.
            try (var statement = scheduling.prepareStatement(
                    "SELECT \"fechaBloqueoAcademico\" IS NOT NULL FROM public.\"Caso\" WHERE \"idCaso\" = ?")) {
                statement.setObject(1, caseId);
                try (var rows = statement.executeQuery()) { assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); }
            }
            scheduling.commit();
            java.util.concurrent.Future<?> completed = pending;
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> completed.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(CasoCommandUpdateAdapter.STALE_CASE, assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
            var after = updateReader().findById(caseId).orElseThrow();
            assertNotNull(after.caso().fechaBloqueoAcademico());
            assertEquals(before.caso().titulo(), after.caso().titulo());
            assertEquals(before.caso().activo(), after.caso().activo());
            assertEquals(before.caso().estado(), after.caso().estado());
            assertEquals(before.caso().fechaCreacion(), after.caso().fechaCreacion());
            assertEquals(before.caso().fechaActualizacion(), after.caso().fechaActualizacion());
            assertFalse(before.fingerprint().equals(after.fingerprint()));
            assertEquals("EVALUABLE_CONTENT_LOCKED", assertThrows(CasoDomainException.class,
                    () -> repository.update(replacementContent(after.caso(), "Still forbidden", null, List.of()), after.fingerprint())).code());
            assertEquals(after, updateReader().findById(caseId).orElseThrow());
        } finally {
            if (pending != null) { pending.cancel(true); }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private static com.prisaly.m3.application.update.CasoUpdateResult savedReplacement() {
        Caso input = full(JSON);
        repository.insert(input);
        return updateReader().findById(UUID.fromString(input.id())).orElseThrow();
    }

    private static com.prisaly.m3.application.service.CasoPersistentUpdateService authorizedUpdater() {
        // Explicit test double. APP's productive modification authorization remains closed.
        var auth = new CaseAuthorizationPort() {
            public boolean canCreateCase(String user, String owner) { return false; }
            public boolean canModifyCase(String user, String caso) { return teacher.toString().equals(user); }
            public boolean canPublishCase(String user, String caso) { return false; }
        };
        return new com.prisaly.m3.application.service.CasoPersistentUpdateService(() -> teacher, auth, repository);
    }

    private static Caso replacementContent(Caso c, String title, String history, List<Escenario> scenarios) {
        return new Caso(c.id(), title, c.docenteId(), c.tiempoEstimadoSegundos(), c.estado(), scenarios, c.rubrica(),
                c.contenidoEvaluableBloqueado(), history, c.desarrolloSituacional(), c.activo(),
                c.fechaCreacion(), c.fechaActualizacion(), c.fechaBloqueoAcademico());
    }

    private static Caso editedReplacement(Caso c, String title, String scenarioTitle, String statement) {
        var scenarios = new ArrayList<>(c.escenarios());
        var s = scenarios.getFirst();
        var questions = new ArrayList<>(s.preguntas());
        var q = questions.getFirst();
        questions.set(0, new Pregunta(q.id(), statement, q.opciones(), "Updated help", q.estadoEdicion(),
                q.visible(), q.orden(), q.fechaCreacion(), q.fechaActualizacion()));
        scenarios.set(0, new Escenario(s.id(), scenarioTitle, "Updated context", "Updated instructions", s.orden(), s.activo(), questions));
        return new Caso(c.id(), title, c.docenteId(), c.tiempoEstimadoSegundos() + 60, c.estado(), scenarios, c.rubrica(),
                c.contenidoEvaluableBloqueado(), "Updated history", "Updated development", c.activo(),
                c.fechaCreacion(), c.fechaActualizacion(), c.fechaBloqueoAcademico());
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
