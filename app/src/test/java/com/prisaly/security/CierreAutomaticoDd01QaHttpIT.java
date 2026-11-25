package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m4.infrastructure.config.M4CierreAutomaticoConfig;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-92 DD-01 — automatic session closing over the real APP and PostgreSQL 16.15 (DD-02 B + M5 POST switched on ONLY
 * here to prove that a closed session admits no new start). Margin PT1H for the test; the scheduled cycle is pushed
 * far away and the job is invoked explicitly.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CierreAutomaticoDd01QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        QA.propiedades(p, true);
        p.add("prisaly.m4.cierre-automatico.enabled", () -> "true");        // QA only
        p.add("prisaly.m4.cierre-automatico.margen", () -> "PT1H");
        p.add("prisaly.m4.cierre-automatico.intervalo-ms", () -> "3600000");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    @Autowired M4CierreAutomaticoConfig.CierreAutomaticoSesiones cierre;
    final HttpClient http = HttpClient.newHttpClient();

    /** Session in the given state with finPrevisto = now + finDesdeAhora (PostgreSQL interval text). */
    private UUID sesion(UUID caso, UUID docente, String estado, String finDesdeAhora) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\","
                + "\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\") VALUES (?,?,?,'Espacio DD-01',"
                + "now() + (?::interval) - interval '4 hours', now() + (?::interval), 1800)", id, caso, docente, finDesdeAhora, finDesdeAhora);
        if (!"SinIniciar".equals(estado)) {
            jdbc.update("UPDATE \"Sesion\" SET \"estado\"='EnCurso', \"fechaInicioReal\"=\"inicioPrevisto\" WHERE \"idSesion\"=?", id);
        }
        return id;
    }

    private String estado(UUID sesion) {
        return jdbc.queryForObject("SELECT \"estado\" FROM \"Sesion\" WHERE \"idSesion\"=?", String.class, sesion);
    }

    @Test
    void cierraSoloLasSesionesVencidasMasElMargenYLosIntentosAgotadosSinTocarLoVigente() throws Exception {
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID vencida = sesion(c[0], c[1], "EnCurso", "-2 hours");
        UUID enMargen = sesion(c[0], c[1], "EnCurso", "-30 minutes");
        UUID abierta = sesion(c[0], c[1], "EnCurso", "+2 hours");
        UUID sinIniciar = sesion(c[0], c[1], "SinIniciar", "-2 hours");

        // An attempt started inside the vanished session and exhausted (L1 caps it at finPrevisto), still EnCurso.
        String correo = "dd01." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID est = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, vencida, est);
        UUID participacion = UUID.randomUUID(), intento = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\",\"estado\",\"fechaInicio\")"
                + " VALUES (?,?,'EnCurso',now()-interval '150 minutes')", participacion, conv);
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\")"
                + " VALUES (?,?,1,now()-interval '150 minutes',now()-interval '2 hours','EnCurso')", intento, participacion);
        // A vigent attempt in the open session must never be touched.
        String correo2 = "dd01b." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID est2 = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo2);
        UUID conv2 = RecorridoParticipacionQa.convocar(jdbc, abierta, est2);
        UUID part2 = UUID.randomUUID(), vigente = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\",\"estado\",\"fechaInicio\") VALUES (?,?,'EnCurso',now())", part2, conv2);
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\")"
                + " VALUES (?,?,1,now(),now()+interval '30 minutes','EnCurso')", vigente, part2);

        assertEquals(1, cierre.ejecutarAhora(), "only the session past finPrevisto + margin");
        assertEquals("Finalizado", estado(vencida));
        assertNotNull(jdbc.queryForObject("SELECT \"fechaFinReal\" FROM \"Sesion\" WHERE \"idSesion\"=?", Object.class, vencida));
        assertEquals("EnCurso", estado(enMargen), "inside the margin");
        assertEquals("EnCurso", estado(abierta));
        assertEquals("SinIniciar", estado(sinIniciar), "never opened: no rule closes it");
        var cerrado = jdbc.queryForMap("SELECT \"estado\",\"motivoFinalizacion\",\"fin\" FROM \"Intento\" WHERE \"idIntento\"=?", intento);
        assertEquals("Finalizado", cerrado.get("estado"));
        assertEquals("TIEMPO_VENCIDO", cerrado.get("motivoFinalizacion"));
        assertNotNull(cerrado.get("fin"), "dates preserved");
        assertEquals("EnCurso", jdbc.queryForObject("SELECT \"estado\" FROM \"Intento\" WHERE \"idIntento\"=?", String.class, vigente));

        // Idempotent.
        assertEquals(0, cierre.ejecutarAhora());

        // No new start once closed (E1 requires EnCurso), through the real student HTTP route.
        String cookie = QA.login(http, port, json, worker, jdbc, correo);
        var inicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + vencida + "/intentos", null, cookie);
        assertTrue(inicio.statusCode() == 403 || inicio.statusCode() == 409, inicio.statusCode() + " " + inicio.body());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Intento\" WHERE \"idParticipacion\"=?", Integer.class, participacion));
    }

    @Test
    void dosCiclosConcurrentesCierranUnaSolaVez() throws Exception {
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        List<UUID> vencidas = new ArrayList<>();
        for (int i = 0; i < 3; i++) vencidas.add(sesion(c[0], c[1], "EnCurso", "-3 hours"));
        var listo = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> r = new ArrayList<>();
        for (int i = 0; i < 4; i++) r.add(pool.submit(() -> { listo.await(); return cierre.ejecutarAhora(); }));
        listo.countDown();
        int total = 0;
        for (var f : r) total += f.get();
        pool.shutdown();
        assertEquals(3, total, "each expired session closed exactly once across concurrent cycles");
        for (UUID s : vencidas) assertEquals("Finalizado", estado(s));
    }
}
