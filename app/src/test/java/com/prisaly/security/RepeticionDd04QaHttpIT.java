package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;
import java.net.http.HttpClient;
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
 * DC-92 DD-04 over the real APP and PostgreSQL 16.15 (DC-92 candidate schema): one exceptional repetition authorized by
 * the case creator with a mandatory justification, consumed once by the real M5 start, both attempts kept, the valid
 * attempt selected by the teacher with an immutable history. {@code prisaly.m5.repeticion.enabled} ONLY here.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RepeticionDd04QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        Dc92Qa.propiedades(QA, p);
        p.add("prisaly.m5.repeticion.enabled", () -> "true");   // QA only
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void unaRepeticionAutorizadaJustificadaConHistorialYSeleccionDocente() throws Exception {
        UUID[] c = Dc92Qa.casoConRubrica(catalog, jdbc);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], c[1], true, 1800);
        String correo = Dc92Qa.correo("dd04");
        UUID est = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, sesion, est);
        String cookie = QA.login(http, port, json, worker, jdbc, correo);
        String token = Dc92Qa.tokenDocente(http, port, json, jdbc, c[1]);
        String otro = Dc92Qa.otroDocente(catalog, http, port, json);

        // Attempt 1, finished.
        var i1 = Dc92Qa.iniciar(http, port, sesion, cookie);
        assertEquals(201, i1.statusCode(), i1.body());
        String intento1 = json.readTree(i1.body()).get("intento").get("idIntento").asString();
        assertEquals(200, Dc92Qa.finalizar(http, port, intento1, cookie).statusCode());
        UUID participacion = jdbc.queryForObject("SELECT \"idParticipacion\" FROM \"Participacion\" WHERE \"idConvocatoria\"=?", UUID.class, conv);

        // Without authorization the repetition is still refused, and nothing is written.
        var sinAutorizacion = Dc92Qa.iniciar(http, port, sesion, cookie);
        assertEquals(409, sinAutorizacion.statusCode(), sinAutorizacion.body());
        assertTrue(sinAutorizacion.body().contains("REPETICION_NO_HABILITADA"), sinAutorizacion.body());

        String ruta = "/v1/docente/participaciones/" + participacion + "/repeticion";
        // Only the case creator; justification mandatory; the student cookie is not personnel.
        assertEquals(403, Dc92Qa.personal(http, port, "POST", ruta, otro, "{\"motivo\":\"Falla electrica\"}").statusCode());
        var sinMotivo = Dc92Qa.personal(http, port, "POST", ruta, token, "{\"motivo\":\"  \"}");
        assertEquals(400, sinMotivo.statusCode(), sinMotivo.body());
        int conCookie = RecorridoParticipacionQa.enviar(http, port, "POST", ruta, "{\"motivo\":\"x\"}", cookie).statusCode();
        assertTrue(conCookie == 401 || conCookie == 403, "student cookie: " + conCookie);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"AutorizacionRepeticion\" WHERE \"idParticipacion\"=?", Integer.class, participacion));

        var autorizada = Dc92Qa.personal(http, port, "POST", ruta, token, "{\"motivo\":\"Falla electrica en el aula\"}");
        assertEquals(201, autorizada.statusCode(), autorizada.body());
        JsonNode a = json.readTree(autorizada.body());
        assertEquals("Vigente", a.get("estadoAutorizacion").asString());
        assertEquals("Falla electrica en el aula", a.get("motivo").asString());
        assertEquals(c[1].toString(), a.get("idDocenteAutorizador").asString());
        assertEquals(409, Dc92Qa.personal(http, port, "POST", ruta, token, "{\"motivo\":\"otra\"}").statusCode(), "one vigent at a time");

        // The real M5 start consumes it: attempt 2, attempt 1 kept.
        var i2 = Dc92Qa.iniciar(http, port, sesion, cookie);
        assertEquals(201, i2.statusCode(), i2.body());
        String intento2 = json.readTree(i2.body()).get("intento").get("idIntento").asString();
        assertNotEquals(intento1, intento2);
        assertEquals(2, jdbc.queryForObject("SELECT \"numeroIntento\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid", Integer.class, intento2));
        assertEquals("Utilizada", jdbc.queryForObject("SELECT \"estadoAutorizacion\" FROM \"AutorizacionRepeticion\" WHERE \"idParticipacion\"=?", String.class, participacion));
        assertEquals(200, Dc92Qa.finalizar(http, port, intento2, cookie).statusCode());

        // A single repetition: no further authorization; the database refuses a third attempt too.
        var agotada = Dc92Qa.personal(http, port, "POST", ruta, token, "{\"motivo\":\"tercera\"}");
        assertEquals(409, agotada.statusCode(), agotada.body());
        assertTrue(agotada.body().contains("REPETICION_AGOTADA"), agotada.body());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("INSERT INTO \"Intento\""
                + " (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\")"
                + " VALUES (gen_random_uuid(),?,3,now(),now()+interval '1 minute','EnCurso')", participacion));

        // The teacher selects the academically valid attempt, with reason; history is append-only.
        String sel = "/v1/docente/participaciones/" + participacion + "/intento-valido";
        assertEquals(400, Dc92Qa.personal(http, port, "POST", sel, token, "{\"idIntento\":\"" + intento1 + "\"}").statusCode(), "reason mandatory");
        assertEquals(403, Dc92Qa.personal(http, port, "POST", sel, otro, "{\"idIntento\":\"" + intento1 + "\",\"motivo\":\"x\"}").statusCode());
        var ajeno = Dc92Qa.personal(http, port, "POST", sel, token, "{\"idIntento\":\"" + UUID.randomUUID() + "\",\"motivo\":\"x\"}");
        assertEquals(422, ajeno.statusCode(), ajeno.body());
        assertEquals(201, Dc92Qa.personal(http, port, "POST", sel, token, "{\"idIntento\":\"" + intento1 + "\",\"motivo\":\"Primer intento completo\"}").statusCode());
        assertEquals(201, Dc92Qa.personal(http, port, "POST", sel, token, "{\"idIntento\":\"" + intento2 + "\",\"motivo\":\"Revision docente\"}").statusCode());

        var historial = Dc92Qa.personal(http, port, "GET", ruta, token, null);
        assertEquals(200, historial.statusCode(), historial.body());
        JsonNode h = json.readTree(historial.body());
        assertEquals(2, h.get("intentos").size(), "both attempts kept");
        assertEquals(1, h.get("autorizaciones").size());
        assertEquals(2, h.get("selecciones").size(), "full selection history");
        assertEquals(intento2, h.get("idIntentoValido").asString(), "the latest selection is the valid one");
        assertEquals(403, Dc92Qa.personal(http, port, "GET", ruta, otro, null).statusCode());
        assertEquals(404, Dc92Qa.personal(http, port, "GET", "/v1/docente/participaciones/" + UUID.randomUUID() + "/repeticion", token, null).statusCode());
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE \"SeleccionIntento\" SET \"motivo\"='x' WHERE \"idParticipacion\"=?", participacion));
    }
}
