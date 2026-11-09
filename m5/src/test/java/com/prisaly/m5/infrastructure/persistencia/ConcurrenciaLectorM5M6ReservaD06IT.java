package com.prisaly.m5.infrastructure.persistencia;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionInicioCoordinadoJdbcAdapter;
import com.prisaly.m4.infrastructure.adapter.ProgramacionParticipacionJdbcAdapter;
import com.prisaly.m5.application.acceso.*;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional;
import com.prisaly.m5.infrastructure.evaluacion.FabricaConsultaIntentoEvaluacion;
import com.prisaly.m5.infrastructure.programacion.TitularidadIntentosDesdeM4;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.api.SolicitudEvaluacion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-M5M6-ESTABILIZACION-NOCTURNA, fase C. Test-only, no production change.
 *
 * <p>Real interleavings over PostgreSQL 16.15 between the PR #48 protected reader
 * (REQUIRES_NEW, REPEATABLE_READ, read-only) and the D-06 reservation
 * {@link ProgramacionInicioCoordinadoJdbcAdapter#bloquearYConsultarConvocatoria} (caller write
 * transaction, READ COMMITTED, Sesion then Convocatoria FOR UPDATE). Institutional authorization
 * is a test double (always AUTORIZADO) and is not a production provider.
 */
