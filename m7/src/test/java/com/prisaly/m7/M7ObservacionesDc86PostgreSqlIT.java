package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m7.application.PoliticaReintentos;
import com.prisaly.m7.application.ProcesarEnviosPendientesService;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.application.ReclamoEnvioPort;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-86 over PostgreSQL 16.15 and the official schema. OBS-83-M7-02: with {@code prisaly.m7.zona-horaria=America/Bogota}
 * the notice and email show local time while {@code Sesion} keeps the UTC instant. OBS-83-M7-01: an accepted send
 * persists the Message-ID in the existing {@code ultimoError} column (documented behaviour, no schema change).
 */
@SpringBootTest(classes = M7EnvioWorkerPostgreSqlIT.TestApplication.class)
class M7ObservacionesDc86PostgreSqlIT {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.locations",
                () -> "filesystem:" + Path.of("../app/src/main/resources/db/migration").toAbsolutePath());
        r.add("prisaly.m7.zona-horaria", () -> "America/Bogota");
    }

    @Autowired SesionAcademicaService service;
    @Autowired ReclamoEnvioPort reclamo;
    @Autowired JdbcTemplate jdbc;

    UUID docente, estudiante, usuario, caso, sesion;
    /** 15:00 UTC = 10:00 in Bogotá (fixed UTC-05:00, no DST). Far enough in the future for programming rules. */
    final Instant inicio = OffsetDateTime.parse("2031-03-12T15:00:00Z").toInstant();

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE \"Rol\" RESTART IDENTITY CASCADE");
        docente = UUID.randomUUID();
        usuario = UUID.randomUUID();
        estudiante = UUID.randomUUID();
        caso = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (1,'Fixture','Test')");
        for (UUID id : new UUID[] {docente, usuario}) {
            jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,1,'Fixture',?)",
                    id, id + "@example.invalid");
        }
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\")"
                + " VALUES (?,?,'Caso DC86',60,'Publicado')", caso, docente);
        sesion = service.crearSesion(docente, caso, "Espacio DC86", null, inicio, inicio.plusSeconds(7200), 600).getId();
    }

    @Test
    void avisoYCorreoMuestranHoraDeBogotaYLaSesionConservaUtc() {
        service.convocarEstudiante(docente, sesion, estudiante);
        String aviso = jdbc.queryForObject("SELECT \"contenido\" FROM \"Aviso\"", String.class);
        String correo = jdbc.queryForObject("SELECT \"contenido\" FROM \"EnvioCorreo\"", String.class);
        assertEquals(aviso, correo);
        assertTrue(aviso.contains("Inicio: 2031-03-12 10:00 hora America/Bogota (UTC-05:00)."), aviso);
        assertTrue(aviso.contains("Fin: 2031-03-12 12:00 hora America/Bogota (UTC-05:00)."), aviso);
        assertFalse(aviso.contains("15:00") || aviso.contains(" UTC."), aviso);
        // Internally still UTC: the stored instant is unchanged.
        assertEquals(inicio, jdbc.queryForObject("SELECT \"inicioPrevisto\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                OffsetDateTime.class, sesion).toInstant());
    }

    @Test
    void envioAceptadoPersisteElMessageIdComoTrazaDeExito() {
        service.convocarEstudiante(docente, sesion, estudiante);
        ProveedorCorreoPort proveedor = (remitente, destino, asunto, contenido) ->
                new ResultadoEnvioSmtp.Aceptado("<dc86-traza@prisaly.test>");
        var worker = new ProcesarEnviosPendientesService(reclamo, proveedor, PoliticaReintentos.ratificada(),
                "noreply@prisaly.test", 10, Duration.ofSeconds(15));
        assertEquals(1, worker.procesarLote());
        var fila = jdbc.queryForMap("SELECT \"estadoEnvio\",\"numeroIntentos\",\"ultimoError\",\"fechaEnvio\" FROM \"EnvioCorreo\"");
        assertEquals("Enviado", fila.get("estadoEnvio"));
        assertEquals(1, ((Number) fila.get("numeroIntentos")).intValue());
        assertNotNull(fila.get("fechaEnvio"));
        assertEquals("SMTP accepted messageId=<dc86-traza@prisaly.test>", fila.get("ultimoError"),
                "documented in docs/M7-FASE3.md (OBS-83-M7-01): success trace, not an error");
    }
}
