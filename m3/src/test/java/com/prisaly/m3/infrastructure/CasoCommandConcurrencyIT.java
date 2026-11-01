package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.m3.application.update.CasoUpdateCommand.*;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import com.prisaly.m3.infrastructure.persistence.jpa.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** 10B.2: test-only JDBC tracing/gates. Production adapters and triggers are unchanged. */
@Timeout(60)
class CasoCommandConcurrencyIT {
    private static PostgreSQLContainer postgres;
    private static UUID teacher;
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Set<String> TABLES = Set.of("Caso", "Escenario", "Pregunta", "OpcionRespuesta", "Rubrica", "RubricaPregunta");
    private static final Pattern TABLE = Pattern.compile(
            "(?:from|update|into)\\s+public\\.\"(Caso|Escenario|Pregunta|OpcionRespuesta|Rubrica|RubricaPregunta)\"",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql", "V003__bd0052_integridad.sql",
            "V004__bd0052_integridad_intentos.sql", "V005__bd0052_cierre_integridad.sql",
            "V006__bd0052_proteccion_relaciones.sql", "V007__fase3_politicas_aprobadas.sql",
            "V008__bloqueo_caso_al_reasignar_sesion.sql", "V009__rf09_solo_casos_publicados.sql");

    @BeforeAll
    static void database() throws Exception {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path migrations = List.of(cwd.resolve("app/src/main/resources/db/migration"),
                cwd.resolve("../app/src/main/resources/db/migration").normalize()).stream()
                .filter(p -> MIGRATIONS.stream().allMatch(n -> Files.isRegularFile(p.resolve(n))))
                .findFirst().orElseThrow(() -> new IllegalStateException("Official V001-V009 not found"));
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
            for (String migration : MIGRATIONS) { statement.execute(Files.readString(migrations.resolve(migration), StandardCharsets.UTF_8)); }
        }
        var jdbc = new JdbcTemplate(datasource);
        Integer role = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "DocenteConcurrencyM3", "Fixture 10B.2");
        teacher = UUID.randomUUID();
        jdbc.update("INSERT INTO public.\"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                teacher, role, "Docente fixture", "concurrency.m3@prisaly.test");
    }

    @AfterAll
    static void stop() { if (postgres != null) { postgres.stop(); } }

    @Test
    void directQuestionAndRubricWritersWaitForRootAfterM3HasValidatedFingerprint() throws Exception {
        for (boolean rubric : List.of(false, true)) {
            try (var h = new Harness(4); var workers = new Workers(); var gate = h.pauseBeforeFirstMutation()) {
                var before = h.saved();
                Future<CasoUpdateResult> pendingM3 = workers.submit(() -> h.as("m3", () -> h.rename(before, "M3 confirmed")));
                gate.awaitReached(); // First UPDATE is prepared only AFTER root lock, snapshot and fingerprint validation.
                Probe m3 = h.started("m3");
                Future<Integer> direct = workers.submit(() -> h.as("direct", () -> h.directChild(before, rubric, "Direct confirmed")));
                Probe writer = h.started("direct");
                try {
                    assertNotEquals(m3.pid(), writer.pid());
                    h.awaitBlocked(writer.pid(), m3.pid()); // No explicit root lock in directChild(). V007 must supply it.
                    assertFalse(direct.isDone());
                } finally { gate.close(); }
                var committedM3 = pendingM3.get(15, TimeUnit.SECONDS);
                assertEquals(1, direct.get(15, TimeUnit.SECONDS).intValue());
                var current = h.read(before);
                assertEquals("M3 confirmed", current.caso().titulo());
                assertEquals("Direct confirmed", childText(current, rubric));
                assertEquals(childText(before, rubric), childText(committedM3, rubric),
                        "The M3 result must not contain the still-uncommitted direct writer's value");
                assertNotEquals(committedM3.fingerprint(), current.fingerprint());
                assertCode(CasoCommandUpdateAdapter.STALE_CASE, () -> h.rename(committedM3, "Must not overwrite"));
                assertEquals(current, h.read(before));
                h.assertOneWriterTransaction();
            }
        }
    }

    @Test
    void directChildWriterObtainingRootViaTriggerFirstMakesPendingM3PreconditionStale() throws Exception {
        for (boolean rubric : List.of(false, true)) {
            try (var h = new Harness(4); var workers = new Workers(); var direct = h.datasource.getConnection()) {
                var before = h.saved();
                direct.setAutoCommit(false);
                Probe first = probe(direct);
                try (var statement = direct.prepareStatement(childSql(rubric))) {
                    statement.setString(1, "Direct first"); statement.setObject(2, childId(before, rubric));
                    assertEquals(1, statement.executeUpdate()); // Trigger has locked Caso; no root SELECT here.
                }
                Future<CasoUpdateResult> pending = workers.submit(() -> h.as("m3", () -> h.rename(before, "Must not write")));
                Probe second = h.started("m3");
                try { h.awaitBlocked(second.pid(), first.pid()); } finally { direct.commit(); }
                assertFutureCode(CasoCommandUpdateAdapter.STALE_CASE, pending);
                var current = h.read(before);
                assertEquals(before.caso().titulo(), current.caso().titulo());
                assertEquals("Direct first", childText(current, rubric));
            }
        }
    }

    @Test
    void m3CommitsFirstThenWaitingSessionLocksTheUpdatedCase() throws Exception {
        try (var h = new Harness(4); var workers = new Workers(); var gate = h.pauseBeforeFirstMutation()) {
            var before = h.saved();
            Future<CasoUpdateResult> m3 = workers.submit(() -> h.as("m3", () -> h.rename(before, "Before programming")));
            gate.awaitReached();
            Probe owner = h.started("m3");
            Future<UUID> session = workers.submit(() -> h.as("program", () -> h.schedule(before)));
            Probe scheduler = h.started("program");
            try { h.awaitBlocked(scheduler.pid(), owner.pid()); } finally { gate.close(); }
            var updated = m3.get(15, TimeUnit.SECONDS);
            UUID sessionId = session.get(15, TimeUnit.SECONDS);
            assertFalse(updated.caso().contenidoEvaluableBloqueado());
            var locked = h.read(before);
            assertEquals("Before programming", locked.caso().titulo());
            assertNotNull(locked.caso().fechaBloqueoAcademico());
            assertTrue(locked.caso().contenidoEvaluableBloqueado());
            assertNotEquals(updated.fingerprint(), locked.fingerprint());
            assertEquals(UUID.fromString(before.caso().id()), h.jdbc.queryForObject(
                    "SELECT \"idCaso\" FROM public.\"Sesion\" WHERE \"idSesion\" = ?", UUID.class, sessionId));
            assertCode(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, () -> h.rename(locked, "Blocked"));
            assertEquals(locked, h.read(before));
        }
    }

    @Test
    void writerRequiresNewSuspendsAndRestoresOuterTransactionAndCommitsIndependently() throws Exception {
        try (var h = new Harness(2)) {
            var target = h.saved();
            var unrelated = h.saved();
            List<String> events = new ArrayList<>();
            var outer = new TransactionTemplate(h.manager);
            outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            outer.executeWithoutResult(status -> {
                var em = EntityManagerFactoryUtils.getTransactionalEntityManager(h.emf);
                assertNotNull(em);
                Object resource = TransactionSynchronizationManager.getResource(h.emf);
                Probe initial = em.unwrap(Session.class).doReturningWork(CasoCommandConcurrencyIT::probe);
                em.unwrap(Session.class).doWork(c -> writeOuterHistory(c, unrelated, "Outer pending"));
                h.outerUncommitted = unrelated;
                registerEvents(events);
                CasoUpdateResult result;
                try { result = h.as("m3", () -> h.rename(target, "Inner committed")); }
                catch (Exception e) { throw new AssertionError(e); }
                assertEquals("Inner committed", result.caso().titulo());
                assertEquals(List.of("suspend", "resume"), events);
                assertSame(resource, TransactionSynchronizationManager.getResource(h.emf));
                assertSame(em, EntityManagerFactoryUtils.getTransactionalEntityManager(h.emf));
                assertEquals(initial, em.unwrap(Session.class).doReturningWork(CasoCommandConcurrencyIT::probe));
                assertEquals("repeatable read", initial.isolation());
                Probe inner = h.observations.stream().filter(o -> o.role().equals("m3")).findFirst().orElseThrow().probe();
                assertNotEquals(initial.pid(), inner.pid());
                assertNotEquals(initial.transaction(), inner.transaction());
                assertEquals("Outer pending", em.unwrap(Session.class).doReturningWork(c -> history(c, unrelated)));
                assertEquals(Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ),
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                status.setRollbackOnly();
            });
            assertEquals("Inner committed", h.read(target).caso().titulo());
            assertEquals(unrelated, h.read(unrelated), "Outer rollback must not undo the inner commit");
            assertFalse(TransactionSynchronizationManager.hasResource(h.emf));
            h.assertOneWriterTransaction();
        }
    }

    @Test
    void writerPoolExhaustionIsBoundedAndResumesOuterResource() throws Exception {
        try (var h = new Harness(1)) {
            var before = h.saved();
            List<String> events = new ArrayList<>();
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                var em = EntityManagerFactoryUtils.getTransactionalEntityManager(h.emf);
                assertNotNull(em);
                Probe initial = em.unwrap(Session.class).doReturningWork(CasoCommandConcurrencyIT::probe);
                Object resource = TransactionSynchronizationManager.getResource(h.emf);
                registerEvents(events);
                var failure = assertThrows(CannotCreateTransactionException.class, () -> h.rename(before, "No connection"));
                assertTrue(hasCause(failure, SQLTransientConnectionException.class));
                assertEquals(List.of("suspend", "resume"), events);
                assertSame(resource, TransactionSynchronizationManager.getResource(h.emf));
                assertEquals(initial, em.unwrap(Session.class).doReturningWork(CasoCommandConcurrencyIT::probe));
                assertFalse(status.isRollbackOnly());
                status.setRollbackOnly();
            });
            assertEquals(before, h.read(before));
            assertEquals("After recovery", h.rename(before, "After recovery").caso().titulo());
        }
    }

    @Test
    void failedInnerCompositionRollsBackEveryCommandWhileOuterTransactionCanCommit() {
        try (var h = new Harness(2)) {
            var target = h.saved();
            var unrelated = h.saved();
            new TransactionTemplate(h.manager).executeWithoutResult(status -> {
                var em = EntityManagerFactoryUtils.getTransactionalEntityManager(h.emf);
                assertNotNull(em);
                em.unwrap(Session.class).doWork(c -> writeOuterHistory(c, unrelated, "Outer committed"));
                RuntimeException failure = assertThrows(RuntimeException.class, () -> h.updates.update(
                        UUID.fromString(target.caso().id()), target.fingerprint(), List.of(
                                new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, "Must roll back"),
                                new ActualizarEscenarioCommand(UUID.fromString(target.caso().escenarios().getLast().id()),
                                        CampoEscenario.ORDEN, 2))));
                assertTrue(hasSqlState(failure, "23505"));
                assertFalse(status.isRollbackOnly());
            });
            assertEquals(target, h.read(target));
            assertEquals("Outer committed", h.read(unrelated).caso().historia());
        }
    }

    @Test
    void legitimateHistoricalReferenceRemainsProtectedByRestrictiveFkAndF303Priority() {
        try (var h = new Harness(3)) {
            var before = h.saved();
            UUID sessionId = h.schedule(before); // Official trigger, never a manual lock date.
            UUID student = UUID.randomUUID();
            UUID user = UUID.randomUUID();
            Integer role = h.jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                    Integer.class, "EstudianteHistory10B2", "SQL fixture, no M1 permissions implementation");
            h.jdbc.update("INSERT INTO public.\"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                    user, role, "Estudiante fixture", user + "@prisaly.test");
            h.jdbc.update("INSERT INTO public.\"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", student, user);
            UUID invitation = UUID.randomUUID();
            UUID participation = UUID.randomUUID();
            UUID attempt = UUID.randomUUID();
            UUID response = UUID.randomUUID();
            h.jdbc.update("INSERT INTO public.\"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)",
                    invitation, sessionId, student);
            h.jdbc.update("INSERT INTO public.\"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participation, invitation);
            // First attempt, fresh student/case; use approved Intento state and official defaults elsewhere.
            h.jdbc.update("""
                    INSERT INTO public."Intento" ("idIntento","idParticipacion","numeroIntento","inicio","limiteTemporal","estado")
                    VALUES (?,?,1,clock_timestamp(),clock_timestamp()+interval '5 minutes','EnCurso')
                    """, attempt, participation);
            var question = before.caso().escenarios().getFirst().preguntas().getFirst();
            UUID option = UUID.fromString(question.opciones().getLast().id());
            h.jdbc.update("INSERT INTO public.\"Respuesta\" (\"idRespuesta\",\"idIntento\",\"idPregunta\",\"idOpcionSeleccionada\") VALUES (?,?,?,?)",
                    response, attempt, UUID.fromString(question.id()), option);
            assertEquals(Integer.valueOf(1), h.jdbc.queryForObject("""
                    SELECT count(*) FROM public."Respuesta" r JOIN public."Intento" i USING ("idIntento")
                    JOIN public."Participacion" p USING ("idParticipacion") JOIN public."Convocatoria" c USING ("idConvocatoria")
                    JOIN public."Sesion" s USING ("idSesion") WHERE r."idRespuesta"=? AND s."idCaso"=?
                    """, Integer.class, response, UUID.fromString(before.caso().id())));
            assertEquals("r", h.jdbc.queryForObject("""
                    SELECT confdeltype::text FROM pg_constraint WHERE conname='fk_respuesta_idopcionseleccionada_idpregunta'
                    AND convalidated AND conrelid='public."Respuesta"'::regclass AND confrelid='public."OpcionRespuesta"'::regclass
                    """, String.class));
            var locked = h.read(before);
            assertCode(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, () -> h.updates.update(
                    UUID.fromString(before.caso().id()), locked.fingerprint(), List.of(new EliminarOpcionCommand(option))));
            var sql = assertThrows(RuntimeException.class, () -> h.jdbc.update(
                    "DELETE FROM public.\"OpcionRespuesta\" WHERE \"idOpcion\"=?", option));
            assertTrue(hasSqlState(sql, "23514"), "F3-03 must reject before FK deletion checking");
            assertEquals(option, h.jdbc.queryForObject("SELECT \"idOpcionSeleccionada\" FROM public.\"Respuesta\" WHERE \"idRespuesta\"=?",
                    UUID.class, response));
            assertEquals(locked, h.read(before));
        }
    }

    private record Probe(int pid, long transaction, String isolation, String readOnly, boolean autoCommit) { }
    private record Observation(String role, String sql, Probe probe) { }

    private static Probe probe(Connection c) throws SQLException {
        try (var statement = c.createStatement()) {
            statement.setQueryTimeout(5);
            try (var r = statement.executeQuery("SELECT pg_backend_pid(),txid_current(),current_setting('transaction_isolation'),current_setting('transaction_read_only')")) {
                assertTrue(r.next());
                return new Probe(r.getInt(1), r.getLong(2), r.getString(3), r.getString(4), c.getAutoCommit());
            }
        }
    }

    private static void registerEvents(List<String> events) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void suspend() { events.add("suspend"); }
            public void resume() { events.add("resume"); }
        });
    }

    private static void writeOuterHistory(Connection c, CasoUpdateResult row, String value) throws SQLException {
        try (var s = c.prepareStatement("UPDATE public.\"Caso\" SET \"historia\"=? WHERE \"idCaso\"=?")) {
            s.setString(1, value); s.setObject(2, UUID.fromString(row.caso().id())); assertEquals(1, s.executeUpdate());
        }
    }

    private static String history(Connection c, CasoUpdateResult row) throws SQLException {
        try (var s = c.prepareStatement("SELECT \"historia\" FROM public.\"Caso\" WHERE \"idCaso\"=?")) {
            s.setObject(1, UUID.fromString(row.caso().id()));
            try (var r = s.executeQuery()) { assertTrue(r.next()); return r.getString(1); }
        }
    }

    private static UUID childId(CasoUpdateResult row, boolean rubric) {
        return UUID.fromString(rubric ? row.caso().rubrica().id() : row.caso().escenarios().getFirst().preguntas().getFirst().id());
    }

    private static String childText(CasoUpdateResult row, boolean rubric) {
        return rubric ? row.caso().rubrica().descripcion() : row.caso().escenarios().getFirst().preguntas().getFirst().enunciado();
    }

    private static String childSql(boolean rubric) {
        return rubric ? "UPDATE public.\"Rubrica\" SET \"descripcion\"=? WHERE \"idRubrica\"=?"
                : "UPDATE public.\"Pregunta\" SET \"enunciado\"=? WHERE \"idPregunta\"=?";
    }

    private static void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(CasoDomainException.class, action::run).code());
    }

    private static void assertFutureCode(String code, Future<?> future) {
        var failure = assertThrows(ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS));
        assertEquals(code, assertInstanceOf(CasoDomainException.class, failure.getCause()).code());
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable c = error; c != null; c = c.getCause()) { if (type.isInstance(c)) { return true; } }
        return false;
    }

    private static boolean hasSqlState(Throwable error, String state) {
        for (Throwable c = error; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql && state.equals(sql.getSQLState())) { return true; }
        }
        return false;
    }

    private static final class Gate implements AutoCloseable {
        private final AtomicBoolean once = new AtomicBoolean();
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        void before(String role, String sql) throws InterruptedException {
            if (role.equals("m3") && sql.startsWith("UPDATE public.\"Caso\"") && once.compareAndSet(false, true)) {
                reached.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("JDBC gate release timed out"); }
            }
        }
        void awaitReached() throws InterruptedException { assertTrue(reached.await(10, TimeUnit.SECONDS), "M3 must reach its first mutation"); }
        public void close() { release.countDown(); }
    }

    private static final class Workers implements AutoCloseable {
        private final ExecutorService executor = Executors.newFixedThreadPool(2);
        private final List<Future<?>> futures = new ArrayList<>();
        <T> Future<T> submit(Callable<T> task) { Future<T> f = executor.submit(task); futures.add(f); return f; }
        public void close() throws InterruptedException {
            for (Future<?> f : futures) { f.cancel(true); }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS), "Workers must terminate within SQL/socket timeouts");
        }
    }

    private static final class Harness implements AutoCloseable {
        private final HikariDataSource pool;
        private final AbstractDataSource datasource;
        private final LocalContainerEntityManagerFactoryBean factory;
        private final EntityManagerFactory emf;
        private final JpaTransactionManager manager;
        private final CasoPersistenceAdapter inserts;
        private final CasoCommandUpdateAdapter updates;
        private final JdbcTemplate jdbc;
        private final ThreadLocal<String> role = new ThreadLocal<>();
        private final Map<String, CompletableFuture<Probe>> started = new ConcurrentHashMap<>();
        private final List<Observation> observations = new CopyOnWriteArrayList<>();
        private volatile Gate gate;
        private CasoUpdateResult outerUncommitted;

        Harness(int maximumPoolSize) {
            var config = new HikariConfig();
            config.setJdbcUrl(postgres.getJdbcUrl()); config.setUsername(postgres.getUsername()); config.setPassword(postgres.getPassword());
            config.setMaximumPoolSize(maximumPoolSize); config.setMinimumIdle(0); config.setConnectionTimeout(500);
            config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            config.addDataSourceProperty("socketTimeout", "12");
            config.setConnectionInitSql("SET statement_timeout = '10s'");
            pool = new HikariDataSource(config);
            datasource = new AbstractDataSource() {
                public Connection getConnection() throws SQLException { return traced(pool.getConnection()); }
                public Connection getConnection(String user, String password) throws SQLException { return getConnection(); }
            };
            jdbc = new JdbcTemplate(datasource);
            factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(datasource); factory.setPackagesToScan(CasoJpaAggregateReader.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.globally_quoted_identifiers", true));
            try {
                factory.afterPropertiesSet(); emf = factory.getObject(); assertNotNull(emf);
                manager = new JpaTransactionManager(emf);
                var reader = new CasoJpaAggregateReader(emf);
                inserts = new CasoPersistenceAdapter(emf, reader, manager);
                updates = new CasoCommandUpdateAdapter(emf, reader, manager);
            } catch (RuntimeException | Error failure) { factory.destroy(); pool.close(); throw failure; }
        }

        private Connection traced(Connection raw) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (p, m, args) -> {
                Object result = invoke(m, raw, args);
                if (m.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql) {
                    PreparedStatement statement = (PreparedStatement) result;
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class}, (sp, sm, sa) -> {
                        String label = role.get();
                        if (label != null && (sm.getName().equals("executeQuery") || sm.getName().equals("executeUpdate"))) {
                            Probe probe = probe(raw); // Uses raw createStatement: never re-enters tracing.
                            if (label.equals("m3") && outerUncommitted != null) {
                                assertEquals(outerUncommitted.caso().historia(), history(raw, outerUncommitted),
                                        "The writer connection must not see the suspended transaction's uncommitted change");
                            }
                            observations.add(new Observation(label, sql, probe));
                            started.computeIfAbsent(label, ignored -> new CompletableFuture<>()).complete(probe);
                            Gate current = gate;
                            if (current != null) { current.before(label, sql); }
                        }
                        return invoke(sm, statement, sa);
                    });
                }
                return result;
            });
        }

        private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
            try { return method.invoke(target, args); } catch (InvocationTargetException failure) { throw failure.getCause(); }
        }

        <T> T as(String label, Callable<T> task) throws Exception {
            role.set(label);
            try { return task.call(); } finally { role.remove(); }
        }

        Probe started(String label) throws Exception {
            return started.computeIfAbsent(label, ignored -> new CompletableFuture<>()).get(10, TimeUnit.SECONDS);
        }

        Gate pauseBeforeFirstMutation() { gate = new Gate(); return gate; }

        void awaitBlocked(int waiter, int blocker) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, blocker, waiter))) { return; }
                Thread.onSpinWait();
            }
            fail("Expected backend " + waiter + " to wait for backend " + blocker);
        }

        void assertOneWriterTransaction() {
            var relevant = observations.stream().filter(o -> o.role().equals("m3") && TABLE.matcher(o.sql()).find()).toList();
            assertFalse(relevant.isEmpty());
            Probe first = relevant.getFirst().probe();
            for (var observation : relevant) {
                assertEquals(first, observation.probe(), "Every academic statement must share physical backend, transaction and effective isolation");
                assertEquals("read committed", observation.probe().isolation());
                assertEquals("off", observation.probe().readOnly()); assertFalse(observation.probe().autoCommit());
            }
            var reads = relevant.stream().filter(o -> o.sql().stripLeading().startsWith("SELECT")).toList();
            assertEquals(12, reads.size(), "All six tables must be read before and after mutation");
            for (int start : List.of(0, 6)) {
                Set<String> tables = reads.subList(start, start + 6).stream().map(o -> {
                    var matcher = TABLE.matcher(o.sql()); assertTrue(matcher.find()); return matcher.group(1);
                }).collect(java.util.stream.Collectors.toSet());
                assertEquals(TABLES, tables);
            }
        }

        int directChild(CasoUpdateResult before, boolean rubric, String value) {
            return jdbc.update(childSql(rubric), value, childId(before, rubric)); // Deliberately no root SELECT.
        }

        CasoUpdateResult read(CasoUpdateResult before) { return updates.findById(UUID.fromString(before.caso().id())).orElseThrow(); }
        CasoUpdateResult rename(CasoUpdateResult before, String name) {
            return updates.update(UUID.fromString(before.caso().id()), before.fingerprint(), List.of(new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, name)));
        }

        UUID schedule(CasoUpdateResult before) {
            UUID session = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public."Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                        "inicioPrevisto","finPrevisto","fechaInicioReal","tiempoMaximoParticipacionSegundos","estado")
                    VALUES (?,?,?,?,clock_timestamp()-interval '1 minute',clock_timestamp()+interval '5 minutes',clock_timestamp(),300,'EnCurso')
                    """, session, UUID.fromString(before.caso().id()), teacher, "Fixture 10B.2");
            return session;
        }

        CasoUpdateResult saved() {
            String root = UUID.randomUUID().toString();
            String question = UUID.randomUUID().toString();
            String rubric = UUID.randomUUID().toString();
            var q = new Pregunta(question, "Question", List.of(
                    new OpcionRespuesta(UUID.randomUUID().toString(), "Correct", true, null, 4),
                    new OpcionRespuesta(UUID.randomUUID().toString(), "Incorrect", false, null, 13)),
                    "Help", "Lista", true, 3, CREATED, null);
            var r = new Rubrica(rubric, List.of(), root, "Rubric", "Original rubric", new BigDecimal("12.50"),
                    "{\"n\":1.00}", CREATED, Set.of(new RubricaPregunta(UUID.randomUUID().toString(), rubric, question, new BigDecimal("12.50"), null)));
            var caso = new Caso(root, "Original", teacher.toString(), 300, CasoEstado.PUBLICADO,
                    List.of(new Escenario(UUID.randomUUID().toString(), "Scenario", null, null, 2, true, List.of(q)),
                            new Escenario(UUID.randomUUID().toString(), null, null, null, 19, true, List.of())),
                    r, false, "Original history", null, true, CREATED, null, null);
            inserts.insert(caso);
            return updates.findById(UUID.fromString(root)).orElseThrow();
        }

        public void close() {
            if (gate != null) { gate.close(); }
            try { factory.destroy(); } finally { pool.close(); }
        }
    }
}
