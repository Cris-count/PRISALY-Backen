package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m7.application.EntregaCodigoAccesoRepositorio.EntregaReclamada;
import com.prisaly.m7.application.EntregaCodigoAccesoRepositorio.EstadoFinal;
import com.prisaly.m7.infrastructure.EntregaCodigoAccesoJdbc;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * N6 (DC-EJEC-NOCTURNA-01): the DD-02 access-code Outbox repository over the optional candidate V016 in a disposable
 * PostgreSQL 16.15. Covers what the service unit tests cannot: SKIP LOCKED claims, leases, terminal states that are
 * never reopened, payload erasure and metadata purge. Not part of the productive Flyway location.
 */
@Testcontainers
class EntregaCodigoAccesoJdbcPostgreSqlIT {
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:16.15");
    JdbcTemplate jdbc;
    EntregaCodigoAccesoJdbc repositorio;
    Instant ahora;

    @BeforeEach
    void migrar() {
        var flyway = M4SchemaFixture.flyway(db, "classpath:db/migration", "classpath:db/dd02b-propuesta");
        flyway.clean();
        flyway.migrate();
        jdbc = M4SchemaFixture.jdbc(db);
        repositorio = new EntregaCodigoAccesoJdbc(jdbc);
        ahora = jdbc.queryForObject("SELECT now()", java.sql.Timestamp.class).toInstant().plusSeconds(1);
    }

    private UUID pendiente(String vigencia) {
        UUID id = UUID.randomUUID(), challenge = UUID.randomUUID();
        jdbc.update("INSERT INTO \"EntregaCodigoAcceso\" (\"idEntrega\",\"challengeId\",\"claveIdempotencia\",\"cargaCifrada\","
                + "\"nonce\",\"kidCifrado\",\"proximoIntento\",\"fechaExpiracion\") VALUES (?,?,?,?,?,?, now() - interval '1 minute', now() + ?::interval)",
                id, challenge, "n6:" + challenge, new byte[] {1, 2}, new byte[12], "kid-qa", vigencia);
        return id;
    }

    private Map<String, Object> fila(UUID id) {
        return jdbc.queryForMap("SELECT \"estado\",\"cargaCifrada\",\"nonce\",\"kidCifrado\",\"reclamadaHasta\",\"intentos\" "
                + "FROM \"EntregaCodigoAcceso\" WHERE \"idEntrega\"=?", id);
    }

    private static boolean sinCarga(Map<String, Object> f) {
        return f.get("cargaCifrada") == null && f.get("nonce") == null && f.get("kidCifrado") == null;
    }

