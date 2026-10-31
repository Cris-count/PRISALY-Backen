package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateReader;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.Session;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** Tests the unchanged reader with independent outer/inner managers and real pooled connections. */
class CasoJpaAggregateTransactionIT {

    private static final UUID CASE_ID = id("case");
    private static final String COMMITTED_HISTORY = "Historia confirmada";
    private static final String PENDING_HISTORY = "Historia externa sin confirmar";
    private static final Pattern ROOT_TABLE = Pattern.compile(
            "\\bfrom\\s+(?:\"?public\"?\\.)?\"(Caso|Escenario|Pregunta|OpcionRespuesta|Rubrica|RubricaPregunta)\"",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql",
            "V003__bd0052_integridad.sql", "V004__bd0052_integridad_intentos.sql",
            "V005__bd0052_cierre_integridad.sql", "V006__bd0052_proteccion_relaciones.sql",
            "V007__fase3_politicas_aprobadas.sql", "V008__bloqueo_caso_al_reasignar_sesion.sql",
            "V009__rf09_solo_casos_publicados.sql");
    private static PostgreSQLContainer postgres;

    @BeforeAll
    static void prepareDatabase() throws Exception {
        Path migrations = canonicalMigrationDirectory();
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            // Canonical SQL, unchanged; as in the other M3 ITs, not Flyway history validation.
            for (String migration : MIGRATIONS) {
                statement.execute(Files.readString(migrations.resolve(migration), StandardCharsets.UTF_8));
            }
        }
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion")
                VALUES ('DocenteTxM3', 'Fixture transaccional') RETURNING "idRol"
                """, Integer.class);
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo") VALUES (?, ?, ?, ?)
                """, id("teacher"), role, "Docente de prueba", "transaction.m3@prisaly.test");
        jdbc.update("""
                INSERT INTO public."Caso" ("idCaso", "idDocente", "nombre", "historia", "tiempoEstimadoSegundos")
                VALUES (?, ?, ?, ?, ?)
                """, CASE_ID, id("teacher"), "Caso transaccional", COMMITTED_HISTORY, 300);
        jdbc.update("""
                INSERT INTO public."Escenario" ("idEscenario", "idCaso", "orden") VALUES (?, ?, ?)
                """, id("scenario"), CASE_ID, 1);
        jdbc.update("""
                INSERT INTO public."Pregunta" ("idPregunta", "idEscenario", "enunciado", "orden")
                VALUES (?, ?, ?, ?)
                """, id("question"), id("scenario"), "Pregunta de prueba", 1);
        jdbc.update("""
                INSERT INTO public."OpcionRespuesta" ("idOpcion", "idPregunta", "texto", "orden")
                VALUES (?, ?, ?, ?)
                """, id("option"), id("question"), "Opcion de prueba", 1);
        jdbc.update("""
                INSERT INTO public."Rubrica" ("idRubrica", "idCaso", "nombre") VALUES (?, ?, ?)
                """, id("rubric"), CASE_ID, "Rubrica de prueba");
        jdbc.update("""
                INSERT INTO public."RubricaPregunta"
                    ("idRubricaPregunta", "idRubrica", "idPregunta", "valorPregunta") VALUES (?, ?, ?, ?)
                """, id("association"), id("rubric"), id("question"), new java.math.BigDecimal("12.50"));
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void requiresNewSuspendsOuterTransactionAndUsesOneIndependentRepeatableReadConnection() {
        try (var harness = new Harness(2)) {
            assertEquals("read committed", harness.jdbc().queryForObject(
                    "SELECT current_setting('transaction_isolation')", String.class));
            List<String> events = new ArrayList<>();
            harness.outerTransaction().executeWithoutResult(status -> {
                EntityManager outer = boundEntityManager(harness.emf);
                Object outerResource = TransactionSynchronizationManager.getResource(harness.emf);
                Probe before = probe(outer);
                assertEquals("read committed", before.isolation());
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, before.jdbcIsolation());
                assertEquals("off", before.readOnly());
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                registerSuspensionEvents(events);
                writePendingHistory(outer);
                assertEquals(PENDING_HISTORY, history(outer));

                CasoJpaAggregateSnapshot snapshot;
                harness.tracing = true;
                try {
                    snapshot = harness.reader.findById(CASE_ID).orElseThrow();
                } finally {
                    harness.tracing = false;
                }
                assertEquals(COMMITTED_HISTORY, snapshot.caso().historia());
                assertEquals(List.of("suspend", "resume"), events);
                assertEquals(List.of("Caso", "Escenario", "Pregunta", "OpcionRespuesta", "Rubrica", "RubricaPregunta"),
                        harness.observations.stream().map(Observation::table).toList());
                Observation first = harness.observations.getFirst();
                assertNotSame(outer, first.entityManager());
                assertNotSame(outerResource, first.resource());
                assertNotEquals(before.pid(), first.probe().pid());
                assertNotEquals(before.transactionId(), first.probe().transactionId());
                for (Observation observation : harness.observations) {
                    assertSame(first.entityManager(), observation.entityManager());
                    assertSame(first.resource(), observation.resource());
                    assertEquals(first.probe(), observation.probe(), "Same backend and SQL transaction for every SELECT");
                    assertEquals("repeatable read", observation.probe().isolation());
                    assertEquals(Connection.TRANSACTION_REPEATABLE_READ, observation.probe().jdbcIsolation());
                    assertEquals("on", observation.probe().readOnly());
                    assertTrue(observation.probe().jdbcReadOnly());
                    assertFalse(observation.probe().autoCommit());
                }
                assertSame(outer, boundEntityManager(harness.emf));
                assertSame(outerResource, TransactionSynchronizationManager.getResource(harness.emf));
                assertEquals(before, probe(outer), "Outer physical connection, transaction and isolation are restored");
                assertEquals(PENDING_HISTORY, history(outer), "Outer uncommitted work remains available to its owner");
                assertEquals(Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED),
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                status.setRollbackOnly();
            });
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.hasResource(harness.emf));
            assertEquals(COMMITTED_HISTORY, harness.jdbc().queryForObject("""
                    SELECT "historia" FROM public."Caso" WHERE "idCaso" = ?
                    """, String.class, CASE_ID));
            assertEquals("read committed", harness.jdbc().queryForObject(
                    "SELECT current_setting('transaction_isolation')", String.class));
            assertEquals("off", harness.jdbc().queryForObject(
                    "SELECT current_setting('transaction_read_only')", String.class));
        }
    }

    @Test
    void exhaustedSingleConnectionPoolFailsWithinAcquisitionTimeoutAndResumesOuterTransaction() {
        try (var harness = new Harness(1)) {
            List<String> events = new ArrayList<>();
            harness.outerTransaction().executeWithoutResult(status -> {
                EntityManager outer = boundEntityManager(harness.emf);
                Object outerResource = TransactionSynchronizationManager.getResource(harness.emf);
                Probe before = probe(outer); // force acquisition before invoking REQUIRES_NEW
                registerSuspensionEvents(events);
                writePendingHistory(outer);
                assertEquals(1, harness.pool.getHikariPoolMXBean().getActiveConnections());
                assertEquals(1, harness.pool.getMaximumPoolSize());
                CannotCreateTransactionException failure = assertThrows(CannotCreateTransactionException.class,
                        () -> harness.reader.findById(CASE_ID));
                assertTrue(hasCause(failure, SQLTransientConnectionException.class),
                        "Failure must originate in pool acquisition, not an unsupported isolation configuration");
                assertEquals(List.of("suspend", "resume"), events);
                assertSame(outerResource, TransactionSynchronizationManager.getResource(harness.emf));
                assertSame(outer, boundEntityManager(harness.emf));
                assertEquals(before, probe(outer));
                assertEquals(PENDING_HISTORY, history(outer));
                assertFalse(status.isRollbackOnly(), "Handled inner startup failure must not mark outer rollback-only");
                status.setRollbackOnly();
            });
            assertFalse(TransactionSynchronizationManager.hasResource(harness.emf));
            assertEquals(COMMITTED_HISTORY, harness.reader.findById(CASE_ID).orElseThrow().caso().historia(),
                    "Pool remains usable after the outer transaction releases its connection");
        }
    }

    private static void registerSuspensionEvents(List<String> events) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void suspend() { events.add("suspend"); }
            @Override
            public void resume() { events.add("resume"); }
        });
    }

    private static EntityManager boundEntityManager(EntityManagerFactory emf) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertTrue(TransactionSynchronizationManager.hasResource(emf));
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
        assertNotNull(em);
        return em;
    }

    private static Probe probe(EntityManager em) {
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT pg_backend_pid(), txid_current(),
                                current_setting('transaction_isolation'), current_setting('transaction_read_only')
                         """)) {
                assertTrue(result.next());
                return new Probe(result.getInt(1), result.getLong(2), result.getString(3), result.getString(4),
                        connection.getTransactionIsolation(), connection.isReadOnly(), connection.getAutoCommit());
            }
        });
    }

    private static void writePendingHistory(EntityManager em) {
        // Fixture-only JDBC write on the outer transaction's connection; no aggregate writer is introduced.
        em.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE public."Caso" SET "historia" = ? WHERE "idCaso" = ?
                    """)) {
                statement.setString(1, PENDING_HISTORY);
                statement.setObject(2, CASE_ID);
                assertEquals(1, statement.executeUpdate());
            }
        });
    }

    private static String history(EntityManager em) {
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT "historia" FROM public."Caso" WHERE "idCaso" = ?
                    """)) {
                statement.setObject(1, CASE_ID);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    return result.getString(1);
                }
            }
        });
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private record Probe(int pid, long transactionId, String isolation, String readOnly,
            int jdbcIsolation, boolean jdbcReadOnly, boolean autoCommit) {
    }

    private record Observation(String table, EntityManager entityManager, Object resource, Probe probe) {
    }

    private static final class Harness implements AutoCloseable {
        private final HikariDataSource pool;
        private final LocalContainerEntityManagerFactoryBean factory;
        private final EntityManagerFactory emf;
        private final CasoJpaAggregateReader reader;
        private final List<Observation> observations = new ArrayList<>();
        private boolean tracing;

        private Harness(int maximumPoolSize) {
            var config = new HikariConfig();
            config.setJdbcUrl(postgres.getJdbcUrl());
            config.setUsername(postgres.getUsername());
            config.setPassword(postgres.getPassword());
            config.setMaximumPoolSize(maximumPoolSize);
            config.setMinimumIdle(0);
            // Bounded pool-acquisition timeout, not a sleep or a scheduling assertion.
            config.setConnectionTimeout(500);
            // Known pool baseline; inner SQL isolation must nevertheless become repeatable read.
            config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            pool = new HikariDataSource(config);
            factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(pool);
            factory.setPackagesToScan(CasoJpaAggregateReader.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            StatementInspector inspector = this::inspect;
            // Mirror app's relevant JPA properties. Deliberately do NOT override
            // hibernate.connection.handling_mode, unlike the previous read IT.
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.globally_quoted_identifiers", true,
                    "hibernate.session_factory.statement_inspector", inspector));
            try {
                factory.afterPropertiesSet();
                emf = factory.getObject();
                assertNotNull(emf);
                reader = new CasoJpaAggregateReader(emf);
            } catch (RuntimeException | Error failure) {
                factory.destroy();
                pool.close();
                throw failure;
            }
        }

        private String inspect(String sql) {
            if (tracing) {
                var matcher = ROOT_TABLE.matcher(sql);
                assertTrue(matcher.find(), "Unexpected SQL while tracing reader: " + sql);
                EntityManager em = boundEntityManager(emf);
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ),
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                // Direct JDBC probes bypass StatementInspector, avoiding recursive observations.
                observations.add(new Observation(matcher.group(1), em,
                        TransactionSynchronizationManager.getResource(emf), probe(em)));
            }
            return sql;
        }

        private TransactionTemplate outerTransaction() {
            // A different manager instance, sharing the EMF/DataSource, as app/reader integration requires.
            var transaction = new TransactionTemplate(new JpaTransactionManager(emf));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return transaction;
        }

        private JdbcTemplate jdbc() { return new JdbcTemplate(pool); }

        @Override
        public void close() {
            try {
                factory.destroy();
            } finally {
                pool.close();
            }
        }
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("m3-aggregate-transaction-" + key).getBytes(StandardCharsets.UTF_8));
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
