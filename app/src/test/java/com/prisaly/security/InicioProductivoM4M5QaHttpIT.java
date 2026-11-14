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

import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-CIERRE-FUNCIONAL-09, fase 4 — <b>ENSAMBLADO PRODUCTIVO</b> (sin políticas sustituidas). DD-02 B y la
 * publicación de los POST se activan SOLO en este contexto QA. Muestra lo que de verdad ocurre hoy con las reglas
 * sin ratificar: el descubrimiento funciona, el primer inicio responde 409 {@code INICIO_NO_DISPONIBLE} sin escribir
 * y la finalización de un intento existente es la operación real, idempotente.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InicioProductivoM4M5QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) { QA.propiedades(p, true); }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void conLasPoliticasProductivasElPrimerInicioSigueBloqueadoYLaFinalizacionEsReal() throws Exception {
        String correo = "prod09." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], c[1], true, 1800);
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, sesion, estudiante);
        String cookie = QA.login(http, port, json, worker, correo);

        var lista = RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/convocatorias", null, cookie);
        assertEquals(200, lista.statusCode(), lista.body());
        assertTrue(lista.body().contains(conv.toString()));

        var inicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookie);
        assertEquals(409, inicio.statusCode(), inicio.body());
        assertTrue(inicio.body().contains("INICIO_NO_DISPONIBLE"), inicio.body());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\" WHERE \"idConvocatoria\"=?", Integer.class, conv),
                "the blocked first start writes nothing");

        // An attempt that already exists (fixture) resumes and closes with the real production assembly.
        UUID participacion = UUID.randomUUID(), intento = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participacion, conv);
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\")"
                + " VALUES (?,?,1,now(),now()+interval '30 minutes','EnCurso')", intento, participacion);
        var reanudacion = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookie);
        assertEquals(200, reanudacion.statusCode(), reanudacion.body());
        assertEquals(intento.toString(), json.readTree(reanudacion.body()).get("intento").get("idIntento").asText());

        var fin = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookie);
        assertEquals(200, fin.statusCode(), fin.body());
        var repetido = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookie);
        assertEquals(200, repetido.statusCode(), repetido.body());
        assertEquals(json.readTree(fin.body()).get("fin").asText(), json.readTree(repetido.body()).get("fin").asText());
        assertEquals("Finalizado", jdbc.queryForObject("SELECT \"estado\" FROM \"Intento\" WHERE \"idIntento\"=?", String.class, intento));
    }

    @Test
    void sinOrigenConfiableLosPostQueCambianEstadoSeRechazan() throws Exception {
        String correo = "orig09." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], c[1], true, 1800);
        RecorridoParticipacionQa.convocar(jdbc, sesion, estudiante);
        String cookie = QA.login(http, port, json, worker, correo);
        var sinOrigen = http.send(java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://localhost:" + port + "/api/v1/estudiantes/sesiones/" + sesion + "/intentos"))
                .header("Cookie", cookie).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403, sinOrigen.statusCode(), sinOrigen.body());
    }
}
