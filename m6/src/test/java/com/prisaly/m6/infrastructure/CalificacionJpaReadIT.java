package com.prisaly.m6.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.domain.model.Calificacion;
import com.prisaly.m6.infrastructure.persistence.jpa.CalificacionJpaEntity;
import com.prisaly.m6.infrastructure.persistence.jpa.CalificacionJpaRepository;
import com.prisaly.m6.infrastructure.persistence.jpa.CalificacionReadAdapter;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Official SQL and JDBC fixtures only; no academic algorithm or intermodule adapter. */
@Timeout(60)
class CalificacionJpaReadIT {
    private static PostgreSQLContainer postgres;
    private static DriverManagerDataSource datasource;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext context;
    private static ConsultaCalificacionPort reader;
    private static UUID teacher;
    private static int studentRole;
    private static final Instant DATE = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final String JSON = """
            {"text":"fixture", "object":{"flag":true,"missing":null},
             "array":[1,"two",false,null,{"key":"value"}],
             "decimal":12345678901234567890.1234567890123456789}
            """;
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql", "V003__bd0052_integridad.sql",
            "V004__bd0052_integridad_intentos.sql", "V005__bd0052_cierre_integridad.sql",
            "V006__bd0052_proteccion_relaciones.sql", "V007__fase3_politicas_aprobadas.sql",
            "V008__bloqueo_caso_al_reasignar_sesion.sql", "V009__rf09_solo_casos_publicados.sql");

    @BeforeAll
    static void prepare() throws Exception {
        Path migrations = migrations();
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
            for (String name : MIGRATIONS) { statement.execute(Files.readString(migrations.resolve(name), StandardCharsets.UTF_8)); }
        }
        jdbc = new JdbcTemplate(datasource);
        Integer teacherRole = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "DocenteReadM6", "SQL fixture only");
        studentRole = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "EstudianteReadM6", "SQL fixture only");
        teacher = user(teacherRole);
        context = new AnnotationConfigApplicationContext();
        context.register(JpaConfiguration.class, CalificacionReadAdapter.class);
        context.refresh();
        reader = context.getBean(ConsultaCalificacionPort.class);
    }

    @AfterAll
    static void close() {
        try { if (context != null) { context.close(); } }
        finally { if (postgres != null) { postgres.stop(); } }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = CalificacionJpaRepository.class)
    static class JpaConfiguration {
        @Bean
        DataSource dataSource() { return datasource; }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(CalificacionJpaEntity.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
    }

    @Test
    void readsAllColumnsByGradeAndPersistentAttemptUuidWithoutMixingAttempts() {
        var first = chain(); var second = chain();
        UUID grade = insert(first, new BigDecimal("12.50"), JSON, "fixture-not-approved-policy", DATE);
        UUID otherGrade = insert(second, new BigDecimal("7.25"), "{\"other\":true}", null, DATE);
        Calificacion loaded = reader.findById(grade).orElseThrow();
        assertEquals(grade, loaded.idCalificacion()); assertEquals(first.attempt(), loaded.idIntento());
        assertEquals(first.rubric(), loaded.idRubrica()); assertEquals(new BigDecimal("12.50"), loaded.valor());
        assertEquals(DATE, loaded.fechaCalculo()); assertEquals("fixture-not-approved-policy", loaded.versionPolitica());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)",
                Boolean.class, JSON, loaded.detalleEvaluacion()));
        assertEquals(loaded, reader.findByIntentoId(first.attempt()).orElseThrow());
        assertEquals(otherGrade, reader.findByIntentoId(second.attempt()).orElseThrow().idCalificacion());
        assertNull(reader.findById(otherGrade).orElseThrow().versionPolitica());
        assertEquals(loaded, reader.findById(grade).orElseThrow());
    }

    @Test
    void missingGradeIsEmptyAndNullIdentifiersAreRejected() {
        assertTrue(reader.findById(UUID.randomUUID()).isEmpty());
        assertTrue(reader.findByIntentoId(UUID.randomUUID()).isEmpty());
        assertTrue(reader.findByIntentoId(chain().attempt()).isEmpty());
        assertThrows(NullPointerException.class, () -> reader.findById(null));
        assertThrows(NullPointerException.class, () -> reader.findByIntentoId(null));
    }

    @Test
    void readsZeroNegativeAndPhysicalNumericBoundariesExactly() {
        for (String text : List.of("0.00", "-12.50", "999999.99", "-999999.99")) {
            BigDecimal expected = new BigDecimal(text);
            UUID grade = insert(chain(), expected, "{}", null, DATE);
            assertEquals(expected, reader.findById(grade).orElseThrow().valor());
        }
    }

    @Test
    void preservesOfficialDateDefaultAndUnicodePolicyAndDistinguishesJsonNullFromSqlNull() {
        var valid = chain(); String policy = "\uD83D\uDE00".repeat(50);
        UUID grade = insert(valid, BigDecimal.ONE, "null", policy, null);
        var loaded = reader.findById(grade).orElseThrow();
        assertEquals("null", loaded.detalleEvaluacion()); assertEquals(policy, loaded.versionPolitica());
        assertNotNull(loaded.fechaCalculo()); assertEquals(0, loaded.fechaCalculo().getNano() % 1_000);
        assertEquals(jdbc.queryForObject("SELECT \"fechaCalculo\" FROM public.\"Calificacion\" WHERE \"idCalificacion\"=?",
                Timestamp.class, grade).toInstant(), loaded.fechaCalculo());
        var missingJson = chain();
        assertSqlState("23502", () -> insert(missingJson, BigDecimal.ONE, null, null, DATE));
        assertTrue(reader.findByIntentoId(missingJson.attempt()).isEmpty());
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT \"detalleEvaluacion\" = 'null'::jsonb AND \"detalleEvaluacion\" IS NOT NULL FROM public.\"Calificacion\" WHERE \"idCalificacion\"=?",
                Boolean.class, grade));
    }

    @Test
    void uniqueAttemptRejectsSecondGradeWithoutChangingOriginal() {
        var source = chain(); UUID id = insert(source, BigDecimal.ONE, JSON, null, DATE);
        var original = reader.findById(id).orElseThrow();
        assertSqlState("23505", () -> insert(source, new BigDecimal("2.00"), "{}", null, DATE));
        assertEquals(original, reader.findByIntentoId(source.attempt()).orElseThrow());
    }

    @Test
    void foreignKeysAndCaseCorrespondenceRemainEnforcedWithOfficialTriggerPriority() {
        var first = chain(); var second = chain();
        // With one missing FK the V003 case comparison rejects first, before the FK check.
        assertSqlState("23514", () -> insert(new Chain(UUID.randomUUID(), first.rubric()), BigDecimal.ONE, "{}", null, DATE));
        assertSqlState("23514", () -> insert(new Chain(first.attempt(), UUID.randomUUID()), BigDecimal.ONE, "{}", null, DATE));
        assertSqlState("23514", () -> insert(new Chain(first.attempt(), second.rubric()), BigDecimal.ONE, "{}", null, DATE));
        // Both missing: NULL case IS DISTINCT FROM NULL is false, then the real FK rejects.
        assertSqlState("23503", () -> insert(new Chain(UUID.randomUUID(), UUID.randomUUID()), BigDecimal.ONE, "{}", null, DATE));
        for (String constraint : List.of("fk_calificacion_idintento", "fk_calificacion_idrubrica")) {
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("""
                    SELECT count(*) FROM pg_constraint WHERE conrelid='public."Calificacion"'::regclass
                    AND conname=? AND contype='f' AND convalidated AND confdeltype='r'
                    """, Integer.class, constraint));
        }
        assertTrue(reader.findByIntentoId(first.attempt()).isEmpty());
        assertTrue(reader.findByIntentoId(second.attempt()).isEmpty());
    }

    @Test
    void officialV003RejectsUpdateAndDeleteAndPreservesStoredGrade() {
        var source = chain(); UUID id = insert(source, new BigDecimal("12.50"), JSON, null, DATE);
        var original = reader.findById(id).orElseThrow();
        var update = assertSqlState("23514", () -> jdbc.update(
                "UPDATE public.\"Calificacion\" SET \"valor\"=? WHERE \"idCalificacion\"=?", BigDecimal.ZERO, id));
        assertTrue(update.getMessage().contains("Calificaci"));
        assertSqlState("23514", () -> jdbc.update("DELETE FROM public.\"Calificacion\" WHERE \"idCalificacion\"=?", id));
        assertEquals(original, reader.findById(id).orElseThrow());
    }

    @Test
    void productionContractsExposeOnlyQueriesAndEntityIsImmutableWithoutPublicSetters() {
        for (Class<?> type : List.of(ConsultaCalificacionPort.class, CalificacionJpaRepository.class)) {
            assertEquals(2, type.getMethods().length);
        }
        assertEquals(Set.of("findById", "findByIntentoId"), Arrays.stream(ConsultaCalificacionPort.class.getMethods())
                .map(m -> m.getName()).collect(Collectors.toSet()));
        assertEquals(Set.of("findById", "findByIdIntento"), Arrays.stream(CalificacionJpaRepository.class.getMethods())
                .map(m -> m.getName()).collect(Collectors.toSet()));
        assertTrue(CalificacionJpaEntity.class.isAnnotationPresent(Immutable.class));
        assertTrue(Arrays.stream(CalificacionJpaEntity.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).allMatch(m -> m.getName().startsWith("get")));
        assertEquals(0, CalificacionJpaEntity.class.getConstructors().length);
    }

    @Test
    void repositoryFailuresArePropagatedRatherThanReportedAsMissingGrades() {
        var repository = mock(CalificacionJpaRepository.class);
        var adapter = new CalificacionReadAdapter(repository); UUID id = UUID.randomUUID();
        var failure = new DataAccessResourceFailureException("Test database unavailable");
        when(repository.findById(id)).thenThrow(failure);
        when(repository.findByIdIntento(id)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> adapter.findById(id)));
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> adapter.findByIntentoId(id)));
    }

    private record Chain(UUID attempt, UUID rubric) { }

    private static Chain chain() {
        UUID caseId = UUID.randomUUID(), rubric = UUID.randomUUID(), student = UUID.randomUUID();
        UUID session = UUID.randomUUID(), invitation = UUID.randomUUID(), participation = UUID.randomUUID(), attempt = UUID.randomUUID();
        jdbc.update("INSERT INTO public.\"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,?,?)",
                caseId, teacher, "Fixture M6 read", 300, "Publicado");
        jdbc.update("INSERT INTO public.\"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\") VALUES (?,?,?)", rubric, caseId, "Fixture rubric");
        jdbc.update("INSERT INTO public.\"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", student, user(studentRole));
        // Programming activates F3-03 normally; no manual academic lock update.
        jdbc.update("""
                INSERT INTO public."Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                    "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,?,?,?,?)
                """, session, caseId, teacher, "Fixture session", Timestamp.from(DATE), Timestamp.from(DATE.plusSeconds(600)), 300);
        jdbc.update("INSERT INTO public.\"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)", invitation, session, student);
        jdbc.update("INSERT INTO public.\"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participation, invitation);
        // Fresh student/case, first finalized attempt. Nullable motive/default evaluation state are not invented catalogues.
        jdbc.update("""
                INSERT INTO public."Intento" ("idIntento","idParticipacion","numeroIntento","inicio","fin","limiteTemporal","estado")
                VALUES (?,?,1,?,?,?,'Finalizado')
                """, attempt, participation, Timestamp.from(DATE), Timestamp.from(DATE.plusSeconds(60)), Timestamp.from(DATE.plusSeconds(300)));
        return new Chain(attempt, rubric);
    }

    private static UUID user(int role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.\"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                id, role, "Fixture M6", id + "@prisaly.test");
        return id;
    }

    private static UUID insert(Chain source, BigDecimal value, String json, String policy, Instant date) {
        UUID id = UUID.randomUUID();
        if (date == null) {
            jdbc.update("""
                    INSERT INTO public."Calificacion" ("idCalificacion","idIntento","idRubrica","valor","detalleEvaluacion","versionPolitica")
                    VALUES (?,?,?,?,CAST(? AS jsonb),?)
                    """, id, source.attempt(), source.rubric(), value, json, policy);
        } else {
            jdbc.update("""
                    INSERT INTO public."Calificacion" ("idCalificacion","idIntento","idRubrica","valor","detalleEvaluacion","versionPolitica","fechaCalculo")
                    VALUES (?,?,?,?,CAST(? AS jsonb),?,?)
                    """, id, source.attempt(), source.rubric(), value, json, policy, Timestamp.from(date));
        }
        return id;
    }

    private static SQLException assertSqlState(String state, Runnable operation) {
        var failure = assertThrows(DataAccessException.class, operation::run);
        var sql = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals(state, sql.getSQLState()); return sql;
    }

    private static Path migrations() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path path : List.of(cwd.resolve("app/src/main/resources/db/migration"), cwd.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (MIGRATIONS.stream().allMatch(name -> Files.isRegularFile(path.resolve(name)))) { return path; }
        }
        throw new IllegalStateException("Official V001-V009 not found from " + cwd);
    }
}
