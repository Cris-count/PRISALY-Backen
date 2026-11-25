package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;
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
 * DC-BE-CONGELACION-CANDIDATO-88 — RF-27 minimal CANDIDATE (RF27-CONTRATO-MINIMO-88) over the real APP, PostgreSQL 16.15
 * with the integral candidate schema and Mailpit. The M7 email worker is switched on ONLY here: the first N2 release
 * creates exactly one notice and one email for the titular student, delivered once by the existing worker, without
 * the grade value; repeated releases notify nothing more.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificacionCalificacionRf27QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

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
        p.add("prisaly.m7.worker.enabled", () -> "true");                        // QA only: real delivery to Mailpit
        p.add("prisaly.m7.worker.intervalo-ms", () -> "300");
        p.add("prisaly.m7.worker.lote", () -> "5");
        p.add("prisaly.m7.worker.lease-segundos", () -> "60");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void laPrimeraLiberacionNotificaUnaVezAlEstudianteTitularSinRevelarLaNota() throws Exception {
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID docente = c[1], p1 = c[2], p2 = c[3], rubrica = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\",\"definicionEvaluacion\") VALUES (?,?,?,'{}')",
                rubrica, c[0], "Rubrica RF27 " + rubrica);
        jdbc.update("INSERT INTO \"RubricaPregunta\" (\"idRubricaPregunta\",\"idRubrica\",\"idPregunta\",\"valorPregunta\")"
                + " VALUES (gen_random_uuid(),?,?,6.00),(gen_random_uuid(),?,?,4.00)", rubrica, p1, rubrica, p2);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], docente, true, 1800);
        String correo = "rf27." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        RecorridoParticipacionQa.convocar(jdbc, sesion, estudiante);   // SQL fixture: no convocation email is queued
        String cookie = QA.login(http, port, json, worker, jdbc, correo);
        var inicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookie);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asString();
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento
                + "/respuestas/" + p1, "{\"idOpcionSeleccionada\":\"" + c[5] + "\",\"revisionEsperada\":0}", cookie).statusCode());
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookie).statusCode());

        String token = loginPersonal(jdbc.queryForObject("SELECT \"correo\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente));
        assertEquals(201, personal("POST", "/v1/evaluacion/intentos/" + intento, token, "{\"idRubrica\":\"" + rubrica + "\"}").statusCode());
        String valor = jdbc.queryForObject("SELECT \"valor\"::text FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid", String.class, intento);
        UUID idCalificacion = jdbc.queryForObject("SELECT \"idCalificacion\" FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid", UUID.class, intento);
        String clave = "calificacion-liberada:" + idCalificacion;
        assertEquals(0, cuenta("Aviso", clave), "evaluating alone notifies nothing (the grade is not released)");

        // First release: one notice + one email for the titular, in the release transaction.
        assertEquals(201, personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", token, null).statusCode());
        assertEquals(1, cuenta("Aviso", clave));
        assertEquals(1, cuenta("EnvioCorreo", clave));
        var aviso = jdbc.queryForMap("SELECT a.\"tipo\", a.\"contenido\", a.\"idUsuarioDestino\", e.\"idUsuario\" AS titular"
                + " FROM \"Aviso\" a JOIN \"Estudiante\" e ON e.\"idEstudiante\"=? WHERE a.\"claveEvento\"=?", estudiante, clave);
        assertEquals("Calificacion", aviso.get("tipo"));
        assertEquals(aviso.get("titular"), aviso.get("idUsuarioDestino"), "addressed to the titular student only");
        assertFalse(((String) aviso.get("contenido")).contains(valor), "the grade value is never in the notice");

        // Repeated releases: 200 and no second notice/email.
        assertEquals(200, personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", token, null).statusCode());
        assertEquals(1, cuenta("Aviso", clave));
        assertEquals(1, cuenta("EnvioCorreo", clave));

        // Real delivery by the existing M7 worker, exactly once, without the grade.
        JsonNode mensaje = esperarCorreo(correo);
        assertEquals("Calificación disponible: Espacio QA 09", mensaje.get("Subject").asString());
        String texto = mensaje.get("Text").asString();
        assertTrue(texto.contains("ya está disponible"), texto);
        assertFalse(texto.contains(valor), "the grade value is never in the email");
        Thread.sleep(1500);   // several worker cycles
        assertEquals(1, buzon(correo).get("messages").size(), "delivered exactly once");
        assertEquals("Enviado", jdbc.queryForObject("SELECT \"estadoEnvio\" FROM \"EnvioCorreo\" WHERE \"claveIdempotencia\"=?", String.class, clave));
    }

    private int cuenta(String tabla, String clave) {
        String col = "Aviso".equals(tabla) ? "claveEvento" : "claveIdempotencia";
        return jdbc.queryForObject("SELECT count(*) FROM \"" + tabla + "\" WHERE \"" + col + "\"=?", Integer.class, clave);
    }

    private JsonNode buzon(String correo) throws Exception {
        String base = "http://" + QA.mailpit.getHost() + ":" + QA.mailpit.getMappedPort(8025);
        return json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/search?query=" + java.net.URLEncoder.encode(
                "to:" + correo + " subject:Calificación", java.nio.charset.StandardCharsets.UTF_8))).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode esperarCorreo(String correo) throws Exception {
        String base = "http://" + QA.mailpit.getHost() + ":" + QA.mailpit.getMappedPort(8025);
        for (int i = 0; i < 100; i++) {
            JsonNode lista = buzon(correo);
            if (lista.get("messages") != null && lista.get("messages").size() > 0) {
                String id = lista.get("messages").get(0).get("ID").asString();
                return json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/message/" + id)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("RF-27 email not delivered to Mailpit");
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