@SpringBootTest(classes = RepositorioIntentosJpaIT.AppPrueba.class)
@Testcontainers
class ConcurrenciaLectorM5M6ReservaD06IT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.locations", RepositorioIntentosJpaIT::ubicacionFlywayCanonica);
    }

    private static final long LIMITE_S = 10;

    @Autowired JdbcTemplate jdbc;
    @Autowired RepositorioIntentos repositorio;
    @Autowired PlatformTransactionManager tx;
    @Autowired FachadaParticipacionTransaccional fachada;

    CadenaIntentoPrueba cadena;
    ExecutorService hilos;

    @BeforeEach void preparar() {
        cadena = CadenaIntentoPrueba.crear(jdbc);
        fachada.registrarRespuesta(cadena.intento(), cadena.pregunta1(), cadena.opcion1a());
        fachada.finalizarIntento(cadena.intento(), MotivoFinalizacion.ENTREGA_ESTUDIANTE);
        hilos = Executors.newFixedThreadPool(2);
    }

    @AfterEach void cerrar() { hilos.shutdownNow(); }

    /** I1: D-06 holds Sesion+Convocatoria FOR UPDATE; the reader on the same convocatoria completes meanwhile. */
    @Test void lectorNoSeBloqueaMientrasD06RetieneLaReserva() throws Exception {
        var reservada = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var escritura = CompletableFuture.runAsync(() -> escritura().executeWithoutResult(s -> {
            assertTrue(reservar().isPresent());
            reservada.countDown();
            esperar(liberar);
        }), hilos);
        assertTrue(reservada.await(LIMITE_S, TimeUnit.SECONDS));
        assertEquals(List.of("RowShareLock/Sesion", "RowShareLock/Convocatoria"), bloqueosDeFila());

        var lectura = CompletableFuture.supplyAsync(() -> lector().findById(cadena.idIntento()), hilos);
        IntentoEvaluacion dato = lectura.get(LIMITE_S, TimeUnit.SECONDS).orElseThrow();
        assertFalse(escritura.isDone(), "the reader finished while D-06 still held its reservation");
        assertEquals(cadena.caso(), dato.idCaso());
        assertEquals(IntentoEvaluacion.EstadoIntento.FINALIZADO, dato.estado());

        liberar.countDown();
        escritura.get(LIMITE_S, TimeUnit.SECONDS);
        assertEquals(List.of(), bloqueosDeFila());
    }

    /** I2: the reader keeps its REPEATABLE_READ snapshot open; D-06 reserves and commits without waiting. */
    @Test void d06NoEsperaAUnaLecturaAbiertaYLaLecturaConservaSuInstantanea() throws Exception {
        var enLectura = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        var real = new ProgramacionParticipacionJdbcAdapter(jdbc);
        var nombreVisto = new AtomicReference<String>();
        ProgramacionParticipacionPort pausada = new ProgramacionParticipacionPort() {
            public Optional<ContextoProgramacion> consultarSesion(UUID s, UUID e) { return real.consultarSesion(s, e); }
            public Optional<ContextoProgramacion> consultarConvocatoria(UUID c, UUID e) {
                var r = real.consultarConvocatoria(c, e); // snapshot already taken by earlier reads
                enLectura.countDown();
                esperar(continuar);
                nombreVisto.set(jdbc.queryForObject("SELECT \"nombreEspacioAcademico\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                        String.class, cadena.sesion()));
                return r;
            }
        };
        String nombreOriginal = nombreSesion();
        var lectura = CompletableFuture.supplyAsync(() -> lector(pausada).findById(cadena.idIntento()), hilos);
        assertTrue(enLectura.await(LIMITE_S, TimeUnit.SECONDS));

        // D-06 reservation plus a committed change on the reserved Sesion, while the read is open.
        var escritura = CompletableFuture.runAsync(() -> escritura().executeWithoutResult(s -> {
            assertTrue(reservar().isPresent());
            jdbc.update("UPDATE \"Sesion\" SET \"nombreEspacioAcademico\" = ? WHERE \"idSesion\" = ?",
                    nombreOriginal + " (editado)", cadena.sesion());
        }), hilos);
        escritura.get(LIMITE_S, TimeUnit.SECONDS); // must not wait for the reader
        assertEquals(nombreOriginal + " (editado)", nombreSesion());

        continuar.countDown();
        assertEquals(cadena.caso(), lectura.get(LIMITE_S, TimeUnit.SECONDS).orElseThrow().idCaso());
        assertEquals(nombreOriginal, nombreVisto.get(), "REPEATABLE_READ snapshot must not see the later commit");
    }

    /** I3: the reader called inside an active D-06 write transaction leaves it intact. */
    @Test void lectorAnidadoNoAlteraLaTransaccionD06() {
        var despues = new AtomicReference<Map<String, Object>>();
        escritura().executeWithoutResult(s -> {
            assertTrue(reservar().isPresent());
            String pid = jdbc.queryForObject("SELECT pg_backend_pid()::text", String.class);
            assertEquals(cadena.caso(), lector().findById(cadena.idIntento()).orElseThrow().idCaso());
            despues.set(Map.of(
                    "aislamiento", jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class),
                    "soloLectura", TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    "mismaConexion", pid.equals(jdbc.queryForObject("SELECT pg_backend_pid()::text", String.class))));
            // D-06 guards still accept this transaction and the reservation can be repeated.
            assertTrue(reservar().isPresent());
            assertEquals(List.of("RowShareLock/Sesion", "RowShareLock/Convocatoria"), bloqueosDeFilaDe(pid));
        });
        assertEquals(Map.of("aislamiento", "read committed", "soloLectura", false, "mismaConexion", true), despues.get());
    }

    // ---------------------------------------------------------------- helpers

    private TransactionTemplate escritura() { return new TransactionTemplate(tx); } // production default: READ COMMITTED

    private Optional<ContextoProgramacion> reservar() {
        return new ProgramacionInicioCoordinadoJdbcAdapter(jdbc, new ProgramacionParticipacionJdbcAdapter(jdbc))
                .bloquearYConsultarConvocatoria(cadena.convocatoria(), cadena.estudiante());
    }

    private ConsultaIntentoEvaluacionPort lector() { return lector(new ProgramacionParticipacionJdbcAdapter(jdbc)); }

    private ConsultaIntentoEvaluacionPort lector(ProgramacionParticipacionPort programacion) {
        IdentidadEstudianteActual identidad = () -> Optional.of(new IdentificadorEstudiante(cadena.estudiante().toString()));
        var protegida = new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad,
                new TitularidadIntentosDesdeM4(jdbc, new ProgramacionParticipacionJdbcAdapter(jdbc))), fachada);
        return new FabricaConsultaIntentoEvaluacion(s -> DecisionAcceso.AUTORIZADO, protegida, repositorio, identidad,
                programacion, tx).para(new SolicitudEvaluacion(cadena.idIntento(), UUID.randomUUID()));
    }

    private String nombreSesion() {
        return jdbc.queryForObject("SELECT \"nombreEspacioAcademico\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                String.class, cadena.sesion());
    }

    /** Row-level FOR UPDATE locks currently held on this chain's Sesion/Convocatoria tables, from another connection. */
    private static final String BLOQUEOS = """
            SELECT l.mode || '/' || c.relname FROM pg_locks l JOIN pg_class c ON c.oid = l.relation
            WHERE l.locktype = 'relation' AND l.mode = 'RowShareLock' AND c.relname IN ('Sesion','Convocatoria')
            """;
    private static final String ORDEN = " ORDER BY CASE c.relname WHEN 'Sesion' THEN 1 ELSE 2 END";

    /** FOR UPDATE table locks held by other connections (observed from a pooled connection). */
    private List<String> bloqueosDeFila() {
        return jdbc.queryForList(BLOQUEOS + " AND l.pid <> pg_backend_pid()" + ORDEN, String.class)
                .stream().distinct().toList();
    }

    /** FOR UPDATE table locks held by one backend (used inside its own transaction). */
    private List<String> bloqueosDeFilaDe(String pid) {
        return jdbc.queryForList(BLOQUEOS + " AND l.pid = ?" + ORDEN, String.class, Integer.valueOf(pid))
                .stream().distinct().toList();
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(LIMITE_S, TimeUnit.SECONDS)) { throw new IllegalStateException("timeout"); }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