    @Test
    void reclamacionesConcurrentesNuncaEntreganLaMismaFilaADosWorkers() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 24; i++) { ids.add(pendiente("5 minutes")); }
        int workers = 4;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<List<EntregaReclamada>>> tareas = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                tareas.add(pool.submit(() -> { salida.await(); return repositorio.reclamar(24, Duration.ofSeconds(30), ahora); }));
            }
            salida.countDown();
            List<UUID> reclamadas = new ArrayList<>();
            for (var t : tareas) { t.get().forEach(e -> reclamadas.add(e.idEntrega())); }
            assertEquals(reclamadas.size(), new HashSet<>(reclamadas).size(), "a delivery was claimed twice: " + reclamadas);
            // A row locked by one worker is skipped by the others, so every pending row is claimed exactly once.
            assertEquals(new HashSet<>(ids), new HashSet<>(reclamadas));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, repositorio.reclamar(24, Duration.ofSeconds(30), ahora).size(), "nothing is claimable during the lease");
    }

    @Test
    void elLeaseImpideOtraReclamacionHastaVencerYLaFilaConservaSuCarga() {
        UUID id = pendiente("5 minutes");
        assertEquals(1, repositorio.reclamar(5, Duration.ofSeconds(30), ahora).size());
        assertEquals(0, repositorio.reclamar(5, Duration.ofSeconds(30), ahora.plusSeconds(10)).size());
        var recuperada = repositorio.reclamar(5, Duration.ofSeconds(30), ahora.plusSeconds(31));
        assertEquals(1, recuperada.size(), "an abandoned lease must be recoverable");
        assertEquals(id, recuperada.get(0).idEntrega());
        assertEquals("PENDIENTE", fila(id).get("estado"));
        assertTrue(!sinCarga(fila(id)), "a pending delivery keeps its complete payload");
    }

    @Test
    void unEstadoFinalBorraLaCargaYNingunResultadoPosteriorLoReabre() {
        UUID id = pendiente("5 minutes");
        repositorio.reclamar(5, Duration.ofSeconds(30), ahora);
        repositorio.marcarEnviada(id, ahora);
        assertEquals("ENVIADA", fila(id).get("estado"));
        assertTrue(sinCarga(fila(id)));
        // Late or duplicated worker results (stale lease) are no-ops on a final row.
        repositorio.reprogramar(id, 1, ahora.plusSeconds(30), "SMTP_421");
        repositorio.finalizarSinEnvio(id, EstadoFinal.AGOTADA, "SMTP_421", ahora);
        repositorio.marcarEnviada(id, ahora.plusSeconds(1));
        var f = fila(id);
        assertEquals("ENVIADA", f.get("estado"));
        assertEquals(0, f.get("intentos"));
        assertTrue(sinCarga(f));
        assertEquals(0, repositorio.reclamar(5, Duration.ofSeconds(30), ahora.plusSeconds(120)).size());
    }

    @Test
    void cadaEstadoFinalDejaCeroCargasYLaReprogramacionLiberaElLease() {
        UUID transitoria = pendiente("5 minutes");
        repositorio.reclamar(5, Duration.ofSeconds(30), ahora);
        repositorio.reprogramar(transitoria, 1, ahora.plusSeconds(30), "SMTP_" + "4".repeat(60));
        var f = fila(transitoria);
        assertEquals("PENDIENTE", f.get("estado"));
        assertEquals(1, f.get("intentos"));
        assertEquals(null, f.get("reclamadaHasta"));
        assertEquals(40, jdbc.queryForObject("SELECT length(\"codigoError\") FROM \"EntregaCodigoAcceso\" WHERE \"idEntrega\"=?",
                Integer.class, transitoria), "the error code is bounded to the column size");
        assertEquals(0, repositorio.reclamar(5, Duration.ofSeconds(30), ahora.plusSeconds(10)).size(), "not before its next attempt");
        for (EstadoFinal estado : EstadoFinal.values()) {
            UUID id = pendiente("5 minutes");
            repositorio.finalizarSinEnvio(id, estado, "QA", ahora);
            assertEquals(estado.name(), fila(id).get("estado"));
            assertTrue(sinCarga(fila(id)), estado.name());
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"EntregaCodigoAcceso\" WHERE \"estado\"<>'PENDIENTE' "
                + "AND (\"cargaCifrada\" IS NOT NULL OR \"nonce\" IS NOT NULL OR \"kidCifrado\" IS NOT NULL)", Integer.class));
    }

    @Test
    void unCodigoSinTiempoSuficienteSeCancelaSinCargaYLaPurgaConservaLoPendiente() {
        UUID casiVencida = pendiente("30 seconds"), vigente = pendiente("5 minutes");
        assertEquals(1, repositorio.cancelarSinTiempo(ahora.plusSeconds(60), ahora));
        assertEquals("CANCELADA_VENCIDA", fila(casiVencida).get("estado"));
        assertTrue(sinCarga(fila(casiVencida)));
        assertEquals(List.of(vigente), repositorio.reclamar(5, Duration.ofSeconds(30), ahora).stream()
                .map(EntregaReclamada::idEntrega).toList(), "a code that is about to expire is never claimed");
        assertEquals(1, repositorio.purgarMetadatos(ahora.plus(Duration.ofDays(31))));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"EntregaCodigoAcceso\"", Integer.class));
        assertEquals("PENDIENTE", fila(vigente).get("estado"));
    }
}
