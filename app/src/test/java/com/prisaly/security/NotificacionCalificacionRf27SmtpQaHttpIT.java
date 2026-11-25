package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m7.application.PoliticaReintentos;
import com.prisaly.m7.application.ProcesarEnviosPendientesService;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-96 — RF-27 (DC-92: aviso y correo al liberar, sin duplicados): SMTP failure, retry and idempotency of the grade
 * notification email, over the real APP, PostgreSQL 16.15 with the integral candidate schema and a Mailpit whose SMTP
 * fault injection (chaos) returns real SMTP replies on RCPT TO. The existing M7 worker is driven by hand
 * ({@code procesarLote}); its scheduler is effectively parked (1 h delay) so every claim in this class is explicit.
 * Production code is unchanged: this only proves the ratified behaviour (D4/D5 retry policy) on the RF-27 row.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificacionCalificacionRf27SmtpQaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa(true);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        QA.propiedades(p, true);
        Path base = Path.of("src/main/resources/db").toAbsolutePath();
        p.add("spring.flyway.locations", () -> "filesystem:" + base.resolve("migration") + ",filesystem:"
                + base.resolve("dd02b-propuesta") + ",filesystem:" + base.resolve("m6-propuesta") + ",filesystem:"
                + base.resolve("d05-propuesta"));
        p.add("prisaly.m4.schema-contract", () -> "bd0052-v013-integral");
        p.add("prisaly.m6.evaluacion.enabled", () -> "true");                    // QA only
        p.add("prisaly.m6.liberacion.enabled", () -> "true");                    // QA only
        p.add("prisaly.m7.notificacion-calificacion.enabled", () -> "true");     // QA only (RF-27 candidate)
        p.add("prisaly.m7.worker.enabled", () -> "true");                        // QA only: beans of the real worker
        p.add("prisaly.m7.worker.intervalo-ms", () -> "3600000");                // parked: claims are explicit
        p.add("prisaly.m7.worker.lote", () -> "20");
        p.add("prisaly.m7.worker.lease-segundos", () -> "60");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    @Autowired ProcesarEnviosPendientesService envios;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void sinFallos() throws Exception {
        caos(0, 0);
    }

    @Test
    void falloSmtpTransitorioProgramaReintentoYTrasRecuperarseEntregaUnaSolaVez() throws Exception {
        Liberada l = liberarCalificacion();
        caos(451, 100);

        envios.procesarLote();
        Map<String, Object> e = envio(l.clave());
        assertEquals("Pendiente", e.get("estadoEnvio"), "a transient SMTP failure keeps the email pending");
        assertEquals(1, e.get("numeroIntentos"));
        assertTrue(((String) e.get("ultimoError")).startsWith("451"), String.valueOf(e.get("ultimoError")));
        assertTrue((Boolean) e.get("diferido"), "next retry is scheduled in the future (policy: 1 min after attempt 1)");
        assertEquals(0, buzon(l.correo()), "nothing delivered while SMTP fails");

        envios.procesarLote();
        assertEquals(1, envio(l.clave()).get("numeroIntentos"), "not claimed again before its retry time");

        caos(0, 0);
        vencerReintento(l.clave());
        envios.procesarLote();
        e = envio(l.clave());
        assertEquals("Enviado", e.get("estadoEnvio"));
        assertEquals(2, e.get("numeroIntentos"));
        assertEquals(1, buzon(l.correo()), "delivered once after recovery");

        // Idempotency after delivery: repeated releases and further worker cycles add nothing.
        assertEquals(200, personal("POST", "/v1/evaluacion/intentos/" + l.intento() + "/liberacion", l.token(), null).statusCode());
        vencerReintento(l.clave());
        envios.procesarLote();
        envios.procesarLote();
        assertEquals(1, cuenta("Aviso", "claveEvento", l.clave()));
        assertEquals(1, cuenta("EnvioCorreo", "claveIdempotencia", l.clave()));
        assertEquals(1, buzon(l.correo()), "still exactly one email");
        assertEquals(2, envio(l.clave()).get("numeroIntentos"), "a sent email is never claimed again");
    }

    @Test
    void rechazoPermanenteDelDestinatarioMarcaFallidoSinReintentarYConservaElAviso() throws Exception {
        Liberada l = liberarCalificacion();
        caos(550, 100);

        envios.procesarLote();
        Map<String, Object> e = envio(l.clave());
        assertEquals("Fallido", e.get("estadoEnvio"), "a permanent recipient rejection is final");
        assertEquals(1, e.get("numeroIntentos"));
        assertTrue(((String) e.get("ultimoError")).startsWith("550"), String.valueOf(e.get("ultimoError")));

        caos(0, 0);
        vencerReintento(l.clave());
        envios.procesarLote();
        assertEquals(1, envio(l.clave()).get("numeroIntentos"), "a Fallido email is never retried");
        assertEquals(0, buzon(l.correo()));
        assertEquals(1, cuenta("Aviso", "claveEvento", l.clave()), "the in-app notice does not depend on SMTP");
    }

    @Test
    void fallosTransitoriosHastaAgotarLaPoliticaMarcanFallidoTrasCincoIntentos() throws Exception {
        Liberada l = liberarCalificacion();
        caos(451, 100);

        for (int intento = 1; intento <= PoliticaReintentos.MAX_INTENTOS; intento++) {
            vencerReintento(l.clave());
            envios.procesarLote();
            Map<String, Object> e = envio(l.clave());
            assertEquals(intento, e.get("numeroIntentos"));
            assertEquals(intento < PoliticaReintentos.MAX_INTENTOS ? "Pendiente" : "Fallido", e.get("estadoEnvio"),
                    "attempt " + intento);
        }
        caos(0, 0);
        vencerReintento(l.clave());
        envios.procesarLote();
        assertEquals(PoliticaReintentos.MAX_INTENTOS, envio(l.clave()).get("numeroIntentos"), "no sixth attempt");
        assertEquals(0, buzon(l.correo()));
        assertEquals(1, cuenta("EnvioCorreo", "claveIdempotencia", l.clave()), "retries reuse the same row");
    }

    // ---------------------------------------------------------------------------------------------------------------

    record Liberada(String intento, String clave, String correo, String token) { }

    /** Same flow as NotificacionCalificacionRf27QaHttpIT: student attempt, teacher evaluation, first N2 release. */
    private Liberada liberarCalificacion() throws Exception {
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID docente = c[1], p1 = c[2], p2 = c[3], rubrica = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\",\"definicionEvaluacion\") VALUES (?,?,?,'{}')",
                rubrica, c[0], "Rubrica RF27 SMTP " + rubrica);
        jdbc.update("INSERT INTO \"RubricaPregunta\" (\"idRubricaPregunta\",\"idRubrica\",\"idPregunta\",\"valorPregunta\")"
                + " VALUES (gen_random_uuid(),?,?,6.00),(gen_random_uuid(),?,?,4.00)", rubrica, p1, rubrica, p2);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], docente, true, 1800);
        String correo = "rf27s." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        RecorridoParticipacionQa.convocar(jdbc, sesion, estudiante);
        String cookie = QA.login(http, port, json, worker, jdbc, correo);   // access code goes through Mailpit (no chaos)
        var inicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookie);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asString();
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento
                + "/respuestas/" + p1, "{\"idOpcionSeleccionada\":\"" + c[5] + "\",\"revisionEsperada\":0}", cookie).statusCode());
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookie).statusCode());
        String token = loginPersonal(jdbc.queryForObject("SELECT \"correo\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente));
        assertEquals(201, personal("POST", "/v1/evaluacion/intentos/" + intento, token, "{\"idRubrica\":\"" + rubrica + "\"}").statusCode());
        UUID idCalificacion = jdbc.queryForObject("SELECT \"idCalificacion\" FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid", UUID.class, intento);
        String clave = "calificacion-liberada:" + idCalificacion;
        assertEquals(201, personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", token, null).statusCode());
        assertEquals(1, cuenta("EnvioCorreo", "claveIdempotencia", clave));
        assertEquals("Pendiente", envio(clave).get("estadoEnvio"));
        return new Liberada(intento, clave, correo, token);
    }

    private Map<String, Object> envio(String clave) {
        return jdbc.queryForMap("SELECT \"estadoEnvio\", \"numeroIntentos\", \"ultimoError\","
                + " (\"proximoReintento\" IS NOT NULL AND \"proximoReintento\" > now()) AS diferido"
                + " FROM \"EnvioCorreo\" WHERE \"claveIdempotencia\"=?", clave);
    }

    /** Moves only this row's retry time into the past (DB clock, the same one the claim uses). */
    private void vencerReintento(String clave) {
        jdbc.update("UPDATE \"EnvioCorreo\" SET \"proximoReintento\" = now() - interval '1 second'"
                + " WHERE \"claveIdempotencia\"=? AND \"proximoReintento\" IS NOT NULL", clave);
    }

    private int cuenta(String tabla, String columna, String clave) {
        return jdbc.queryForObject("SELECT count(*) FROM \"" + tabla + "\" WHERE \"" + columna + "\"=?", Integer.class, clave);
    }

    /** Mailpit chaos: reply {@code codigo} to RCPT TO with the given probability (0 = off). */
    private void caos(int codigo, int probabilidad) throws Exception {
        String cuerpo = "{\"Recipient\":{\"ErrorCode\":" + (codigo == 0 ? 451 : codigo) + ",\"Probability\":" + probabilidad + "}}";
        var r = http.send(HttpRequest.newBuilder(URI.create(mailpitApi() + "/api/v1/chaos"))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), "Mailpit chaos API: " + r.body());
    }

    private int buzon(String correo) throws Exception {
        JsonNode r = json.readTree(http.send(HttpRequest.newBuilder(URI.create(mailpitApi() + "/api/v1/search?query="
                + java.net.URLEncoder.encode("to:" + correo + " subject:Calificación", java.nio.charset.StandardCharsets.UTF_8)))
                .GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        return r.get("messages") == null ? 0 : r.get("messages").size();
    }

    private String mailpitApi() {
        return "http://" + QA.mailpit.getHost() + ":" + QA.mailpit.getMappedPort(8025);
    }

    private String loginPersonal(String correo) throws Exception {
        var r = personal("POST", "/v1/personal/autenticar", null,
                "{\"correo\":\"" + correo + "\",\"password\":\"" + RecorridoParticipacionQa.FIXTURE + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asString();
    }

    private HttpResponse<String> personal(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + ruta)).method(metodo,
                cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) { b.header("Content-Type", "application/json"); }
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
