package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.prisaly.m3.infrastructure.persistence.jpa.EscenarioJpaEntity;
import com.prisaly.m3.infrastructure.persistence.jpa.OpcionRespuestaJpaEntity;
import com.prisaly.m3.infrastructure.persistence.jpa.PreguntaJpaEntity;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real read-only JPA queries over the canonical V001-V009 schema. */
class EvaluableContentJpaReadIT {

    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-01-03T04:05:06.654321Z");
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
    void readsAllColumnsNullsAndParentScopedOrder() throws Exception {
        Path migrations = canonicalMigrationDirectory();
        try (var postgres = new PostgreSQLContainer("postgres:16.15")) {
            postgres.start();
            // Same mechanism as CasoJpaReadIT: official scripts, not a substitute
            // schema. This applies their SQL but does not test Flyway history.
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                for (String migration : MIGRATIONS) {
                    statement.execute(Files.readString(migrations.resolve(migration), StandardCharsets.UTF_8));
                }
            }
            var datasource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            prepareFixtures(new JdbcTemplate(datasource));

            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(datasource);
            factory.setPackagesToScan(EscenarioJpaEntity.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.jdbc.time_zone", "UTC"));
            try {
                factory.afterPropertiesSet();
                EntityManager entityManager = factory.getObject().createEntityManager();
                try {
                    entityManager.getTransaction().begin();
                    verifyRead(entityManager);
                    entityManager.getTransaction().commit();
                } finally {
                    if (entityManager.getTransaction().isActive()) {
                        entityManager.getTransaction().rollback();
                    }
                    entityManager.close();
                }
            } finally {
                factory.destroy();
            }
        }
    }

    private static void verifyRead(EntityManager em) {
        List<EscenarioJpaEntity> scenarios = em.createQuery(
                "select e from EscenarioJpaEntity e where e.idCaso = :parent order by e.orden",
                EscenarioJpaEntity.class).setParameter("parent", id("case")).getResultList();
        assertEquals(2, scenarios.size());
        for (int i = 1; i <= 2; i++) {
            String scenarioKey = "scenario-" + i;
            EscenarioJpaEntity scenario = scenarios.get(i - 1);
            assertEquals(id(scenarioKey), scenario.getIdEscenario());
            assertEquals(id("case"), scenario.getIdCaso());
            assertEquals(i, scenario.getOrden());
            assertEquals(i == 2, scenario.isActivo());
            assertEquals(i == 1 ? null : "Titulo", scenario.getTitulo());
            assertEquals(i == 1 ? null : "Contexto", scenario.getContexto());
            assertEquals(i == 1 ? null : "Instrucciones", scenario.getInstrucciones());

            List<PreguntaJpaEntity> questions = em.createQuery(
                    "select p from PreguntaJpaEntity p where p.idEscenario = :parent order by p.orden",
                    PreguntaJpaEntity.class).setParameter("parent", scenario.getIdEscenario()).getResultList();
            assertEquals(2, questions.size());
            for (int j = 1; j <= 2; j++) {
                String questionKey = scenarioKey + "-question-" + j;
                PreguntaJpaEntity question = questions.get(j - 1);
                assertEquals(id(questionKey), question.getIdPregunta());
                assertEquals(scenario.getIdEscenario(), question.getIdEscenario());
                assertEquals("Enunciado " + questionKey, question.getEnunciado());
                assertEquals(j == 1 ? null : "Ayuda", question.getTextoAyuda());
                assertEquals(j == 1 ? "Borrador" : "Lista", question.getEstadoEdicion());
                assertEquals(j == 2, question.isVisible());
                assertEquals(j, question.getOrden());
                assertEquals(CREATED, question.getFechaCreacion());
                assertEquals(j == 1 ? null : UPDATED, question.getFechaActualizacion());

                List<OpcionRespuestaJpaEntity> options = em.createQuery(
                        "select o from OpcionRespuestaJpaEntity o where o.idPregunta = :parent order by o.orden",
                        OpcionRespuestaJpaEntity.class).setParameter("parent", question.getIdPregunta()).getResultList();
                assertEquals(2, options.size());
                for (int k = 1; k <= 2; k++) {
                    String optionKey = questionKey + "-option-" + k;
                    OpcionRespuestaJpaEntity option = options.get(k - 1);
                    assertEquals(id(optionKey), option.getIdOpcion());
                    assertEquals(question.getIdPregunta(), option.getIdPregunta());
                    assertEquals("Respuesta " + optionKey, option.getTexto());
                    assertEquals(k == 2, option.isEsCorrecta());
                    assertEquals(k == 1 ? null : "Explicacion", option.getExplicacion());
                    assertEquals(k, option.getOrden());
                }
            }
        }
        assertNull(em.find(EscenarioJpaEntity.class, id("missing-scenario")));
        assertNull(em.find(PreguntaJpaEntity.class, id("missing-question")));
        assertNull(em.find(OpcionRespuestaJpaEntity.class, id("missing-option")));
    }

    private static void prepareFixtures(JdbcTemplate jdbc) {
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion")
                VALUES ('DocenteContenidoM3', 'Fixture de lectura') RETURNING "idRol"
                """, Integer.class);
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo")
                VALUES (?, ?, ?, ?)
                """, id("teacher"), role, "Docente de prueba", "contenido.m3@prisaly.test");
        // No session is programmed: the case remains legitimately unlocked.
        jdbc.update("""
                INSERT INTO public."Caso"
                    ("idCaso", "idDocente", "nombre", "tiempoEstimadoSegundos", "estado", "activo")
                VALUES (?, ?, ?, ?, ?, ?)
                """, id("case"), id("teacher"), "Caso de lectura", 300, "Borrador", true);
        // Reverse insertion demonstrates that queries explicitly order by SQL orden.
        for (int i = 2; i >= 1; i--) {
            String scenarioKey = "scenario-" + i;
            jdbc.update("""
                    INSERT INTO public."Escenario"
                        ("idEscenario", "idCaso", "titulo", "contexto", "instrucciones", "orden", "activo")
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id(scenarioKey), id("case"), i == 1 ? null : "Titulo",
                    i == 1 ? null : "Contexto", i == 1 ? null : "Instrucciones", i, i == 2);
            for (int j = 2; j >= 1; j--) {
                String questionKey = scenarioKey + "-question-" + j;
                jdbc.update("""
                        INSERT INTO public."Pregunta"
                            ("idPregunta", "idEscenario", "enunciado", "textoAyuda", "estadoEdicion",
                             "visible", "orden", "fechaCreacion", "fechaActualizacion")
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id(questionKey), id(scenarioKey), "Enunciado " + questionKey,
                        j == 1 ? null : "Ayuda", j == 1 ? "Borrador" : "Lista", j == 2, j,
                        Timestamp.from(CREATED), j == 1 ? null : Timestamp.from(UPDATED));
                for (int k = 2; k >= 1; k--) {
                    String optionKey = questionKey + "-option-" + k;
                    jdbc.update("""
                            INSERT INTO public."OpcionRespuesta"
                                ("idOpcion", "idPregunta", "texto", "esCorrecta", "explicacion", "orden")
                            VALUES (?, ?, ?, ?, ?, ?)
                            """, id(optionKey), id(questionKey), "Respuesta " + optionKey,
                            k == 2, k == 1 ? null : "Explicacion", k);
                }
            }
        }
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("m3-evaluable-read-" + key).getBytes(StandardCharsets.UTF_8));
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
