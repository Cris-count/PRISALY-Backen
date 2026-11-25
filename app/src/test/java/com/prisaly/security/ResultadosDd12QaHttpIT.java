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
 * DC-92 DD-12: the titular student consults the grade, the teacher's general feedback and — because the explicit
 * configuration {@code prisaly.m6.resultado.detalle-por-pregunta=true} is set ONLY here — the per-question detail,
 * all of it only after the N2 release. Real APP, PostgreSQL 16.15 (DC-92 candidate schema), real student sessions.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResultadosDd12QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        Dc92Qa.propiedades(QA, p);
        p.add("prisaly.m6.retroalimentacion.enabled", () -> "true");          // QA only
        p.add("prisaly.m6.resultado.detalle-por-pregunta", () -> "true");     // explicit configuration, QA only
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void notaRetroalimentacionYDetalleSoloTrasLaPublicacionYSoloAlTitular() throws Exception {
        UUID[] c = Dc92Qa.casoConRubrica(catalog, jdbc);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], c[1], true, 1800);
        String correoAna = Dc92Qa.correo("dd12a"), correoBeto = Dc92Qa.correo("dd12b");
        UUID ana = RecorridoParticipacionQa.estudiante(catalog, jdbc, correoAna);
        UUID beto = RecorridoParticipacionQa.estudiante(catalog, jdbc, correoBeto);
        UUID convAna = RecorridoParticipacionQa.convocar(jdbc, sesion, ana);
        RecorridoParticipacionQa.convocar(jdbc, sesion, beto);
        String cookieAna = QA.login(http, port, json, worker, jdbc, correoAna);
        String cookieBeto = QA.login(http, port, json, worker, jdbc, correoBeto);
        String token = Dc92Qa.tokenDocente(http, port, json, jdbc, c[1]);
        String otro = Dc92Qa.otroDocente(catalog, http, port, json);

        var inicio = Dc92Qa.iniciar(http, port, sesion, cookieAna);
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asString();
        // p1 answered WRONG (1A), p2 answered right (2A).
        for (UUID[] r : new UUID[][] {{c[2], c[4]}, {c[3], c[6]}}) {
            assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento
                    + "/respuestas/" + r[0], "{\"idOpcionSeleccionada\":\"" + r[1] + "\",\"revisionEsperada\":0}", cookieAna).statusCode());
        }
        assertEquals(200, Dc92Qa.finalizar(http, port, intento, cookieAna).statusCode());
        UUID participacion = jdbc.queryForObject("SELECT \"idParticipacion\" FROM \"Participacion\" WHERE \"idConvocatoria\"=?", UUID.class, convAna);
        assertEquals(201, Dc92Qa.personal(http, port, "POST", "/v1/evaluacion/intentos/" + intento, token,
                "{\"idRubrica\":\"" + c[7] + "\"}").statusCode());

        // RF-26 / DD-12: teacher feedback (owner only, mandatory content, one per participation, updatable).
        String ruta = "/v1/docente/participaciones/" + participacion + "/retroalimentacion";
        assertEquals(403, Dc92Qa.personal(http, port, "POST", ruta, otro, "{\"contenido\":\"x\"}").statusCode());
        assertEquals(400, Dc92Qa.personal(http, port, "POST", ruta, token, "{\"contenido\":\" \"}").statusCode());
        assertEquals(201, Dc92Qa.personal(http, port, "POST", ruta, token, "{\"contenido\":\"Primera version\"}").statusCode());
        assertEquals(409, Dc92Qa.personal(http, port, "POST", ruta, token, "{\"contenido\":\"Otra\"}").statusCode());
        var put = Dc92Qa.personal(http, port, "PUT", ruta, token, "{\"contenido\":\"Buen analisis del caso; revisa la pregunta 1.\"}");
        assertEquals(200, put.statusCode(), put.body());

        // Not published yet: the student sees nothing (no grade, no feedback, no detail).
        String rutaEst = "/v1/estudiantes/intentos/" + intento + "/calificacion";
        var antes = RecorridoParticipacionQa.enviar(http, port, "GET", rutaEst, null, cookieAna);
        assertEquals(404, antes.statusCode(), antes.body());
        assertFalse(antes.body().contains("revisa la pregunta") || antes.body().contains("detalle"), antes.body());

        assertEquals(201, Dc92Qa.personal(http, port, "POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", token, null).statusCode());
        var despues = RecorridoParticipacionQa.enviar(http, port, "GET", rutaEst, null, cookieAna);
        assertEquals(200, despues.statusCode(), despues.body());
        JsonNode n = json.readTree(despues.body());
        assertEquals("Buen analisis del caso; revisa la pregunta 1.", n.get("retroalimentacionGeneral").asString());
        JsonNode detalle = n.get("detallePorPregunta");
        assertEquals(2, detalle.size(), despues.body());
        assertEquals("P1", detalle.get(0).get("enunciado").asString());
        assertEquals("1A", detalle.get(0).get("opcionSeleccionada").asString());
        assertFalse(detalle.get(0).get("correcta").asBoolean());
        assertEquals("1B", detalle.get(0).get("opcionCorrecta").asString());
        assertTrue(detalle.get(1).get("correcta").asBoolean());

        // Exclusively the titular student.
        var cruzada = RecorridoParticipacionQa.enviar(http, port, "GET", rutaEst, null, cookieBeto);
        assertTrue(cruzada.statusCode() == 403 || cruzada.statusCode() == 404, cruzada.body());
        assertFalse(cruzada.body().contains("revisa la pregunta") || cruzada.body().contains("1B"), cruzada.body());
        assertEquals(401, RecorridoParticipacionQa.enviar(http, port, "GET", rutaEst, null, null).statusCode());
    }
}
