package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaEntity;
import com.prisaly.m3.infrastructure.persistence.jpa.EscenarioJpaEntity;
import com.prisaly.m3.infrastructure.persistence.jpa.PreguntaJpaEntity;
import com.prisaly.m3.infrastructure.persistence.jpa.RubricaJpaEntity;
import com.prisaly.m3.infrastructure.persistence.jpa.RubricaPreguntaJpaEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Reads official rows; JDBC is used only for migrations and isolated test fixtures. */
class RubricaJpaReadIT {

    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    // Synthetic JSON transport fixture, deliberately unrelated to any grading formula.
    private static final String JSON = """
            {"texto":"psicología", "anidado":{"bandera":true,"ausente":null},
             "lista":[1,"dos",false,null,{"clave":"valor"}],
             "decimal":12345678901234567890.123456789,"vacio":{}}
            """;
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql",
            "V002__bd0052_restricciones.sql",
            "V003__bd0052_integridad.sql",
            "V004__bd0052_integridad_intentos.sql",
            "V005__bd0052_cierre_integridad.sql",
            "V006__bd0052_proteccion_relaciones.sql",
            "V007__fase3_politicas_aprobadas.sql",
            "V008__bloqueo_caso_al_reasignar_sesion.sql",
            "V009__rf09_solo_casos_publicados.sql");

    @Test
    void readsRubricsAndQuestionValuesWithoutLosingDecimalsJsonOrNulls() throws Exception {
        Path migrations = canonicalMigrationDirectory();
        try (var postgres = new PostgreSQLContainer("postgres:16.15")) {
            postgres.start();
            // Same canonical SQL execution as the existing M3 read ITs.
            // This does not certify Flyway's schema history bookkeeping.
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
            verifySqlConstraints(jdbc);

            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(datasource);
            factory.setPackagesToScan(RubricaJpaEntity.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.jdbc.time_zone", "UTC"));
            try {
                factory.afterPropertiesSet();
                EntityManager em = factory.getObject().createEntityManager();
                try {
                    em.getTransaction().begin();
                    verifyRead(em, jdbc);
                    em.getTransaction().commit();
                } finally {
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    em.close();
                }
            } finally {
                factory.destroy();
            }
        }
    }

    private static void verifyRead(EntityManager em, JdbcTemplate jdbc) {
        RubricaJpaEntity rubric = em.find(RubricaJpaEntity.class, id("rubric-1"));
        assertNotNull(rubric);
        assertEquals(id("rubric-1"), rubric.getIdRubrica());
        assertEquals(id("case-1"), rubric.getIdCaso());
        assertEquals("Rubrica de lectura", rubric.getNombre());
        assertEquals("Descripcion de prueba", rubric.getDescripcion());
        assertEquals(new BigDecimal("25.75"), rubric.getPuntajeTotal());
        assertEquals(CREATED, rubric.getFechaCreacion());
        assertNotNull(rubric.getDefinicionEvaluacion());
        // Compare semantic JSONB values, not formatting, key order or Java doubles.
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT CAST(? AS jsonb) = CAST(? AS jsonb)", Boolean.class,
                JSON, rubric.getDefinicionEvaluacion()));

        RubricaJpaEntity nullable = em.find(RubricaJpaEntity.class, id("rubric-2"));
        assertNotNull(nullable);
        assertEquals(id("rubric-2"), nullable.getIdRubrica());
        assertEquals(id("case-2"), nullable.getIdCaso());
        assertEquals("Rubrica opcionales nulos", nullable.getNombre());
        assertEquals(CREATED, nullable.getFechaCreacion());
        assertNull(nullable.getDescripcion());
        assertNull(nullable.getPuntajeTotal());
        assertNull(nullable.getDefinicionEvaluacion());

        RubricaPreguntaJpaEntity first = em.find(RubricaPreguntaJpaEntity.class, id("association-1"));
        RubricaPreguntaJpaEntity second = em.find(RubricaPreguntaJpaEntity.class, id("association-2"));
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(id("association-1"), first.getIdRubricaPregunta());
        assertEquals(id("association-2"), second.getIdRubricaPregunta());
        assertEquals(new BigDecimal("12.50"), first.getValorPregunta());
        assertEquals(new BigDecimal("7.25"), second.getValorPregunta());
        assertEquals("Criterio de prueba", first.getCriterio());
        assertNull(second.getCriterio());
        assertEquals(id("question-1-1"), first.getIdPregunta());
        assertEquals(id("question-1-2"), second.getIdPregunta());
        for (RubricaPreguntaJpaEntity association : List.of(first, second)) {
            assertEquals(rubric.getIdRubrica(), association.getIdRubrica());
            PreguntaJpaEntity question = em.find(PreguntaJpaEntity.class, association.getIdPregunta());
            assertNotNull(question);
            EscenarioJpaEntity scenario = em.find(EscenarioJpaEntity.class, question.getIdEscenario());
            assertNotNull(scenario);
            assertEquals(rubric.getIdCaso(), scenario.getIdCaso());
            assertNotNull(em.find(CasoJpaEntity.class, scenario.getIdCaso()));
        }
        // No order is claimed: RubricaPregunta has no orden column.
        Set<UUID> associations = em.createQuery(
                "select r from RubricaPreguntaJpaEntity r where r.idRubrica = :rubric",
                RubricaPreguntaJpaEntity.class).setParameter("rubric", rubric.getIdRubrica())
                .getResultList().stream().map(RubricaPreguntaJpaEntity::getIdRubricaPregunta)
                .collect(Collectors.toSet());
        assertEquals(Set.of(first.getIdRubricaPregunta(), second.getIdRubricaPregunta()), associations);
        assertNull(em.find(RubricaJpaEntity.class, id("missing-rubric")));
        assertNull(em.find(RubricaPreguntaJpaEntity.class, id("missing-association")));
    }

    private static void prepareFixtures(JdbcTemplate jdbc) {
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion")
                VALUES ('DocenteRubricaM3', 'Fixture de lectura') RETURNING "idRol"
                """, Integer.class);
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo")
                VALUES (?, ?, ?, ?)
                """, id("teacher"), role, "Docente de prueba", "rubrica.m3@prisaly.test");
        // Both cases stay unlocked; no session or manual lock update is needed.
        for (int i = 1; i <= 2; i++) {
            jdbc.update("""
                    INSERT INTO public."Caso" ("idCaso", "idDocente", "nombre", "tiempoEstimadoSegundos")
                    VALUES (?, ?, ?, ?)
                    """, id("case-" + i), id("teacher"), "Caso " + i, 300);
            jdbc.update("""
                    INSERT INTO public."Escenario" ("idEscenario", "idCaso", "orden") VALUES (?, ?, ?)
                    """, id("scenario-" + i), id("case-" + i), 1);
            for (int j = 1; j <= 3; j++) {
                jdbc.update("""
                        INSERT INTO public."Pregunta" ("idPregunta", "idEscenario", "enunciado", "orden")
                        VALUES (?, ?, ?, ?)
                        """, id("question-" + i + "-" + j), id("scenario-" + i), "Pregunta " + j, j);
            }
        }
        jdbc.update("""
                INSERT INTO public."Rubrica"
                    ("idRubrica", "idCaso", "nombre", "descripcion", "puntajeTotal", "definicionEvaluacion", "fechaCreacion")
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, id("rubric-1"), id("case-1"), "Rubrica de lectura", "Descripcion de prueba",
                new BigDecimal("25.75"), JSON, Timestamp.from(CREATED));
        jdbc.update("""
                INSERT INTO public."Rubrica" ("idRubrica", "idCaso", "nombre", "fechaCreacion")
                VALUES (?, ?, ?, ?)
                """, id("rubric-2"), id("case-2"), "Rubrica opcionales nulos", Timestamp.from(CREATED));
        insertAssociation(jdbc, "association-1", "rubric-1", "question-1-1", new BigDecimal("12.50"), "Criterio de prueba");
        insertAssociation(jdbc, "association-2", "rubric-1", "question-1-2", new BigDecimal("7.25"), null);
    }

    private static void verifySqlConstraints(JdbcTemplate jdbc) {
        assertSqlState("23505", () -> jdbc.update("""
                INSERT INTO public."Rubrica" ("idRubrica", "idCaso", "nombre") VALUES (?, ?, ?)
                """, id("duplicate-rubric"), id("case-1"), "Duplicada"));
        assertSqlState("23505", () -> insertAssociation(jdbc, "duplicate-question", "rubric-1",
                "question-1-1", new BigDecimal("1.00"), null));
        for (BigDecimal invalid : List.of(BigDecimal.ZERO, new BigDecimal("-0.01"))) {
            assertSqlState("23514", () -> insertAssociation(jdbc, "invalid-value", "rubric-1",
                    "question-1-3", invalid, null));
        }
        assertSqlState("23514", () -> insertAssociation(jdbc, "wrong-case", "rubric-1",
                "question-2-1", new BigDecimal("1.00"), null));
        assertSqlState("23502", () -> insertAssociation(jdbc, "null-value", "rubric-1",
                "question-1-3", null, null));
    }

    private static void insertAssociation(JdbcTemplate jdbc, String key, String rubric, String question,
            BigDecimal value, String criterion) {
        jdbc.update("""
                INSERT INTO public."RubricaPregunta"
                    ("idRubricaPregunta", "idRubrica", "idPregunta", "valorPregunta", "criterio")
                VALUES (?, ?, ?, ?, ?)
                """, id(key), id(rubric), id(question), value, criterion);
    }

    private static void assertSqlState(String expected, Runnable action) {
        DataAccessException error = assertThrows(DataAccessException.class, action::run);
        SQLException cause = assertInstanceOf(SQLException.class, error.getMostSpecificCause());
        assertEquals(expected, cause.getSQLState());
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("m3-rubric-read-" + key).getBytes(StandardCharsets.UTF_8));
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
