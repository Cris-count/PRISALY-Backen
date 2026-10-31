package com.prisaly.m3.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaEntity;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import jakarta.persistence.EntityManagerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL read test; never uses Hibernate DDL or a substitute schema.
 * Runs via the existing docker-it/Failsafe profile, not the default unit-test phase.
 */
class CasoJpaReadIT {

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
    void readsCompleteCasoRowsIncludingNullsStatesAndLegitimateAcademicLock() throws Exception {
        Path migrations = canonicalMigrationDirectory();
        try (var postgres = new PostgreSQLContainer("postgres:16.15")) {
            postgres.start();
            // M3 has no Flyway dependency: execute the unmodified official scripts,
            // including their PostgreSQL functions and BEGIN/COMMIT, through JDBC.
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
            UUID teacher = prepareTeacher(jdbc);
            Instant created = Instant.parse("2026-01-02T03:04:05.123456Z");
            Instant updated = Instant.parse("2026-01-03T04:05:06.654321Z");
            UUID draft = insertCase(jdbc, teacher, "Borrador", true, null, null, created, null);
            UUID archived = insertCase(jdbc, teacher, "Archivado", false,
                    "Historia con acentos: psicología", "Desarrollo situacional", created, updated);
            UUID published = insertCase(jdbc, teacher, "Publicado", true,
                    "Historia publicada", "Desarrollo publicado", created, updated);

            // V009 accepts this active published case; V007 establishes its lock.
            jdbc.update("""
                    INSERT INTO public."Sesion"
                        ("idSesion", "idCaso", "idDocenteProgramador", "nombreEspacioAcademico",
                         "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos")
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), published, teacher, "Espacio de prueba",
                    Timestamp.from(Instant.parse("2030-01-01T10:00:00Z")),
                    Timestamp.from(Instant.parse("2030-01-01T11:00:00Z")), 300);
            Instant databaseLock = jdbc.queryForObject("""
                    SELECT "fechaBloqueoAcademico" FROM public."Caso" WHERE "idCaso" = ?
                    """, (rs, row) -> rs.getTimestamp(1).toInstant(), published);
            assertNotNull(databaseLock);

            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(DataSource.class, () -> datasource);
                context.register(ReadConfiguration.class);
                context.refresh();
                CasoJpaRepository repository = context.getBean(CasoJpaRepository.class);

                CasoJpaEntity loadedDraft = repository.findById(draft).orElseThrow();
                assertCommonFields(loadedDraft, draft, teacher, "Borrador", created);
                assertTrue(loadedDraft.isActivo());
                assertNull(loadedDraft.getHistoria());
                assertNull(loadedDraft.getDesarrolloSituacional());
                assertNull(loadedDraft.getFechaActualizacion());
                assertNull(loadedDraft.getFechaBloqueoAcademico());

                CasoJpaEntity loadedArchived = repository.findById(archived).orElseThrow();
                assertCommonFields(loadedArchived, archived, teacher, "Archivado", created);
                assertFalse(loadedArchived.isActivo());
                assertEquals("Historia con acentos: psicología", loadedArchived.getHistoria());
                assertEquals("Desarrollo situacional", loadedArchived.getDesarrolloSituacional());
                assertEquals(updated, loadedArchived.getFechaActualizacion());
                assertNull(loadedArchived.getFechaBloqueoAcademico());

                CasoJpaEntity loadedPublished = repository.findById(published).orElseThrow();
                assertCommonFields(loadedPublished, published, teacher, "Publicado", created);
                assertTrue(loadedPublished.isActivo());
                assertEquals("Historia publicada", loadedPublished.getHistoria());
                assertEquals("Desarrollo publicado", loadedPublished.getDesarrolloSituacional());
                assertEquals(updated, loadedPublished.getFechaActualizacion());
                assertEquals(databaseLock, loadedPublished.getFechaBloqueoAcademico());
                assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
            }
        }
    }

    private static void assertCommonFields(CasoJpaEntity row, UUID id, UUID teacher,
            String state, Instant created) {
        assertEquals(id, row.getIdCaso());
        assertEquals(teacher, row.getIdDocente());
        assertEquals("Caso " + state, row.getNombre());
        assertEquals(300, row.getTiempoEstimadoSegundos());
        assertEquals(state, row.getEstado());
        assertEquals(created, row.getFechaCreacion());
    }

    private static UUID prepareTeacher(JdbcTemplate jdbc) {
        Integer role = jdbc.queryForObject("""
                INSERT INTO public."Rol" ("nombre", "descripcion")
                VALUES ('DocenteLecturaM3', 'Rol de fixture para lectura') RETURNING "idRol"
                """, Integer.class);
        UUID teacher = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."Usuario" ("idUsuario", "idRol", "nombre", "correo")
                VALUES (?, ?, ?, ?)
                """, teacher, role, "Docente de prueba", "lectura.m3@prisaly.test");
        return teacher;
    }

    private static UUID insertCase(JdbcTemplate jdbc, UUID teacher, String state, boolean active,
            String history, String development, Instant created, Instant updated) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."Caso"
                    ("idCaso", "idDocente", "nombre", "historia", "desarrolloSituacional",
                     "tiempoEstimadoSegundos", "estado", "activo", "fechaCreacion", "fechaActualizacion")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, teacher, "Caso " + state, history, development, 300, state, active,
                Timestamp.from(created), updated == null ? null : Timestamp.from(updated));
        return id;
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

    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = CasoJpaRepository.class)
    static class ReadConfiguration {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource datasource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(datasource);
            factory.setPackagesToScan(CasoJpaEntity.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
