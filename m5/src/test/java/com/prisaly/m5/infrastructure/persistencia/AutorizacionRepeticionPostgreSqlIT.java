package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.infrastructure.adapter.ResponsableConvocatoriaJdbcAdapter;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionDenegadaException;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionPort.AutorizacionRegistrada;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionVigenteException;
import com.prisaly.m5.application.repeticion.AutorizarRepeticionServicio;
import com.prisaly.m5.application.repeticion.RepeticionNoRatificada;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A4 (DC-CLAUDE-BE-EJECUCION-04): autorización de repetición sobre BD-005.2 real (PostgreSQL 16.15,
 * migraciones canónicas). La repetición productiva sigue desactivada: aquí se ensambla una política
 * habilitada <b>solo de prueba</b>.
 */
@Timeout(120)
@DisplayName("Autorización de repetición en PostgreSQL 16.15")
class AutorizacionRepeticionPostgreSqlIT {
    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;

    private CadenaIntentoPrueba cadena;
    private UUID participacion;
    private AutorizarRepeticionServicio habilitado;

    @BeforeAll
    static void iniciar() {
        postgres = new PostgreSQLContainer("postgres:16.15");
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(RepositorioIntentosJpaIT.ubicacionFlywayCanonica()).load().migrate();
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    @AfterAll
    static void terminar() {
        if (postgres != null) { postgres.stop(); }
    }

    @BeforeEach
    void preparar() {
        cadena = CadenaIntentoPrueba.crear(jdbc);
        participacion = jdbc.queryForObject("SELECT \"idParticipacion\" FROM \"Intento\" WHERE \"idIntento\"=?",
                UUID.class, cadena.idIntento());
        habilitado = servicio(() -> true);
    }

    private AutorizarRepeticionServicio servicio(com.prisaly.m5.application.repeticion.PoliticaRepeticion politica) {
        return new AutorizarRepeticionServicio(politica, new AutorizacionRepeticionJdbc(jdbc),
                new ResponsableConvocatoriaJdbcAdapter(jdbc),
                new com.prisaly.m3.infrastructure.persistence.jdbc.AutoriaCasoJdbcAdapter(jdbc));
    }

    private AutorizacionRegistrada autorizar(AutorizarRepeticionServicio s, UUID docente, Duration vigencia) {
        return tx.execute(estado -> s.autorizar(docente, participacion, "Fixture A4", vigencia));
    }

    private int autorizaciones() {
        return jdbc.queryForObject("SELECT count(*) FROM \"AutorizacionRepeticion\" WHERE \"idParticipacion\"=?",
                Integer.class, participacion);
    }

    private String estado(UUID id) {
        return jdbc.queryForObject("SELECT \"estadoAutorizacion\" FROM \"AutorizacionRepeticion\" WHERE \"idAutorizacion\"=?",
                String.class, id);
    }

    private void finalizarPrimerIntento() {
        jdbc.update("UPDATE \"Intento\" SET \"estado\"='Finalizado', \"fin\"=clock_timestamp(),"
                + " \"motivoFinalizacion\"='ENTREGA_ESTUDIANTE' WHERE \"idIntento\"=?", cadena.idIntento());
    }

    private void insertarIntentoRepetido(UUID autorizacion, int numero) {
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\","
                + "\"limiteTemporal\",\"estado\",\"idAutorizacion\") VALUES (?,?,?,now(),now()+interval '1 hour',"
                + "'EnCurso',?)", UUID.randomUUID(), participacion, numero, autorizacion);
    }

