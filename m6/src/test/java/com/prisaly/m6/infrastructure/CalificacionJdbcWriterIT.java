package com.prisaly.m6.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m6.domain.model.Calificacion;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import com.prisaly.m6.infrastructure.persistence.jdbc.CalificacionJdbcWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-BE-24H-01, M6: insert-only grade writer over the official V001-V009 in PostgreSQL 16.15. Values are fixtures,
 * not grades: no formula, scale or rounding is decided here.
 */
@Timeout(90)
class CalificacionJdbcWriterIT {
    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;
    private static CalificacionJdbcWriter writer;
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
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
            for (String name : MIGRATIONS) { statement.execute(Files.readString(migrations.resolve(name), StandardCharsets.UTF_8)); }
        }
        jdbc = new JdbcTemplate(datasource);
        Integer teacherRole = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "DocenteWriteM6", "SQL fixture only");
        studentRole = jdbc.queryForObject("INSERT INTO public.\"Rol\" (\"nombre\",\"descripcion\") VALUES (?,?) RETURNING \"idRol\"",
                Integer.class, "EstudianteWriteM6", "SQL fixture only");
        teacher = user(teacherRole);
        writer = new CalificacionJdbcWriter(jdbc);
    }

    @AfterAll
    static void close() { if (postgres != null) { postgres.stop(); } }

    @Test
    void registraUnaCalificacionConLaFechaDeLaBaseYLaVersionDePolitica() {
        var source = chain();
        var resultado = new ResultadoEvaluacion(new BigDecimal("4.25"), "{\"detalle\":[1,2]}");
        Calificacion grabada = writer.registrar(source.attempt(), source.rubric(), resultado, "estrategia-prueba").orElseThrow();
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM public.\"Calificacion\" WHERE \"idIntento\"=?", source.attempt());
        assertEquals(grabada.idCalificacion(), fila.get("idCalificacion"));
        assertEquals(source.rubric(), fila.get("idRubrica"));
        assertEquals(new BigDecimal("4.25"), fila.get("valor"));
        assertEquals("estrategia-prueba", fila.get("versionPolitica"));
        assertEquals(((Timestamp) fila.get("fechaCalculo")).toInstant(), grabada.fechaCalculo());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT \"detalleEvaluacion\" = CAST(? AS jsonb) FROM public.\"Calificacion\" WHERE \"idIntento\"=?",
                Boolean.class, resultado.detalleEvaluacion(), source.attempt()));
    }

    @Test
    void unaSegundaCalificacionDelMismoIntentoNoEscribeNiSobrescribe() {
        var source = chain();
        var primera = writer.registrar(source.attempt(), source.rubric(), new ResultadoEvaluacion(BigDecimal.ONE, "{}"), "a");
        assertTrue(primera.isPresent());
        var segunda = writer.registrar(source.attempt(), source.rubric(), new ResultadoEvaluacion(new BigDecimal("2.00"), "{}"), "b");
        assertTrue(segunda.isEmpty());
        assertEquals(1, filas(source.attempt()));
        assertEquals(BigDecimal.ONE.setScale(2), jdbc.queryForObject(
                "SELECT \"valor\" FROM public.\"Calificacion\" WHERE \"idIntento\"=?", BigDecimal.class, source.attempt()));
    }

    @Test
    void unValorQueRequeririaRedondeoSeRechazaAntesDeEscribir() {
        var source = chain();
        for (String valor : new String[] {"2.345", "1000000.00"}) {
            assertThrows(IllegalArgumentException.class, () -> writer.registrar(source.attempt(), source.rubric(),
                    new ResultadoEvaluacion(new BigDecimal(valor), "{}"), null));
        }
        assertThrows(IllegalArgumentException.class, () -> writer.registrar(source.attempt(), source.rubric(),
                new ResultadoEvaluacion(BigDecimal.ONE, "{}"), "x".repeat(51)));
        assertEquals(0, filas(source.attempt()));
    }

    @Test
    void laRubricaDeOtroCasoLaRechazaElTriggerOficialV003() {
        var first = chain(); var second = chain();
        var error = assertThrows(DataAccessException.class, () -> writer.registrar(first.attempt(), second.rubric(),
                new ResultadoEvaluacion(BigDecimal.ONE, "{}"), null));
        assertEquals("23514", ((SQLException) error.getMostSpecificCause()).getSQLState());
        assertEquals(0, filas(first.attempt()));
    }

    @Test
    void evaluacionesConcurrentesDelMismoIntentoDejanUnaSolaCalificacion() throws Exception {
        var source = chain();
        var pool = Executors.newFixedThreadPool(8);
        var salida = new CountDownLatch(1);
        List<Future<Optional<Calificacion>>> tareas = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                String version = "concurrente-" + i;
                tareas.add(pool.submit(() -> { salida.await();
                    return writer.registrar(source.attempt(), source.rubric(), new ResultadoEvaluacion(BigDecimal.ONE, "{}"), version); }));
            }
            salida.countDown();
            int presentes = 0;
            for (var t : tareas) { if (t.get().isPresent()) { presentes++; } }
            assertEquals(1, presentes);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, filas(source.attempt()));
    }

    private record Chain(UUID attempt, UUID rubric) { }

    private static int filas(UUID attempt) {
        return jdbc.queryForObject("SELECT count(*) FROM public.\"Calificacion\" WHERE \"idIntento\"=?", Integer.class, attempt);
    }

    private static Chain chain() {
        UUID caseId = UUID.randomUUID(), rubric = UUID.randomUUID(), student = UUID.randomUUID();
        UUID session = UUID.randomUUID(), invitation = UUID.randomUUID(), participation = UUID.randomUUID(), attempt = UUID.randomUUID();
        jdbc.update("INSERT INTO public.\"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,?,?)",
                caseId, teacher, "Fixture M6 write", 300, "Publicado");
        jdbc.update("INSERT INTO public.\"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\") VALUES (?,?,?)", rubric, caseId, "Fixture rubric");
        jdbc.update("INSERT INTO public.\"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", student, user(studentRole));
        jdbc.update("""
                INSERT INTO public."Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                    "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,?,?,?,?)
                """, session, caseId, teacher, "Fixture session", Timestamp.from(DATE), Timestamp.from(DATE.plusSeconds(600)), 300);
        jdbc.update("INSERT INTO public.\"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)", invitation, session, student);
        jdbc.update("INSERT INTO public.\"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participation, invitation);
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

    private static Path migrations() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path path : List.of(cwd.resolve("app/src/main/resources/db/migration"), cwd.resolve("../app/src/main/resources/db/migration").normalize())) {
            if (MIGRATIONS.stream().allMatch(name -> Files.isRegularFile(path.resolve(name)))) { return path; }
        }
        throw new IllegalStateException("Official V001-V009 not found from " + cwd);
    }
}
