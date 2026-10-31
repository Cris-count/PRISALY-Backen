package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;

/** Contrato M4 y relaciones SQL M4–M5 reales; no ejecuta servicios de M5. */
@EnabledIfSystemProperty(named = "m4.baseline.migrations", matches = ".+")
class ProgramacionParticipacionPostgreSqlIT {
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    private JdbcTemplate jdbc;
    private ProgramacionParticipacionPort port;
    private UUID sesion;
    private UUID convocatoria;
    private UUID estudiante;
    private UUID usuarioEstudiante;
    private UUID caso;

    @BeforeAll
    static void iniciar() { postgres.start(); }

    @AfterAll
    static void terminar() { postgres.stop(); }

    @BeforeEach
    void preparar() {
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + System.getProperty("m4.baseline.migrations"))
                .cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        port = new ProgramacionParticipacionJdbcAdapter(jdbc);
        UUID docente = UUID.randomUUID();
        usuarioEstudiante = UUID.randomUUID();
        estudiante = UUID.randomUUID();
        sesion = UUID.randomUUID();
        convocatoria = UUID.randomUUID();
        caso = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (1,'Fixture','Prueba de contrato')");
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,1,'Docente','docente@example.invalid')", docente);
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,1,'Estudiante','estudiante@example.invalid')", usuarioEstudiante);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuarioEstudiante);
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,'Caso de contrato',600,'Publicado')", caso, docente);
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                    "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos","estado")
                VALUES (?,?,?,'Contrato M4-M5','2026-11-05 09:00:00-05','2026-11-05 11:00:00-05',1800,'EnCurso')
                """, sesion, caso, docente);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)", convocatoria, sesion, estudiante);
    }

    @Test
    void consultaSesionDevuelveContextoOficialSinEntidadesMutables() {
        var contexto = port.consultarSesion(sesion, estudiante).orElseThrow();
        assertEquals(convocatoria, contexto.convocatoriaId());
        assertEquals(estudiante, contexto.estudianteId());
        assertEquals(sesion, contexto.sesionId());
        assertEquals(caso, contexto.casoId());
        assertEquals("EnCurso", contexto.estadoSesion());
        assertEquals("Autorizado", contexto.estadoAutorizacionConvocatoria());
        assertTrue(contexto.casoPublicadoYActivo());
        assertEquals(Instant.parse("2026-11-05T14:00:00Z"), contexto.inicioPrevisto());
        assertEquals(Instant.parse("2026-11-05T16:00:00Z"), contexto.finPrevisto());
        assertEquals(Duration.ofMinutes(30), contexto.tiempoMaximoParticipacion());
        assertTrue(contexto.fechaInicioReal().isEmpty());
        assertTrue(contexto.fechaFinReal().isEmpty());
        assertEquals(21, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'", Integer.class));
    }

    @Test
    void titularidadRechazaOtroEstudianteYNoConfundeIdUsuario() {
        assertTrue(port.consultarConvocatoria(convocatoria, estudiante).isPresent());
        assertTrue(port.consultarConvocatoria(convocatoria, UUID.randomUUID()).isEmpty());
        assertTrue(port.consultarConvocatoria(convocatoria, usuarioEstudiante).isEmpty());
        assertTrue(port.consultarSesion(sesion, usuarioEstudiante).isEmpty());
    }

    @Test
    void referenciaInexistenteYAjenaNoRevelanContexto() {
        assertTrue(port.consultarSesion(UUID.randomUUID(), estudiante).isEmpty());
        assertTrue(port.consultarConvocatoria(UUID.randomUUID(), estudiante).isEmpty());
        assertTrue(port.consultarSesion(sesion, UUID.randomUUID()).isEmpty());
    }

    @Test
    void consumidorResuelveParticipacionIntentoYUsaSoloPuertoM4() {
        UUID participacion = UUID.randomUUID();
        UUID intento = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participacion, convocatoria);
        jdbc.update("""
                INSERT INTO "Intento" ("idIntento","idParticipacion","numeroIntento","inicio","limiteTemporal","estado")
                VALUES (?,?,1,now(),now()+interval '30 minutes','EnCurso')
                """, intento, participacion);
        // Fixture de consumidor: simula la resolución que debe exponer M5 en su contrato.
        // No se agrega esta consulta a código productivo M4 ni se usa repositorio M4 desde M5.
        UUID convocatoriaDeParticipacion = jdbc.queryForObject("""
                SELECT p."idConvocatoria" FROM "Participacion" p
                JOIN "Intento" i ON i."idParticipacion"=p."idParticipacion" WHERE i."idIntento"=?
                """, UUID.class, intento);
        var contexto = port.consultarConvocatoria(convocatoriaDeParticipacion, estudiante).orElseThrow();
        assertEquals(sesion, contexto.sesionId());
        assertTrue(port.consultarConvocatoria(convocatoriaDeParticipacion, UUID.randomUUID()).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Intento\"", Integer.class));
        System.out.println("Contrato M4-M5 SQL: Intento→Participacion→Convocatoria resueltos; titular correcto visible, ajeno vacío. Servicios M5 no ejecutados.");
    }

    @Test
    void informaRetiroCierreYCasoArchivadoSinConvertirDatosEnPermiso() {
        jdbc.update("UPDATE \"Convocatoria\" SET \"fechaRetiro\"=now(),\"estadoAutorizacion\"='Retirado' WHERE \"idConvocatoria\"=?", convocatoria);
        jdbc.update("UPDATE \"Sesion\" SET \"estado\"='Finalizado',\"fechaFinReal\"=now() WHERE \"idSesion\"=?", sesion);
        jdbc.update("UPDATE \"Caso\" SET \"estado\"='Archivado',\"activo\"=false WHERE \"idCaso\"=?", caso);
        var contexto = port.consultarConvocatoria(convocatoria, estudiante).orElseThrow();
        assertEquals("Finalizado", contexto.estadoSesion());
        assertEquals("Retirado", contexto.estadoAutorizacionConvocatoria());
        assertTrue(contexto.fechaRetiroConvocatoria().isPresent());
        assertTrue(contexto.fechaFinReal().isPresent());
        assertFalse(contexto.casoPublicadoYActivo());
    }

    @Test
    void observadoEnProvieneDelRelojPostgresql() {
        Instant antes = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
        var contexto = port.consultarSesion(sesion, estudiante).orElseThrow();
        Instant despues = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
        assertFalse(contexto.observadoEn().isBefore(antes));
        assertFalse(contexto.observadoEn().isAfter(despues));
    }

    @Test
    void esquemaNoDisponibleFallaSinAutorizarNiUsarTablasPropuestas() {
        jdbc.execute("ALTER TABLE \"Sesion\" RENAME TO \"SesionNoDisponible\"");
        try {
            assertThrows(ConsultaProgramacionNoDisponibleException.class,
                    () -> port.consultarSesion(sesion, estudiante));
        } finally {
            jdbc.execute("ALTER TABLE \"SesionNoDisponible\" RENAME TO \"Sesion\"");
        }
    }
}
