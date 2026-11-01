package com.prisaly.m6.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.application.port.ConsultaRetroalimentacionPort;
import com.prisaly.m6.domain.model.RetroalimentacionGeneral;
import com.prisaly.m6.infrastructure.persistence.jpa.CalificacionReadAdapter;
import com.prisaly.m6.infrastructure.persistence.jpa.RetroalimentacionGeneralJpaEntity;
import com.prisaly.m6.infrastructure.persistence.jpa.RetroalimentacionGeneralJpaRepository;
import com.prisaly.m6.infrastructure.persistence.jpa.RetroalimentacionGeneralReadAdapter;
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

/** INC-06: canonical migrations and JDBC fixtures, no academic or security policy. */
@Timeout(60)
class RetroalimentacionGeneralJpaReadIT {
    private static PostgreSQLContainer postgres;
    private static DriverManagerDataSource datasource;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext context;
    private static ConsultaRetroalimentacionPort reader;
    private static UUID teacher;
    private static int studentRole;
    private static final Instant DATE = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final List<String> MIGRATIONS = List.of(
            "V001__bd0052_estructura.sql", "V002__bd0052_restricciones.sql", "V003__bd0052_integridad.sql",
            "V004__bd0052_integridad_intentos.sql", "V005__bd0052_cierre_integridad.sql",
            "V006__bd0052_proteccion_relaciones.sql", "V007__fase3_politicas_aprobadas.sql",
            "V008__bloqueo_caso_al_reasignar_sesion.sql", "V009__rf09_solo_casos_publicados.sql");

