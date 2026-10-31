package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.TestM4Application;
import com.prisaly.m4.api.*;
import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.*;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.CasoAcademicoBd0052Adapter;
import com.prisaly.m4.infrastructure.adapter.IdentidadStubAdapter;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** JPA + esquema oficial + RF09/F303 reales. M1 y entrega M7 son fixtures explícitas. */
@SpringBootTest(classes = TestM4Application.class)
class SesionAcademicaPostgreSqlIT {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    static String location() {
        return "filesystem:"
                + java.nio.file.Path.of(
                                System.getProperty(
                                        "m4.baseline.migrations",
                                        "../app/src/main/resources/db/migration"))
                        .toAbsolutePath();
    }

    @BeforeAll
    static void iniciar() {
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(location())
                .load()
                .migrate();
    }

    @AfterAll
    static void terminar() {
        postgres.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.locations", SesionAcademicaPostgreSqlIT::location);
    }

    @Autowired SesionAcademicaService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired IdentidadStubAdapter identidad;
    @Autowired CasoAcademicoPort casos;
    UUID docente, caso, estudiante;
    final Instant inicio = Instant.parse("2030-11-05T14:00:00Z"), fin = inicio.plusSeconds(3600);

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE \"Rol\" RESTART IDENTITY CASCADE");
        identidad.limpiar();
        docente = UUID.randomUUID();
        caso = UUID.randomUUID();
        estudiante = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES"
                    + " (1,'Fixture','Prueba')");
        usuario(docente, "docente@example.invalid");
        UUID alumno = UUID.randomUUID();
        usuario(alumno, "alumno@example.invalid");
        jdbc.update(
                "INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)",
                estudiante,
                alumno);
        caso(caso, "Publicado", true);
    }

    void usuario(UUID id, String correo) {
        jdbc.update(
                "INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES"
                    + " (?,1,'Fixture',?)",
                id,
                correo);
    }

    void caso(UUID id, String estado, boolean activo) {
        jdbc.update(
                "INSERT INTO \"Caso\""
                    + " (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\",\"activo\")"
                    + " VALUES (?,?,'Caso',60,?,?)",
                id,
                docente,
                estado,
                activo);
    }

    SesionAcademica crear() {
        return service.crearSesion(docente, caso, "Espacio", "Mensaje", inicio, fin, 1800);
    }

    int cantidad(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM public.\"" + tabla + "\"", Integer.class);
    }

    Object marca(UUID id) {
        return jdbc.queryForObject(
                "SELECT \"fechaBloqueoAcademico\" FROM \"Caso\" WHERE \"idCaso\"=?",
                Object.class,
                id);
    }

    @Test
    void esquemaHibernateYFlywaySonOficiales() {
        assertInstanceOf(CasoAcademicoBd0052Adapter.class, casos);
        M4SchemaAutoConfiguration.verificar(jdbc);
        assertEquals(
                9,
                jdbc.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        assertEquals(
                21,
                jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"
                            + " AND table_type='BASE TABLE' AND"
                            + " table_name<>'flyway_schema_history'",
                        Integer.class));
    }

    @Test
    void creaConsultaYExponeExactamenteLaMismaSesionOficial() {
        var s = crear();
        assertEquals(s.getId(), service.consultarSesion(s.getId()).getId());
        assertEquals(1, cantidad("Sesion"));
        assertEquals(
                "SinIniciar",
                jdbc.queryForObject(
                        "SELECT \"estado\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                        String.class,
                        s.getId()));
        var c = service.convocarEstudiante(docente, s.getId(), estudiante);
        assertEquals(1, cantidad("Convocatoria"));
        assertEquals(c.getId(), service.consultarConvocatorias(s.getId()).get(0).getId());
        var puerto = new ProgramacionParticipacionJdbcAdapter(jdbc);
        var contexto = puerto.consultarSesion(s.getId(), estudiante).orElseThrow();
        assertEquals(s.getId(), contexto.sesionId());
        assertEquals(c.getId(), contexto.convocatoriaId());
        assertEquals(inicio, contexto.inicioPrevisto());
        assertEquals(java.time.Duration.ofMinutes(30), contexto.tiempoMaximoParticipacion());
        System.out.println(
                "M4 OFICIAL: escritura JPA Sesion/Convocatoria y lectura puerto M5 coherentes; 21"
                    + " tablas");
    }

    @Test
    void rf09BorradorNoPrograma() {
        jdbc.update("UPDATE \"Caso\" SET \"estado\"='Borrador' WHERE \"idCaso\"=?", caso);
        var ex = assertThrows(ReglaNegocioException.class, this::crear);
        assertEquals("M4-CASO-NO-PUBLICADO", ex.getCodigo());
        assertEquals(0, cantidad("Sesion"));
        assertNull(marca(caso));
    }

    @Test
    void rf09InactivoNoPrograma() {
        jdbc.update("UPDATE \"Caso\" SET \"activo\"=false WHERE \"idCaso\"=?", caso);
        assertThrows(ReglaNegocioException.class, this::crear);
        assertEquals(0, cantidad("Sesion"));
    }

    @Test
    void primeraProgramacionActivaF303YProtegeContenido() {
        crear();
        assertNotNull(marca(caso));
        var ex =
                assertThrows(
                        org.springframework.dao.DataIntegrityViolationException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE \"Caso\" SET \"nombre\"='Alterado' WHERE"
                                            + " \"idCaso\"=?",
                                        caso));
        assertEquals("23514", ((SQLException) ex.getMostSpecificCause()).getSQLState());
        System.out.println(
                "M4 OFICIAL RF09/F303: caso Publicado programado, marca real y contenido"
                    + " protegido");
    }

    @Test
    void reasignacionActivaBloqueoDelDestino() {
        var s = crear();
        UUID destino = UUID.randomUUID();
        caso(destino, "Publicado", true);
        service.cambiarCaso(docente, s.getId(), destino);
        assertNotNull(marca(destino));
        assertNotNull(marca(caso));
        assertEquals(destino, service.consultarSesion(s.getId()).getCasoId());
    }

    @Test
    void reasignacionABorradorNoCambiaOrigen() {
        var s = crear();
        UUID destino = UUID.randomUUID();
        caso(destino, "Borrador", true);
        assertThrows(
                ReglaNegocioException.class,
                () -> service.cambiarCaso(docente, s.getId(), destino));
        assertEquals(caso, service.consultarSesion(s.getId()).getCasoId());
        assertNull(marca(destino));
    }

    @Test
    void convocatoriaProtegeCasoInclusoSinParticipacion() {
        var s = crear();
        service.convocarEstudiante(docente, s.getId(), estudiante);
        UUID destino = UUID.randomUUID();
        caso(destino, "Publicado", true);
        var ex =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> service.cambiarCaso(docente, s.getId(), destino));
        assertEquals("M4-SESION-CON-CONVOCATORIAS", ex.getCodigo());
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE \"Sesion\" SET \"idCaso\"=? WHERE \"idSesion\"=?",
                                destino,
                                s.getId()));
    }

    @Test
    void historialRealImpideModificarProgramacion() {
        var s = crear();
        var c = service.convocarEstudiante(docente, s.getId(), estudiante);
        UUID part = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES"
                    + " (?,?)",
                part,
                c.getId());
        jdbc.update(
                "INSERT INTO \"Intento\""
                    + " (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\")"
                    + " VALUES (?,?,1,now(),now()+interval '30 minutes','EnCurso')",
                UUID.randomUUID(),
                part);
        assertThrows(
                ReglaNegocioException.class,
                () -> service.actualizarSesion(docente, s.getId(), "Alterada", null, inicio, fin));
        assertEquals("Espacio", service.consultarSesion(s.getId()).getTitulo());
        assertEquals(1, cantidad("Intento"));
    }

    @Test
    void rollbackRevierteSesionYMarcaEnLaMismaTransaccion() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new TransactionTemplate(txManager)
                                .execute(
                                        status -> {
                                            crear();
                                            assertNotNull(marca(caso));
                                            throw new IllegalStateException("Fallo inducido");
                                        }));
        assertEquals(0, cantidad("Sesion"));
        assertNull(marca(caso));
        System.out.println(
                "M4 OFICIAL rollback: Sesion=0, marca F303 nula desde conexión posterior");
    }

    @Test
    void rollbackDeReasignacionRevierteMarcaDestino() {
        var s = crear();
        UUID destino = UUID.randomUUID();
        caso(destino, "Publicado", true);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new TransactionTemplate(txManager)
                                .execute(
                                        status -> {
                                            service.cambiarCaso(docente, s.getId(), destino);
                                            throw new IllegalStateException("Fallo inducido");
                                        }));
        assertEquals(caso, service.consultarSesion(s.getId()).getCasoId());
        assertNull(marca(destino));
    }

    @Test
    void actualizaSobreMismaFilaSinColumnaVersion() {
        var s = crear();
        var actualizado =
                service.actualizarSesion(docente, s.getId(), "Nuevo", "Otro", inicio, fin);
        assertEquals("Nuevo", actualizado.getTitulo());
        assertEquals(
                "Otro",
                jdbc.queryForObject(
                        "SELECT \"mensajePersonalizado\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                        String.class,
                        s.getId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE"
                            + " table_schema='public' AND table_name='Sesion' AND"
                            + " column_name='version'",
                        Integer.class));
    }

    @Test
    void reasignacionNoDuplicaProgramacionExistente() {
        var s = crear();
        UUID destino = UUID.randomUUID();
        caso(destino, "Publicado", true);
        service.crearSesion(docente, destino, "Destino", null, inicio, fin, 1800);
        var ex =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> service.cambiarCaso(docente, s.getId(), destino));
        assertEquals("M4-SESION-DUPLICADA", ex.getCodigo());
        assertEquals(caso, service.consultarSesion(s.getId()).getCasoId());
    }

    @Test
    void httpCreaSesionOficialConDescripcionCompatible() throws Exception {
        identidad.autenticar(docente);
        var mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new com.prisaly.m4.presentation.controller.SesionController(
                                        service))
                        .setControllerAdvice(
                                new com.prisaly.m4.presentation.controller.M4ExceptionHandler())
                        .build();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/m4/sesiones")
                                .contentType("application/json")
                                .content(
                                        "{\"casoId\":\""
                                                + caso
                                                + "\",\"titulo\":\"HTTP\",\"descripcion\":\"Texto"
                                                + " conservado\",\"fechaInicio\":\""
                                                + inicio
                                                + "\",\"fechaFin\":\""
                                                + fin
                                                + "\",\"tiempoMaximoParticipacionSegundos\":1800}"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isCreated())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.mensajePersonalizado")
                                .value("Texto conservado"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.estado")
                                .value("SinIniciar"));
        assertEquals(
                "Texto conservado",
                jdbc.queryForObject(
                        "SELECT \"mensajePersonalizado\" FROM \"Sesion\"", String.class));
    }

    @Test
    void estadosOficialesMantienenMarcasReales() {
        var s = crear();
        service.cambiarEstado(docente, s.getId(), EstadoSesion.EN_CURSO);
        var real = service.cambiarEstado(docente, s.getId(), EstadoSesion.FINALIZADO);
        assertNotNull(real.getFechaInicioReal());
        assertNotNull(real.getFechaFinReal());
        assertEquals(
                "Finalizado",
                jdbc.queryForObject(
                        "SELECT \"estado\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                        String.class,
                        s.getId()));
        assertThrows(
                RuntimeException.class,
                () -> service.cambiarEstado(docente, s.getId(), EstadoSesion.EN_CURSO));
    }

    @Test
    void docenteAjenoNoModifica() {
        var s = crear();
        assertThrows(
                ReglaNegocioException.class,
                () ->
                        service.actualizarSesion(
                                UUID.randomUUID(), s.getId(), "Ajena", null, inicio, fin));
    }

    @Test
    void permisoDenegadoNoEscribe() {
        identidad.configurarPermiso(docente, "GESTIONAR_SESION", false);
        assertThrows(ReglaNegocioException.class, this::crear);
        assertEquals(0, cantidad("Sesion"));
    }

    @Test
    void convocatoriaDuplicadaNoEscribeDosFilas() {
        var s = crear();
        service.convocarEstudiante(docente, s.getId(), estudiante);
        assertThrows(
                ReglaNegocioException.class,
                () -> service.convocarEstudiante(docente, s.getId(), estudiante));
        assertEquals(1, cantidad("Convocatoria"));
    }

    @Test
    void estudianteInexistenteEsRechazadoPorFK() {
        var s = crear();
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> service.convocarEstudiante(docente, s.getId(), UUID.randomUUID()));
        assertEquals(0, cantidad("Convocatoria"));
    }

    @Test
    void accesoLegadoNuncaAutoriza() {
        crear();
        assertThrows(
                ReglaNegocioException.class,
                () -> service.verificarAccesoSesion(UUID.randomUUID(), estudiante));
    }

    @Test
    void concurrenciaIdenticaSeSerializaSinIndiceInventado() throws Exception {
        int hilos = 10;
        var pool = Executors.newFixedThreadPool(hilos);
        var inicioLatch = new CountDownLatch(1);
        var exito = new AtomicInteger();
        var duplicados = new AtomicInteger();
        var errores = Collections.synchronizedList(new ArrayList<Throwable>());
        var futures = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < hilos; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    try {
                                        inicioLatch.await();
                                        crear();
                                        exito.incrementAndGet();
                                    } catch (ReglaNegocioException ex) {
                                        if ("M4-SESION-DUPLICADA".equals(ex.getCodigo()))
                                            duplicados.incrementAndGet();
                                        else errores.add(ex);
                                    } catch (Exception ex) {
                                        errores.add(ex);
                                    }
                                }));
            inicioLatch.countDown();
            for (var f : futures) f.get(45, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertTrue(errores.isEmpty(), errores.toString());
        assertEquals(1, exito.get());
        assertEquals(9, duplicados.get());
        assertEquals(1, cantidad("Sesion"));
        System.out.println(
                "M4 OFICIAL concurrencia: 1 éxito, 9 duplicados esperados, 0 errores inesperados");
    }

    @Test
    void concurrenciaInicioYConvocatoriaRespetaLockDeSesion() throws Exception {
        var s = crear();
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var inicioFuture =
                    pool.submit(
                            () -> {
                                gate.await();
                                return service.cambiarEstado(
                                        docente, s.getId(), EstadoSesion.EN_CURSO);
                            });
            var convFuture =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    service.convocarEstudiante(docente, s.getId(), estudiante);
                                    return true;
                                } catch (ReglaNegocioException ex) {
                                    assertEquals("M4-CONVOCATORIA-NO-PERMITIDA", ex.getCodigo());
                                    return false;
                                }
                            });
            gate.countDown();
            inicioFuture.get(30, TimeUnit.SECONDS);
            boolean creada = convFuture.get(30, TimeUnit.SECONDS);
            assertEquals(creada ? 1 : 0, cantidad("Convocatoria"));
            assertThrows(
                    ReglaNegocioException.class,
                    () -> service.convocarEstudiante(docente, s.getId(), UUID.randomUUID()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void errorHttpDeActualizacionDuplicadaEs409() throws Exception {
        identidad.autenticar(docente);
        var s = crear();
        var otra =
                service.crearSesion(
                        docente,
                        caso,
                        "Segunda",
                        null,
                        inicio.plusSeconds(86400),
                        fin.plusSeconds(86400),
                        1800);
        var mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                new com.prisaly.m4.presentation.controller.SesionController(
                                        service))
                        .setControllerAdvice(
                                new com.prisaly.m4.presentation.controller.M4ExceptionHandler())
                        .build();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/m4/sesiones/" + otra.getId())
                                .contentType("application/json")
                                .content(
                                        "{\"titulo\":\"Duplicada\",\"fechaInicio\":\""
                                                + inicio
                                                + "\",\"fechaFin\":\""
                                                + fin
                                                + "\"}"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isConflict())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("M4-SESION-DUPLICADA"));
        assertEquals("Segunda", service.consultarSesion(otra.getId()).getTitulo());
    }
}