    private UUID otroDocente() {
        UUID id = UUID.randomUUID();
        Integer rol = jdbc.queryForObject("SELECT \"idRol\" FROM \"Usuario\" WHERE \"idUsuario\"=?", Integer.class,
                cadena.docente());
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                id, rol, "Otro docente A4", id + "@prisaly.test");
        return id;
    }

    @Test
    @DisplayName("la política por defecto (PF-08 sin ratificar) no escribe nada")
    void politicaPorDefectoNoEscribe() {
        assertThrows(RepeticionNoHabilitadaException.class,
                () -> autorizar(servicio(new RepeticionNoRatificada()), cadena.docente(), null));
        assertEquals(0, autorizaciones());
    }

    @Test
    @DisplayName("RF-25: el docente creador del caso autoriza con fechas del reloj de PostgreSQL")
    void creadorAutoriza() {
        var antes = jdbc.queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class).toInstant();
        var a = autorizar(habilitado, cadena.docente(), Duration.ofHours(1));
        var despues = jdbc.queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class).toInstant();
        assertEquals("Vigente", estado(a.idAutorizacion()));
        assertEquals(cadena.docente(), jdbc.queryForObject("SELECT \"idDocenteAutorizador\" FROM \"AutorizacionRepeticion\""
                + " WHERE \"idAutorizacion\"=?", UUID.class, a.idAutorizacion()));
        assertFalse(a.fechaAutorizacion().isBefore(antes));
        assertFalse(a.fechaAutorizacion().isAfter(despues));
        assertEquals(a.fechaAutorizacion().plus(Duration.ofHours(1)), a.fechaVencimiento().orElseThrow());
    }

    @Test
    @DisplayName("otro docente no autoriza y no se escribe nada")
    void otroDocenteNoAutoriza() {
        UUID ajeno = otroDocente();
        assertThrows(AutorizacionRepeticionDenegadaException.class, () -> autorizar(habilitado, ajeno, null));
        assertThrows(AutorizacionRepeticionDenegadaException.class,
                () -> autorizar(habilitado, cadena.estudiante(), null));
        assertEquals(0, autorizaciones());
    }

    @Test
    @DisplayName("RF-25: el programador de la sesión distinto del creador del caso es rechazado; el creador autoriza")
    void programadorDistintoDelCreadorRechazadoYCreadorAutoriza() {
        UUID programador = otroDocente();
        jdbc.update("UPDATE \"Sesion\" SET \"idDocenteProgramador\"=? WHERE \"idSesion\"=?", programador, cadena.sesion());
        assertEquals(programador, jdbc.queryForObject("SELECT \"idDocenteProgramador\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                UUID.class, cadena.sesion()));
        assertEquals(cadena.docente(), jdbc.queryForObject("SELECT \"idDocente\" FROM \"Caso\" WHERE \"idCaso\"=?",
                UUID.class, cadena.caso()));

        assertThrows(AutorizacionRepeticionDenegadaException.class, () -> autorizar(habilitado, programador, null));
        assertEquals(0, autorizaciones());

        var a = autorizar(habilitado, cadena.docente(), null);
        assertEquals("Vigente", estado(a.idAutorizacion()));
        assertEquals(cadena.docente(), a.idDocenteAutorizador());
        assertEquals(1, autorizaciones());
    }

    @Test
    @DisplayName("concurrencia: dos autorizaciones simultáneas del creador dejan una sola vigente")
    void autorizacionesSimultaneas() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var salida = new CountDownLatch(1);
        List<Future<Object>> tareas = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                tareas.add(pool.submit(() -> {
                    salida.await();
                    try { return (Object) autorizar(habilitado, cadena.docente(), null); }
                    catch (RuntimeException e) { return e; }
                }));
            }
            salida.countDown();
            List<Object> r = new ArrayList<>();
            for (var t : tareas) { r.add(t.get(30, TimeUnit.SECONDS)); }
            assertEquals(1, r.stream().filter(AutorizacionRegistrada.class::isInstance).count(), r.toString());
            assertEquals(1, r.stream().filter(AutorizacionRepeticionVigenteException.class::isInstance).count(), r.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, autorizaciones());
    }

    @Test
    @DisplayName("consumo único: la base marca Utilizada al insertar el intento repetido y rechaza reutilizarla")
    void consumoUnicoPorLaBase() {
        finalizarPrimerIntento();
        // Sin autorización la base sigue rechazando la repetición (V005).
        var sinAutorizacion = assertThrows(DataAccessException.class, () -> insertarIntentoRepetido(null, 2));
        assertEquals("23514", ((SQLException) sinAutorizacion.getMostSpecificCause()).getSQLState());

        var a = autorizar(habilitado, cadena.docente(), null);
        insertarIntentoRepetido(a.idAutorizacion(), 2);
        assertEquals("Utilizada", estado(a.idAutorizacion()));

        jdbc.update("UPDATE \"Intento\" SET \"estado\"='Finalizado', \"fin\"=clock_timestamp(),"
                + " \"motivoFinalizacion\"='ENTREGA_ESTUDIANTE' WHERE \"idParticipacion\"=? AND \"numeroIntento\"=2",
                participacion);
        assertThrows(DataAccessException.class, () -> insertarIntentoRepetido(a.idAutorizacion(), 3));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM \"Intento\" WHERE \"idParticipacion\"=?",
                Integer.class, participacion));
        // Consumida la anterior, el creador puede emitir otra (cuántas caben es PF-08, no se decide aquí).
        var segunda = autorizar(habilitado, cadena.docente(), null);
        assertEquals("Vigente", estado(segunda.idAutorizacion()));
        assertEquals(2, autorizaciones());
    }

    @Test
    @DisplayName("el adaptador falla cerrado fuera de una transacción de escritura")
    void exigeTransaccion() {
        var adaptador = new AutorizacionRepeticionJdbc(jdbc);
        assertThrows(IllegalStateException.class, () -> adaptador.bloquearParticipacion(participacion));
        assertThrows(IllegalStateException.class, () -> adaptador.registrar(participacion, cadena.docente(), null, null));
        assertEquals(0, autorizaciones());
    }
}
