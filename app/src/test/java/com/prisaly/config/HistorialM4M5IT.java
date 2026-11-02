package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

/** App real, contratos M4/M5 y PostgreSQL; solo la identidad docente es fixture. */
@SpringBootTest
@Testcontainers
@Import(HistorialM4M5IT.IdentidadDePrueba.class)
class HistorialM4M5IT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.enabled", () -> "true");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IdentidadDePrueba {
        @Bean
        @Primary
        IdentidadPort docenteFixture() {
            return (id, permiso) -> true;
        }
    }

    @Autowired SesionAcademicaService sesiones;
    @Autowired ParticipacionesPort historial;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired ApplicationContext contexto;
    UUID docente, estudiante, caso, sesion, convocatoria;
    final Instant inicio = Instant.parse("2030-11-05T14:00:00Z");

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE public.\"Rol\" RESTART IDENTITY CASCADE");
        docente = UUID.randomUUID();
        estudiante = UUID.randomUUID();
        caso = UUID.randomUUID();
        UUID usuario = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES"
                        + " (1,'Fixture','Prueba')");
        jdbc.update(
                "INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES"
                    + " (?,1,'Docente','doc@example.invalid'),(?,1,'Alumno','alumno@example.invalid')",
                docente,
                usuario);
        jdbc.update(
                "INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)",
                estudiante,
                usuario);
        jdbc.update(
                "INSERT INTO \"Caso\""
                    + " (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\",\"activo\")"
                    + " VALUES (?,?,'Caso',60,'Publicado',true)",
                caso,
                docente);
        sesion =
                sesiones.crearSesion(
                                docente,
                                caso,
                                "Original",
                                "Mensaje",
                                inicio,
                                inicio.plusSeconds(3600),
                                1800)
                        .getId();
        convocatoria = sesiones.convocarEstudiante(docente, sesion, estudiante).getId();
    }

    void modificar() {
        sesiones.actualizarSesion(
                docente, sesion, "Actualizada", "Nuevo", inicio, inicio.plusSeconds(7200));
    }

    UUID participar() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES"
                        + " (?,?)",
                id,
                convocatoria);
        return id;
    }

    void verificarBloqueo() {
        assertEquals(
                "M4-SESION-CON-PARTICIPACIONES",
                assertThrows(ReglaNegocioException.class, this::modificar).getCodigo());
        assertEquals("Original", sesiones.consultarSesion(sesion).getTitulo());
    }

    @Test
    void proveedorUnicoYConvocatoriaSinHistorialPermiteEditar() {
        assertEquals(1, contexto.getBeansOfType(ParticipacionesPort.class).size());
        assertInstanceOf(
                com.prisaly.m5.infrastructure.programacion.ParticipacionesM4DesdeM5.class,
                historial);
        assertFalse(contexto.containsBean("historialM4M5"));
        assertFalse(contexto.containsBean("participacionesNoDisponibles"));
        modificar();
        assertEquals("Actualizada", sesiones.consultarSesion(sesion).getTitulo());
    }

    @Test
    void sesionSinConvocatoriasPermiteEditar() {
        sesion =
                sesiones.crearSesion(
                                docente,
                                caso,
                                "Otra",
                                "Mensaje",
                                inicio.plusSeconds(86400),
                                inicio.plusSeconds(90000),
                                1800)
                        .getId();
        modificar();
        assertEquals("Actualizada", sesiones.consultarSesion(sesion).getTitulo());
    }

    @Test
    void participacionSinIntentosProtegeHistorial() {
        participar();
        verificarBloqueo();
    }

    @Test
    void historialImpideReasignarCaso() {
        participar();
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> sesiones.cambiarCaso(docente, sesion, UUID.randomUUID()));
        assertEquals("M4-SESION-CON-PARTICIPACIONES", error.getCodigo());
        assertEquals(caso, sesiones.consultarSesion(sesion).getCasoId());
    }

    @Test
    void ausenciaDeProveedorNuncaEquivaleAHistorialVacio() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(
                        org.springframework.boot.autoconfigure.AutoConfigurations.of(
                                com.prisaly.m4.infrastructure.config.M4ProviderAutoConfiguration
                                        .class))
                .run(
                        context -> {
                            assertEquals(
                                    1, context.getBeansOfType(ParticipacionesPort.class).size());
                            assertTrue(context.containsBean("participacionesNoDisponibles"));
                            var error =
                                    assertThrows(
                                            ReglaNegocioException.class,
                                            () ->
                                                    context.getBean(ParticipacionesPort.class)
                                                            .tieneParticipacionesOIntentos(sesion));
                            assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
                        });
    }

    @Test
    void intentoExistenteProtegeHistorial() {
        UUID participacion = participar();
        jdbc.update(
                "INSERT INTO \"Intento\""
                    + " (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\")"
                    + " VALUES (?,?,1,now(),now()+interval '1 hour','EnCurso')",
                UUID.randomUUID(),
                participacion);
        verificarBloqueo();
    }

    @Test
    void retiradaConHistorialSigueProtegida() {
        participar();
        jdbc.update(
                "UPDATE \"Convocatoria\" SET \"fechaRetiro\"=clock_timestamp() WHERE"
                        + " \"idConvocatoria\"=?",
                convocatoria);
        verificarBloqueo();
    }

    @Test
    void historialDeOtraSesionNoBloqueaEsta() {
        participar();
        UUID conHistorial = sesion;
        sesion =
                sesiones.crearSesion(
                                docente,
                                caso,
                                "Otra",
                                "Mensaje",
                                inicio.plusSeconds(86400),
                                inicio.plusSeconds(90000),
                                1800)
                        .getId();
        modificar();
        assertEquals("Original", sesiones.consultarSesion(conHistorial).getTitulo());
    }

    @Test
    void errorSqlDeniegaSinFiltrarDatosYRollbackRecupera() {
        new TransactionTemplate(transacciones)
                .executeWithoutResult(
                        tx -> {
                            jdbc.execute(
                                    "ALTER TABLE \"Participacion\" RENAME TO"
                                            + " \"ParticipacionNoDisponibleIT\"");
                            var error = assertThrows(ReglaNegocioException.class, this::modificar);
                            assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
                            var respuesta = new M4ExceptionHandler().handleReglaNegocio(error);
                            assertEquals(503, respuesta.getStatusCode().value());
                            assertFalse(error.getMessage().contains("SELECT"));
                            assertFalse(error.getMessage().contains("ParticipacionNoDisponibleIT"));
                            tx.setRollbackOnly();
                        });
        assertEquals("Original", sesiones.consultarSesion(sesion).getTitulo());
        modificar();
    }

    @Test
    void consultaFueraDeTransaccionOEnSoloLecturaSeRechaza() {
        assertThrows(
                IllegalStateException.class, () -> historial.tieneParticipacionesOIntentos(sesion));
        var lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        lectura.executeWithoutResult(
                tx ->
                        assertThrows(
                                IllegalStateException.class,
                                () -> historial.tieneParticipacionesOIntentos(sesion)));
    }
}