    @BeforeAll
    static void prepare() throws Exception {
        Path migrations = migrations();
        postgres = new PostgreSQLContainer("postgres:16.15"); postgres.start();
        datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
            for (String name : MIGRATIONS) { statement.execute(Files.readString(migrations.resolve(name), StandardCharsets.UTF_8)); }
        }
        jdbc = new JdbcTemplate(datasource);
        Integer role = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "DocenteFeedbackM6", "SQL fixture only");
        studentRole = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "EstudianteFeedbackM6", "SQL fixture only");
        teacher = user(role);
        context = new AnnotationConfigApplicationContext();
        context.register(JpaConfiguration.class, RetroalimentacionGeneralReadAdapter.class, CalificacionReadAdapter.class);
        context.refresh(); reader = context.getBean(ConsultaRetroalimentacionPort.class);
    }

    @AfterAll
    static void close() {
        try { if (context != null) { context.close(); } }
        finally { if (postgres != null) { postgres.stop(); } }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = RetroalimentacionGeneralJpaRepository.class)
    static class JpaConfiguration {
        @Bean
        DataSource dataSource() { return datasource; }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(RetroalimentacionGeneralJpaEntity.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }
        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
    }

    @Test
    void readsBothIdentifiersAndAllFiveFieldsWithoutMixingParticipations() {
        var first = participation(false); var other = participation(false);
        UUID id = feedback(first.id(), " First\n\uD83D\uDE00 feedback ");
        UUID otherId = feedback(other.id(), "Other");
        var expected = new RetroalimentacionGeneral(id, first.id(), teacher, " First\n\uD83D\uDE00 feedback ", DATE);
        assertEquals(expected, reader.findById(id).orElseThrow());
        assertEquals(expected, reader.findByParticipacionId(first.id()).orElseThrow());
        assertEquals(otherId, reader.findByParticipacionId(other.id()).orElseThrow().idRetroalimentacion());
        assertEquals(teacher, reader.findById(otherId).orElseThrow().idDocente());
        assertEquals(expected, reader.findById(id).orElseThrow());
    }

    @Test
    void missingFeedbackIsEmptyAndNullIdentifiersAreInvalid() {
        assertTrue(reader.findById(UUID.randomUUID()).isEmpty());
        assertTrue(reader.findByParticipacionId(UUID.randomUUID()).isEmpty());
        assertTrue(reader.findByParticipacionId(participation(false).id()).isEmpty());
        assertThrows(NullPointerException.class, () -> reader.findById(null));
        assertThrows(NullPointerException.class, () -> reader.findByParticipacionId(null));
    }

    @Test
    void textIsPreservedLiterallyIncludingEmptyWhitespaceLineBreaksAndSupplementaryUnicode() {
        for (String text : List.of("", "   ", " \t\r\n Text\n\uD83D\uDE00 e\u0301 \"quoted\" ", "Long \uD83D\uDE00 ".repeat(2000))) {
            var source = participation(false); UUID id = feedback(source.id(), text);
            assertEquals(text, reader.findById(id).orElseThrow().contenido());
            assertEquals(text, reader.findByParticipacionId(source.id()).orElseThrow().contenido());
        }
    }

    @Test
    void readsPostgresDefaultDateWithoutManufacturingOrLosingMicroseconds() {
        var source = participation(false); UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."RetroalimentacionGeneral" ("idRetroalimentacion","idParticipacion","idDocente","contenido")
                VALUES (?,?,?,?)
                """, id, source.id(), teacher, "Default date fixture");
        var loaded = reader.findById(id).orElseThrow();
        assertNotNull(loaded.fechaRegistro()); assertEquals(0, loaded.fechaRegistro().getNano() % 1_000);
        assertEquals(jdbc.queryForObject("SELECT \"fechaRegistro\" FROM public.\"RetroalimentacionGeneral\" WHERE \"idRetroalimentacion\"=?",
                Timestamp.class, id).toInstant(), loaded.fechaRegistro());
    }

    @Test
    void uniqueParticipationRejectsDuplicateWithoutChangingOriginal() {
        var source = participation(false); UUID id = feedback(source.id(), "Original");
        var original = reader.findById(id).orElseThrow();
        assertSqlState("23505", () -> feedback(source.id(), "Duplicate"));
        assertEquals(original, reader.findByParticipacionId(source.id()).orElseThrow());
    }

    @Test
    void rejectsUnknownParticipationAndTeacherThroughOfficialForeignKeys() {
        var source = participation(false);
        assertSqlState("23503", () -> insert(UUID.randomUUID(), UUID.randomUUID(), teacher, "Text", DATE));
        assertSqlState("23503", () -> insert(UUID.randomUUID(), source.id(), UUID.randomUUID(), "Text", DATE));
        assertTrue(reader.findByParticipacionId(source.id()).isEmpty());
    }

    @Test
    void rejectsNullInEachMandatoryColumnIncludingExplicitNullDate() {
        var source = participation(false); UUID id = UUID.randomUUID();
        for (Runnable operation : List.<Runnable>of(
                () -> insert(null, source.id(), teacher, "Text", DATE),
                () -> insert(id, null, teacher, "Text", DATE),
                () -> insert(id, source.id(), null, "Text", DATE),
                () -> insert(id, source.id(), teacher, null, DATE),
                () -> insert(id, source.id(), teacher, "Text", null))) {
            assertSqlState("23502", operation);
        }
        assertTrue(reader.findById(id).isEmpty());
        assertTrue(reader.findByParticipacionId(source.id()).isEmpty());
    }

    @Test
    void productionApiHasOnlyQueriesAndEntityHasNoPublicMutationApi() {
        assertEquals(Set.of("findById", "findByParticipacionId"), Arrays.stream(ConsultaRetroalimentacionPort.class.getMethods())
                .map(m -> m.getName()).collect(Collectors.toSet()));
        assertEquals(Set.of("findById", "findByIdParticipacion"), Arrays.stream(RetroalimentacionGeneralJpaRepository.class.getMethods())
                .map(m -> m.getName()).collect(Collectors.toSet()));
        assertEquals(2, ConsultaRetroalimentacionPort.class.getMethods().length);
        assertEquals(2, RetroalimentacionGeneralJpaRepository.class.getMethods().length);
        assertTrue(RetroalimentacionGeneralJpaEntity.class.isAnnotationPresent(Immutable.class));
        assertEquals(0, RetroalimentacionGeneralJpaEntity.class.getConstructors().length);
        assertTrue(Arrays.stream(RetroalimentacionGeneralJpaEntity.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).allMatch(m -> m.getName().startsWith("get")));
    }

    @Test
    void failuresArePropagatedAndNullInputNeverReachesRepository() {
        var repository = mock(RetroalimentacionGeneralJpaRepository.class);
        var adapter = new RetroalimentacionGeneralReadAdapter(repository);
        assertThrows(NullPointerException.class, () -> adapter.findById(null));
        assertThrows(NullPointerException.class, () -> adapter.findByParticipacionId(null));
        verifyNoInteractions(repository);
        UUID id = UUID.randomUUID(); var failure = new DataAccessResourceFailureException("Test failure");
        when(repository.findById(id)).thenThrow(failure);
        when(repository.findByIdParticipacion(id)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> adapter.findById(id)));
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> adapter.findByParticipacionId(id)));
    }

    @Test
    void existingGradeReaderWorksAlongsideFeedbackReaderInSameJpaContext() {
        var source = participation(true); UUID attempt = UUID.randomUUID(), grade = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."Intento" ("idIntento","idParticipacion","numeroIntento","inicio","fin","limiteTemporal","estado")
                VALUES (?,?,1,?,?,?,'Finalizado')
                """, attempt, source.id(), Timestamp.from(DATE), Timestamp.from(DATE.plusSeconds(60)), Timestamp.from(DATE.plusSeconds(300)));
        jdbc.update("""
                INSERT INTO public."Calificacion" ("idCalificacion","idIntento","idRubrica","valor","detalleEvaluacion","fechaCalculo")
                VALUES (?,?,?,?,CAST(? AS jsonb),?)
                """, grade, attempt, source.rubric(), new BigDecimal("12.50"), "{\"fixture\":[true,null,1.00]}", Timestamp.from(DATE));
        UUID feedback = feedback(source.id(), "Regression feedback");
        var grades = context.getBean(ConsultaCalificacionPort.class);
        var loaded = grades.findById(grade).orElseThrow();
        assertEquals(grade, loaded.idCalificacion()); assertEquals(attempt, loaded.idIntento());
        assertEquals(source.rubric(), loaded.idRubrica()); assertEquals(new BigDecimal("12.50"), loaded.valor());
        assertEquals(DATE, loaded.fechaCalculo()); assertNull(loaded.versionPolitica());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT CAST(? AS jsonb) = CAST(? AS jsonb)",
                Boolean.class, "{\"fixture\":[true,null,1.00]}", loaded.detalleEvaluacion()));
        assertEquals(loaded, grades.findByIntentoId(attempt).orElseThrow());
        assertEquals(feedback, reader.findByParticipacionId(source.id()).orElseThrow().idRetroalimentacion());
    }

    private record Participation(UUID id, UUID rubric) { }

    private static Participation participation(boolean withRubric) {
        UUID caseId = UUID.randomUUID(), session = UUID.randomUUID(), student = UUID.randomUUID();
        UUID invitation = UUID.randomUUID(), participation = UUID.randomUUID();
        UUID rubric = withRubric ? UUID.randomUUID() : null;
        jdbc.update("INSERT INTO public.\"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,?,?)",
                caseId, teacher, "Feedback SQL fixture", 300, "Publicado");
        if (withRubric) {
            // Insert before programming, so F3-03 is never bypassed.
            jdbc.update("INSERT INTO public.\"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\") VALUES (?,?,?)", rubric, caseId, "Regression fixture rubric");
        }
        jdbc.update("INSERT INTO public.\"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", student, user(studentRole));
        jdbc.update("""
                INSERT INTO public."Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                    "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos") VALUES (?,?,?,?,?,?,?)
                """, session, caseId, teacher, "Fixture", Timestamp.from(DATE), Timestamp.from(DATE.plusSeconds(600)), 300);
        jdbc.update("INSERT INTO public.\"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)", invitation, session, student);
        jdbc.update("INSERT INTO public.\"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participation, invitation);
        return new Participation(participation, rubric);
    }

    private static UUID user(int role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.\"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                id, role, "SQL fixture", id + "@prisaly.test");
        return id;
    }

    private static UUID feedback(UUID participation, String text) {
        UUID id = UUID.randomUUID(); insert(id, participation, teacher, text, DATE); return id;
    }

    private static void insert(UUID id, UUID participation, UUID author, String text, Instant date) {
        jdbc.update("""
                INSERT INTO public."RetroalimentacionGeneral" ("idRetroalimentacion","idParticipacion","idDocente","contenido","fechaRegistro")
                VALUES (?,?,?,?,?)
                """, id, participation, author, text, date == null ? null : Timestamp.from(date));
    }

    private static void assertSqlState(String expected, Runnable operation) {
        var failure = assertThrows(DataAccessException.class, operation::run);
        assertEquals(expected, assertInstanceOf(SQLException.class, failure.getMostSpecificCause()).getSQLState());
    }

    private static Path migrations() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path path : List.of(cwd.resolve("app/src/main/resources/db/migration"), cwd.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (MIGRATIONS.stream().allMatch(name -> Files.isRegularFile(path.resolve(name)))) { return path; }
        }
        throw new IllegalStateException("Official V001-V009 not found from " + cwd);
    }
}
