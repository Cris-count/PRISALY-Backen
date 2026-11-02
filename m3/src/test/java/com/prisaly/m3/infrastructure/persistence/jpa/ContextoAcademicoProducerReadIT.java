package com.prisaly.m3.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Internal reader + package mapper only; deliberately no producer bean or access credential. */
class ContextoAcademicoProducerReadIT {
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql", "V003__bd0052_integridad.sql",
            "V004__bd0052_integridad_intentos.sql", "V005__bd0052_cierre_integridad.sql",
            "V006__bd0052_proteccion_relaciones.sql", "V007__fase3_politicas_aprobadas.sql",
            "V008__bloqueo_caso_al_reasignar_sesion.sql", "V009__rf09_solo_casos_publicados.sql");
    private static PostgreSQLContainer postgres;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static JdbcTemplate jdbc;
    private static CasoJpaAggregateReader reader;
    private static CasoPersistenceAdapter inserts;

    @BeforeAll
    static void prepare() throws Exception {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path migrations = null;
        for (Path candidate : List.of(cwd.resolve("app/src/main/resources/db/migration"),
                cwd.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (MIGRATIONS.stream().allMatch(n -> Files.isRegularFile(candidate.resolve(n)))) {
                migrations = candidate; break;
            }
        }
        assertNotNull(migrations, "Official V001-V009 are required");
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (var c = datasource.getConnection(); var s = c.createStatement()) {
            for (String migration : MIGRATIONS) {
                s.execute(Files.readString(migrations.resolve(migration), StandardCharsets.UTF_8));
            }
        }
        jdbc = new JdbcTemplate(datasource);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(datasource);
        factory.setPackagesToScan(CasoJpaAggregateReader.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                "hibernate.globally_quoted_identifiers", true, "hibernate.jdbc.time_zone", "UTC"));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        assertNotNull(emf);
        reader = new CasoJpaAggregateReader(emf);
        inserts = new CasoPersistenceAdapter(emf, reader, new JpaTransactionManager(emf));
    }

    @AfterAll
    static void close() {
        try { if (factory != null) { factory.destroy(); } }
        finally { if (postgres != null) { postgres.stop(); } }
    }

    @Test
    void readsSixOfficialTablesThenMapsInternallyWithoutExposingAProducer() {
        var fixture = ContextoAcademicoProducerMapperTest.full();
        seedOwner(fixture.caso().idDocente());
        inserts.insert(CasoPersistenceMapper.toDomain(fixture));
        var stored = reader.findById(fixture.caso().idCaso()).orElseThrow();
        var result = ContextoAcademicoProducerMapper.map(stored);
        assertEquals(stored.caso().idCaso(), result.idCaso());
        assertEquals(stored.caso().fechaCreacion(), result.fechaCreacion());
        assertFalse(result.escenarios().getFirst().activo());
        var question = result.escenarios().getFirst().preguntas().getFirst();
        assertFalse(question.visible()); assertTrue(question.opciones().getFirst().esCorrecta());
        assertEquals(stored.opciones().getFirst().idOpcion(), question.opciones().getFirst().idOpcion());
        assertEquals(0, fixture.rubrica().puntajeTotal().compareTo(result.rubrica().puntajeTotal()));
        var association = result.rubrica().preguntas().iterator().next();
        assertEquals(question.idPregunta(), association.idPregunta());
        assertEquals(0, fixture.rubricaPreguntas().iterator().next().valorPregunta().compareTo(association.valorPregunta()));
        assertNull(association.criterio());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)", Boolean.class,
                fixture.rubrica().definicionEvaluacion(), result.rubrica().definicionEvaluacion()));
        assertEquals(stored, reader.findById(result.idCaso()).orElseThrow(), "Pure mapping writes nothing");
    }

    @Test
    void absentCaseDiffersFromPersistedCaseWithEmptyStructure() {
        assertTrue(reader.findById(UUID.randomUUID()).isEmpty());
        var source = ContextoAcademicoProducerMapperTest.full();
        var r = source.caso();
        UUID id = UUID.randomUUID();
        var bare = new CasoJpaAggregateSnapshot(new CasoJpaAggregateSnapshot.CasoRow(id, r.idDocente(), r.nombre(),
                null, null, r.tiempoEstimadoSegundos(), r.estado(), true, null, r.fechaCreacion(), null),
                List.of(), List.of(), List.of(), null, Set.of());
        seedOwner(r.idDocente());
        inserts.insert(CasoPersistenceMapper.toDomain(bare));
        var result = ContextoAcademicoProducerMapper.map(reader.findById(id).orElseThrow());
        assertTrue(result.escenarios().isEmpty()); assertNull(result.rubrica());
        assertNull(result.historia()); assertNull(result.desarrolloSituacional());
        assertNull(result.fechaActualizacion()); assertNull(result.fechaBloqueoAcademico());
    }

    private static void seedOwner(UUID owner) {
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion") VALUES ('M3Producer','Fixture 08A')
                ON CONFLICT ("nombre") DO UPDATE SET "descripcion" = EXCLUDED."descripcion" RETURNING "idRol"
                """, Integer.class);
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo") VALUES (?, ?, 'Fixture', ?)
                ON CONFLICT ("idUsuario") DO NOTHING
                """, owner, role, owner + "@prisaly.test");
    }
}
