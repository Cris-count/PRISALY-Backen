package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateReader;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.CasoRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.EscenarioRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.OpcionRespuestaRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.PreguntaRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.RubricaPreguntaRow;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.RubricaRow;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.Session;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Complete storage snapshot tests, including a deterministic two-connection interleaving. */
class CasoJpaAggregateReadIT {

    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-01-03T04:05:06.654321Z");
    private static final String JSON = """
            {"texto":"psicología", "objeto":{"valor":null,"bandera":true},
             "lista":[1,"dos",false,null,{"otra":"clave"}],
             "numero":12345678901234567890.123456789}
            """;
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql",
            "V003__bd0052_integridad.sql", "V004__bd0052_integridad_intentos.sql",
            "V005__bd0052_cierre_integridad.sql", "V006__bd0052_proteccion_relaciones.sql",
            "V007__fase3_politicas_aprobadas.sql", "V008__bloqueo_caso_al_reasignar_sesion.sql",
            "V009__rf09_solo_casos_publicados.sql");

    @Test
    void preservesCompleteAggregateAndRepeatableReadSnapshot() throws Exception {
        Path migrations = canonicalMigrationDirectory();
        try (var postgres = new PostgreSQLContainer("postgres:16.15")) {
            postgres.start();
            // Execute the canonical scripts unchanged, as in the existing M3 ITs.
            // This verifies their schema/triggers, not Flyway history bookkeeping.
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                for (String migration : MIGRATIONS) {
                    statement.execute(Files.readString(migrations.resolve(migration), StandardCharsets.UTF_8));
                }
            }
            var datasource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(datasource);
            prepareFixtures(jdbc);

            AtomicReference<Runnable> beforeScenarioRead = new AtomicReference<>(() -> { });
            StatementInspector inspector = sql -> {
                if (sql.contains("from public.\"Escenario\"")) {
                    beforeScenarioRead.get().run();
                }
                return sql;
            };
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(datasource);
            factory.setPackagesToScan(CasoJpaAggregateReader.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.jdbc.time_zone", "UTC",
                    "hibernate.session_factory.statement_inspector", inspector,
                    "hibernate.cache.use_second_level_cache", false,
                    "hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD"));
            try {
                factory.afterPropertiesSet();
                EntityManagerFactory emf = factory.getObject();
                assertNotNull(emf);
                var reader = new CasoJpaAggregateReader(emf); // no transactional proxy required
                assertTrue(reader.findById(id("missing")).isEmpty());
                assertThrows(NullPointerException.class, () -> reader.findById(null));
                verifyBareAndNullable(reader);
                CasoJpaAggregateSnapshot full = reader.findById(id("rich")).orElseThrow();
                verifyFullSnapshot(full, jdbc);
                verifyLegitimateLock(reader, jdbc);

                // Called immediately before the second table is read, AFTER Caso's SELECT.
                // The writer commits on a separate JDBC connection, with no sleeps or race.
                AtomicBoolean interleaved = new AtomicBoolean();
                beforeScenarioRead.set(() -> {
                    if (!interleaved.compareAndSet(false, true)) {
                        return;
                    }
                    var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
                    assertNotNull(em);
                    int readerPid = em.unwrap(Session.class).doReturningWork(connection -> {
                        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
                        assertTrue(connection.isReadOnly());
                        try (var statement = connection.createStatement();
                             var result = statement.executeQuery("SELECT pg_backend_pid(), current_setting('transaction_read_only')")) {
                            assertTrue(result.next());
                            assertEquals("on", result.getString(2));
                            return result.getInt(1);
                        }
                    });
                    commitConcurrentChange(datasource, readerPid);
                });
                CasoJpaAggregateSnapshot during = reader.findById(id("rich")).orElseThrow();
                assertTrue(interleaved.get(), "Interleaving hook must execute between Caso and Escenario reads");
                assertEquals(full, during, "All six tables must retain the pre-commit snapshot");
                beforeScenarioRead.set(() -> { });
                CasoJpaAggregateSnapshot after = reader.findById(id("rich")).orElseThrow();
                assertEquals("Historia nueva", after.caso().historia());
                assertEquals("Pregunta nueva", after.preguntas().stream()
                        .filter(p -> p.idPregunta().equals(id("question-1-1"))).findFirst().orElseThrow().enunciado());
                assertEquals("Historia original", full.caso().historia(), "Detached snapshot stays unchanged");
                assertThrows(UnsupportedOperationException.class, () -> full.escenarios().clear());
                assertThrows(UnsupportedOperationException.class, () -> full.preguntas().clear());
                assertThrows(UnsupportedOperationException.class, () -> full.opciones().clear());
                assertThrows(UnsupportedOperationException.class, () -> full.rubricaPreguntas().clear());
            } finally {
                factory.destroy();
            }
        }
    }

    private static void verifyBareAndNullable(CasoJpaAggregateReader reader) {
        CasoJpaAggregateSnapshot bare = reader.findById(id("bare")).orElseThrow();
        assertEquals(new CasoRow(id("bare"), id("teacher"), "Caso bare", null, null,
                300, "Borrador", true, null, CREATED, null), bare.caso());
        assertTrue(bare.escenarios().isEmpty());
        assertTrue(bare.preguntas().isEmpty());
        assertTrue(bare.opciones().isEmpty());
        assertNull(bare.rubrica());
        assertTrue(bare.rubricaPreguntas().isEmpty());

        CasoJpaAggregateSnapshot nullable = reader.findById(id("nullable")).orElseThrow();
        assertEquals(new RubricaRow(id("rubric-nullable"), id("nullable"), "Rubrica nullable",
                null, null, null, CREATED), nullable.rubrica());
        assertTrue(nullable.rubricaPreguntas().isEmpty());
    }

    private static void verifyFullSnapshot(CasoJpaAggregateSnapshot full, JdbcTemplate jdbc) {
        assertEquals(new CasoRow(id("rich"), id("teacher"), "Caso rich", "Historia original",
                "Desarrollo original", 300, "Archivado", false, null, CREATED, UPDATED), full.caso());
        assertEquals(List.of(
                new EscenarioRow(id("scenario-1"), id("rich"), null, null, null, 2, false),
                new EscenarioRow(id("scenario-2"), id("rich"), "Titulo", "Contexto", "Instrucciones", 9, true)),
                full.escenarios());
        List<PreguntaRow> expectedQuestions = new ArrayList<>();
        List<OpcionRespuestaRow> expectedOptions = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            UUID scenario = id("scenario-" + i);
            assertEquals(List.of(3, 11), full.preguntas().stream().filter(p -> p.idEscenario().equals(scenario))
                    .map(PreguntaRow::orden).toList());
            for (int j = 1; j <= 2; j++) {
                String key = "question-" + i + "-" + j;
                expectedQuestions.add(new PreguntaRow(id(key), scenario, "Enunciado " + key,
                        j == 1 ? null : "Ayuda", j == 1 ? "Borrador" : "Lista", j == 2,
                        j == 1 ? 3 : 11, CREATED, j == 1 ? null : UPDATED));
                assertEquals(List.of(4, 13), full.opciones().stream().filter(o -> o.idPregunta().equals(id(key)))
                        .map(OpcionRespuestaRow::orden).toList());
                for (int k = 1; k <= 2; k++) {
                    expectedOptions.add(new OpcionRespuestaRow(id(key + "-option-" + k), id(key),
                            "Respuesta " + k, k == 2, k == 1 ? null : "Explicacion", k == 1 ? 4 : 13));
                }
            }
        }
        assertEquals(4, full.preguntas().size());
        assertEquals(8, full.opciones().size());
        assertEquals(new HashSet<>(expectedQuestions), new HashSet<>(full.preguntas()));
        assertEquals(new HashSet<>(expectedOptions), new HashSet<>(full.opciones()));
        String canonicalJson = jdbc.queryForObject("""
                SELECT "definicionEvaluacion"::text FROM public."Rubrica" WHERE "idRubrica" = ?
                """, String.class, id("rubric-rich"));
        assertEquals(new RubricaRow(id("rubric-rich"), id("rich"), "Rubrica completa",
                "Descripcion", new BigDecimal("25.75"), canonicalJson, CREATED), full.rubrica());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)",
                Boolean.class, JSON, full.rubrica().definicionEvaluacion()));
        assertEquals(Set.of(
                new RubricaPreguntaRow(id("association-1"), id("rubric-rich"), id("question-1-1"),
                        new BigDecimal("12.50"), null),
                new RubricaPreguntaRow(id("association-2"), id("rubric-rich"), id("question-2-2"),
                        new BigDecimal("7.25"), "Criterio")), full.rubricaPreguntas());
        Set<UUID> questionIds = full.preguntas().stream().map(PreguntaRow::idPregunta)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(full.rubricaPreguntas().stream().allMatch(r -> questionIds.contains(r.idPregunta())));
    }

    private static void verifyLegitimateLock(CasoJpaAggregateReader reader, JdbcTemplate jdbc) {
        CasoJpaAggregateSnapshot locked = reader.findById(id("locked")).orElseThrow();
        Instant databaseDate = jdbc.queryForObject("""
                SELECT "fechaBloqueoAcademico" FROM public."Caso" WHERE "idCaso" = ?
                """, (rs, row) -> rs.getTimestamp(1).toInstant(), id("locked"));
        assertNotNull(databaseDate);
        assertEquals(databaseDate, locked.caso().fechaBloqueoAcademico());
        assertEquals("Publicado", locked.caso().estado());
        assertTrue(locked.caso().activo());
    }

    private static void commitConcurrentChange(DriverManagerDataSource datasource, int readerPid) {
        try (var connection = datasource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                    assertTrue(result.next());
                    assertFalse(readerPid == result.getInt(1), "Writer must use a different connection");
                }
                try (var statement = connection.prepareStatement("""
                        UPDATE public."Caso" SET "historia" = 'Historia nueva' WHERE "idCaso" = ?
                        """)) {
                    statement.setObject(1, id("rich"));
                    assertEquals(1, statement.executeUpdate());
                }
                try (var statement = connection.prepareStatement("""
                        UPDATE public."Pregunta" SET "enunciado" = 'Pregunta nueva' WHERE "idPregunta" = ?
                        """)) {
                    statement.setObject(1, id("question-1-1"));
                    assertEquals(1, statement.executeUpdate());
                }
                connection.commit();
            } catch (Exception | AssertionError failure) {
                connection.rollback();
                throw failure;
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Concurrent JDBC fixture update failed", failure);
        }
    }

    private static void prepareFixtures(JdbcTemplate jdbc) {
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion")
                VALUES ('DocenteSnapshotM3', 'Fixture de lectura') RETURNING "idRol"
                """, Integer.class);
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo") VALUES (?, ?, ?, ?)
                """, id("teacher"), role, "Docente de prueba", "snapshot.m3@prisaly.test");
        for (String key : List.of("bare", "nullable", "rich", "locked")) {
            jdbc.update("""
                    INSERT INTO public."Caso"
                        ("idCaso", "idDocente", "nombre", "historia", "desarrolloSituacional",
                         "tiempoEstimadoSegundos", "estado", "activo", "fechaCreacion", "fechaActualizacion")
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id(key), id("teacher"), "Caso " + key,
                    key.equals("rich") ? "Historia original" : null,
                    key.equals("rich") ? "Desarrollo original" : null, 300,
                    key.equals("rich") ? "Archivado" : key.equals("locked") ? "Publicado" : "Borrador",
                    !key.equals("rich"), Timestamp.from(CREATED), key.equals("rich") ? Timestamp.from(UPDATED) : null);
        }
        // Reverse insertion, nonconsecutive physical order values, no filtering of flags.
        for (int i = 2; i >= 1; i--) {
            jdbc.update("""
                    INSERT INTO public."Escenario"
                        ("idEscenario", "idCaso", "titulo", "contexto", "instrucciones", "orden", "activo")
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id("scenario-" + i), id("rich"), i == 1 ? null : "Titulo",
                    i == 1 ? null : "Contexto", i == 1 ? null : "Instrucciones", i == 1 ? 2 : 9, i == 2);
            for (int j = 2; j >= 1; j--) {
                String key = "question-" + i + "-" + j;
                jdbc.update("""
                        INSERT INTO public."Pregunta"
                            ("idPregunta", "idEscenario", "enunciado", "textoAyuda", "estadoEdicion",
                             "visible", "orden", "fechaCreacion", "fechaActualizacion")
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id(key), id("scenario-" + i), "Enunciado " + key,
                        j == 1 ? null : "Ayuda", j == 1 ? "Borrador" : "Lista", j == 2,
                        j == 1 ? 3 : 11, Timestamp.from(CREATED), j == 1 ? null : Timestamp.from(UPDATED));
                for (int k = 2; k >= 1; k--) {
                    jdbc.update("""
                            INSERT INTO public."OpcionRespuesta"
                                ("idOpcion", "idPregunta", "texto", "esCorrecta", "explicacion", "orden")
                            VALUES (?, ?, ?, ?, ?, ?)
                            """, id(key + "-option-" + k), id(key), "Respuesta " + k,
                            k == 2, k == 1 ? null : "Explicacion", k == 1 ? 4 : 13);
                }
            }
        }
        jdbc.update("""
                INSERT INTO public."Rubrica"
                    ("idRubrica", "idCaso", "nombre", "descripcion", "puntajeTotal", "definicionEvaluacion", "fechaCreacion")
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, id("rubric-rich"), id("rich"), "Rubrica completa", "Descripcion",
                new BigDecimal("25.75"), JSON, Timestamp.from(CREATED));
        jdbc.update("""
                INSERT INTO public."Rubrica" ("idRubrica", "idCaso", "nombre", "fechaCreacion") VALUES (?, ?, ?, ?)
                """, id("rubric-nullable"), id("nullable"), "Rubrica nullable", Timestamp.from(CREATED));
        for (int i = 1; i <= 2; i++) {
            jdbc.update("""
                    INSERT INTO public."RubricaPregunta"
                        ("idRubricaPregunta", "idRubrica", "idPregunta", "valorPregunta", "criterio")
                    VALUES (?, ?, ?, ?, ?)
                    """, id("association-" + i), id("rubric-rich"),
                    id(i == 1 ? "question-1-1" : "question-2-2"),
                    new BigDecimal(i == 1 ? "12.50" : "7.25"), i == 1 ? null : "Criterio");
        }
        // Only the legitimate session trigger establishes the academic lock.
        jdbc.update("""
                INSERT INTO public."Sesion"
                    ("idSesion", "idCaso", "idDocenteProgramador", "nombreEspacioAcademico",
                     "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id("session"), id("locked"), id("teacher"), "Espacio de prueba",
                Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")),
                Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")), 300);
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("m3-aggregate-read-" + key).getBytes(StandardCharsets.UTF_8));
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
