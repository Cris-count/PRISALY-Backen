package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;
import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.inicio.ElegibilidadInicio;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.IniciarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
import com.prisaly.m5.infrastructure.persistencia.PersistenciaIntentoException;
import com.prisaly.m5.infrastructure.programacion.CoordinacionInicioDesdeM4;
import com.prisaly.m5.infrastructure.programacion.ParticipacionesM4DesdeM5;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Full app + PostgreSQL: M5 owns {@link ParticipacionesPort} ({@code participacionesM4DesdeM5}).
 * Teacher identity is a test fixture only.
 */
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

    /** D-06: políticas e identidad exclusivamente de prueba; repositorios y reserva reales. */
    FachadaParticipacionTransaccional inicioD06(Consumer<ContextoProgramacion> observar) {
        return inicioD06(observar, Duration.ofMinutes(30));
    }

    FachadaParticipacionTransaccional inicioD06(
            Consumer<ContextoProgramacion> observar, Duration duracionDePrueba) {
        var intentos = contexto.getBean(RepositorioIntentos.class);
        var participaciones = contexto.getBean(RepositorioParticipaciones.class);
        var reserva = contexto.getBean(ProgramacionInicioCoordinadoPort.class);
        var coordinacion =
                new CoordinacionInicioDesdeM4(
                        (c, e) -> {
                            var actual = reserva.bloquearYConsultarConvocatoria(c, e);
                            actual.ifPresent(observar);
                            return actual;
                        },
                        () ->
                                Optional.of(
                                        new com.prisaly.m5.application.acceso
                                                .IdentificadorEstudiante(estudiante.toString())));
        var fachada =
                new ParticipacionFacade(
                        new ConsultarIntentoServicio(intentos),
                        new RegistrarRespuestaServicio(intentos),
                        new ActualizarRespuestaServicio(intentos),
                        new FinalizarIntentoServicio(intentos),
                        new IniciarIntentoServicio(
                                participaciones,
                                intentos,
                                c -> ElegibilidadInicio.Resultado.ELEGIBLE,
                                (c, ahora) -> Optional.of(ahora.plus(duracionDePrueba))));
        return new FachadaParticipacionTransaccional(fachada, transacciones, coordinacion);
    }

    void iniciarD06() {
        inicioD06(c -> {}).iniciarOReanudar(new ReferenciaConvocatoria(convocatoria.toString()));
    }

    void esperar(CountDownLatch latch) {
        try {
            assertTrue(latch.await(20, TimeUnit.SECONDS), "barrera no alcanzada");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    /** Dos transacciones/conexiones independientes; demuestra espera real desde una tercera. */
    void competirD06(Runnable primero, boolean rollbackPrimero, Runnable segundo) throws Exception {
        var listo = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var pidSegundo = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(transacciones)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        primero.run();
                                                        listo.countDown();
                                                        esperar(liberar);
                                                        if (rollbackPrimero) tx.setRollbackOnly();
                                                    }));
            esperar(listo);
            var b =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(transacciones)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        jdbc.execute(
                                                                "SET LOCAL lock_timeout = '15s'");
                                                        pidSegundo.set(
                                                                jdbc.queryForObject(
                                                                        "SELECT pg_backend_pid()",
                                                                        Integer.class));
                                                        segundo.run();
                                                        if (tx.isRollbackOnly())
                                                            tx.setRollbackOnly();
                                                    }));
            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(
                            () ->
                                    pidSegundo.get() != 0
                                            && Boolean.TRUE.equals(
                                                    jdbc.queryForObject(
                                                            "SELECT"
                                                                + " cardinality(pg_blocking_pids(?))"
                                                                + " > 0",
                                                            Boolean.class,
                                                            pidSegundo.get())));
            assertFalse(b.isDone(), "la segunda operación debe esperar el bloqueo de Sesion");
            liberar.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        } finally {
            liberar.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void d06InicioConfirmadoImpideEdicionConcurrente() throws Exception {
        competirD06(this::iniciarD06, false, this::verificarBloqueo);
        assertEquals(
                1, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\"", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Intento\"", Integer.class));
    }

    @Test
    void d06EdicionConfirmadaSeReleeAntesDeIniciar() throws Exception {
        competirD06(
                this::modificar,
                false,
                () ->
                        inicioD06(c -> assertEquals(inicio.plusSeconds(7200), c.finPrevisto()))
                                .iniciarOReanudar(
                                        new ReferenciaConvocatoria(convocatoria.toString())));
        assertEquals("Actualizada", sesiones.consultarSesion(sesion).getTitulo());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Intento\"", Integer.class));
    }

    @Test
    void d06RollbackInicioLiberaEdicionYSinHuerfanos() throws Exception {
        competirD06(this::iniciarD06, true, this::modificar);
        assertEquals("Actualizada", sesiones.consultarSesion(sesion).getTitulo());
        assertEquals(
                0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\"", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Intento\"", Integer.class));
    }

    @Test
    void d06RollbackEdicionNoContaminaContextoDelInicio() throws Exception {
        competirD06(
                this::modificar,
                true,
                () ->
                        inicioD06(c -> assertEquals(inicio.plusSeconds(3600), c.finPrevisto()))
                                .iniciarOReanudar(
                                        new ReferenciaConvocatoria(convocatoria.toString())));
        assertEquals("Original", sesiones.consultarSesion(sesion).getTitulo());
    }

    @Test
    void d06ReasignacionConConvocatoriaNoTrasladaElIntento() throws Exception {
        competirD06(
                this::iniciarD06,
                false,
                () ->
                        assertEquals(
                                "M4-SESION-CON-PARTICIPACIONES",
                                assertThrows(
                                                ReglaNegocioException.class,
                                                () ->
                                                        sesiones.cambiarCaso(
                                                                docente, sesion, UUID.randomUUID()))
                                        .getCodigo()));
        assertEquals(caso, sesiones.consultarSesion(sesion).getCasoId());
    }

    @Test
    void d06EstadoConfirmadoSeReleeBajoReserva() throws Exception {
        competirD06(
                () -> sesiones.cambiarEstado(docente, sesion, EstadoSesion.EN_CURSO),
                false,
                () ->
                        inicioD06(c -> assertEquals("EnCurso", c.estadoSesion()))
                                .iniciarOReanudar(
                                        new ReferenciaConvocatoria(convocatoria.toString())));
    }

    @Test
    void d06ReservaNoAdmiteTransaccionAusenteNiSoloLectura() {
        var reserva = contexto.getBean(ProgramacionInicioCoordinadoPort.class);
        assertThrows(
                IllegalTransactionStateException.class,
                () -> reserva.bloquearYConsultarConvocatoria(convocatoria, estudiante));
        var lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        assertThrows(
                ConsultaProgramacionNoDisponibleException.class,
                () ->
                        lectura.execute(
                                tx ->
                                        reserva.bloquearYConsultarConvocatoria(
                                                convocatoria, estudiante)));
    }

    @Test
    void d06ReservaAjenaNoDevuelveContexto() {
        var reserva = contexto.getBean(ProgramacionInicioCoordinadoPort.class);
        new TransactionTemplate(transacciones)
                .executeWithoutResult(
                        tx ->
                                assertTrue(
                                        reserva.bloquearYConsultarConvocatoria(
                                                        convocatoria, UUID.randomUUID())
                                                .isEmpty()));
    }

    @Test
    void d06ReanudacionConservaIntentoYLimites() {
        var operaciones = inicioD06(c -> {});
        var referencia = new ReferenciaConvocatoria(convocatoria.toString());
        var primero = operaciones.iniciarOReanudar(referencia);
        var segundo = operaciones.iniciarOReanudar(referencia);
        assertEquals(primero.intento(), segundo.intento());
        assertEquals(primero.inicio(), segundo.inicio());
        assertEquals(primero.limiteTemporal(), segundo.limiteTemporal());
        assertTrue(segundo.reanudado());
    }

    @Test
    void d06DosIniciosConcurrentesNoDuplicanHistorial() throws Exception {
        competirD06(this::iniciarD06, false, this::iniciarD06);
        assertEquals(
                1, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\"", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Intento\"", Integer.class));
    }

    @Test
    void d06FalloEntreParticipacionEIntentoRevierteAmbos() {
        var operaciones = inicioD06(c -> {}, Duration.ZERO);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        operaciones.iniciarOReanudar(
                                new ReferenciaConvocatoria(convocatoria.toString())));
        assertEquals(
                0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\"", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Intento\"", Integer.class));
        modificar();
    }

    @Test
    void d06TimeoutAbortaYUnaNuevaTransaccionRecupera() throws Exception {
        var reserva = contexto.getBean(ProgramacionInicioCoordinadoPort.class);
        var listo = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var a =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(transacciones)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        reserva.bloquearYConsultarConvocatoria(
                                                                convocatoria, estudiante);
                                                        listo.countDown();
                                                        esperar(liberar);
                                                    }));
            esperar(listo);
            var error =
                    assertThrows(
                            ConsultaProgramacionNoDisponibleException.class,
                            () ->
                                    new TransactionTemplate(transacciones)
                                            .execute(
                                                    tx -> {
                                                        jdbc.execute(
                                                                "SET LOCAL lock_timeout = '200ms'");
                                                        return reserva
                                                                .bloquearYConsultarConvocatoria(
                                                                        convocatoria, estudiante);
                                                    }));
            assertInstanceOf(DataAccessException.class, error.getCause());
            liberar.countDown();
            a.get(20, TimeUnit.SECONDS);
            iniciarD06();
        } finally {
            liberar.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void d06ReservarOtraSesionNoEsUnBloqueoGlobal() throws Exception {
        UUID otra =
                sesiones.crearSesion(
                                docente,
                                caso,
                                "Otra",
                                null,
                                inicio.plusSeconds(86400),
                                inicio.plusSeconds(90000),
                                1800)
                        .getId();
        UUID otraConvocatoria = sesiones.convocarEstudiante(docente, otra, estudiante).getId();
        var reserva = contexto.getBean(ProgramacionInicioCoordinadoPort.class);
        var pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transacciones)
                    .executeWithoutResult(
                            tx -> {
                                reserva.bloquearYConsultarConvocatoria(convocatoria, estudiante);
                                var otraReserva =
                                        pool.submit(
                                                () ->
                                                        new TransactionTemplate(transacciones)
                                                                .execute(
                                                                        t ->
                                                                                reserva.bloquearYConsultarConvocatoria(
                                                                                                otraConvocatoria,
                                                                                                estudiante)
                                                                                        .isPresent()));
                                try {
                                    assertTrue(otraReserva.get(5, TimeUnit.SECONDS));
                                } catch (Exception ex) {
                                    throw new AssertionError(ex);
                                }
                            });
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void contextoRealDeM4ResuelveLaConvocatoriaYRechazaOtroEstudiante() {
        var programacion = contexto.getBean(ProgramacionParticipacionPort.class);
        var datos = programacion.consultarConvocatoria(convocatoria, estudiante).orElseThrow();
        assertEquals(convocatoria, datos.convocatoriaId());
        assertEquals(estudiante, datos.estudianteId());
        assertEquals(sesion, datos.sesionId());
        var titularidad = contexto.getBean(TitularidadIntentos.class);
        assertEquals(
                Titularidad.PROPIO,
                titularidad.deConvocatoria(
                        new ReferenciaConvocatoria(convocatoria.toString()),
                        new IdentificadorEstudiante(estudiante.toString())));
        assertTrue(programacion.consultarConvocatoria(convocatoria, UUID.randomUUID()).isEmpty());
    }

    @Test
    void proveedorUnicoEsM5YConvocatoriaSinHistorialPermiteEditar() {
        assertEquals(1, contexto.getBeansOfType(ParticipacionesPort.class).size());
        assertTrue(contexto.containsBean("participacionesM4DesdeM5"));
        assertFalse(contexto.containsBean("historialM4M5"));
        assertEquals(ParticipacionesM4DesdeM5.class, AopUtils.getTargetClass(historial));
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
    void errorSqlNoSeTraduceASinHistorialYRollbackRecupera() {
        new TransactionTemplate(transacciones)
                .executeWithoutResult(
                        tx -> {
                            jdbc.execute(
                                    "ALTER TABLE \"Participacion\" RENAME TO"
                                            + " \"ParticipacionNoDisponibleIT\"");
                            var error = assertThrows(ReglaNegocioException.class, this::modificar);
                            assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
                            assertInstanceOf(PersistenciaIntentoException.class, error.getCause());
                            assertEquals(
                                    503,
                                    new M4ExceptionHandler()
                                            .handleReglaNegocio(error)
                                            .getStatusCode()
                                            .value());
                            assertFalse(error.getMessage().contains("SELECT"));
                            assertFalse(error.getMessage().contains("ParticipacionNoDisponibleIT"));
                            tx.setRollbackOnly();
                        });
        assertEquals("Original", sesiones.consultarSesion(sesion).getTitulo());
        modificar();
    }

    @Test
    void consultaFueraDeTransaccionSeRechazaPorMandatory() {
        assertThrows(
                IllegalTransactionStateException.class,
                () -> historial.tieneParticipacionesOIntentos(sesion));
    }
}
