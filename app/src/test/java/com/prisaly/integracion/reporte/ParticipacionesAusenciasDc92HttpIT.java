package com.prisaly.integracion.reporte;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/**
 * DC-92 DD-09 (teacher query of own sessions) and DD-13 (absence classification with history, never a zero) over the
 * real APP and PostgreSQL 16.15 with the DC-92 candidate schema. Switches ON only here. Fictitious data.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParticipacionesAusenciasDc92HttpIT extends ReporteParticipacionHttpFixture {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("prisaly.m4.consulta-participaciones.enabled", () -> "true"); // QA only
        r.add("prisaly.m4.ausencias.enabled", () -> "true");                // QA only
        esquemaDc92(r);
    }

    UUID admin;

    @BeforeEach
    void preparar() {
        admin = administrador();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(uri(ruta)).method(metodo,
                cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode item(JsonNode items, String nombre) {
        return StreamSupport.stream(items.spliterator(), false).filter(n -> nombre.equals(n.get("nombreEstudiante").asString()))
                .findFirst().orElseThrow(() -> new AssertionError("no item " + nombre + " in " + items));
    }

    @Test
    void dd09ConsultaSoloDeSusSesionesYConvocadosConNotaSoloPublicada() throws Exception {
        Docente a = docente(admin, true), b = docente(admin, true), sinPermiso = docente(admin, false);
        Sesion s = sesion(a, "Espacio DD-09", "Finalizado");
        Sesion otra = sesion(a, "Otra sesion", "Finalizado");
        UUID p = participacion(convocar(s, "Ana Publicada"));
        UUID i = intento(p, 1, true, a.id());
        calificar(s, i, "4.00");
        liberar(i, a.id());
        UUID q = participacion(convocar(s, "Beto Oculta"));
        calificar(s, intento(q, 1, true, a.id()), "2.00");
        convocar(otra, "De Otra Sesion");

        var r = enviar("GET", "/v1/docente/sesiones/" + s.id() + "/participaciones", a.token(), null);
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode items = json.readTree(r.body()).get("items");
        assertEquals(2, items.size(), "only convoked students of THIS session");
        assertFalse(r.body().contains("De Otra Sesion"));
        JsonNode ana = item(items, "Ana Publicada");
        assertEquals("PUBLICADA", ana.get("estadoEvaluacion").asString());
        assertTrue(r.body().contains("\"calificacionPublicada\":4.00"), r.body());
        assertEquals(1, ana.get("numeroIntentos").asInt());
        assertFalse(ana.get("fechaIngreso").isNull(), "entry date");
        assertFalse(ana.get("inicioIntento").isNull() || ana.get("finIntento").isNull());
        JsonNode beto = item(items, "Beto Oculta");
        assertEquals("EVALUADA_NO_PUBLICADA", beto.get("estadoEvaluacion").asString());
        assertTrue(beto.get("calificacionPublicada").isNull(), "result subject to publication");
        assertFalse(r.body().contains("2.00"));
        assertFalse(r.body().contains("@prisaly.test"), "no email");

        var ajeno = enviar("GET", "/v1/docente/sesiones/" + s.id() + "/participaciones", b.token(), null);
        assertEquals(403, ajeno.statusCode(), ajeno.body());
        assertTrue(ajeno.body().contains("M4-SESION-AJENA"));
        assertFalse(ajeno.body().contains("Ana Publicada"));
        assertEquals(403, enviar("GET", "/v1/docente/sesiones/" + s.id() + "/participaciones", sinPermiso.token(), null).statusCode());
        assertEquals(404, enviar("GET", "/v1/docente/sesiones/" + UUID.randomUUID() + "/participaciones", a.token(), null).statusCode());
        assertEquals(401, enviar("GET", "/v1/docente/sesiones/" + s.id() + "/participaciones", null, null).statusCode());
    }

    @Test
    void dd13ClasificacionDiferenciadaConHistorialYSinCeroAutomatico() throws Exception {
        Docente a = docente(admin, true), b = docente(admin, true);
        Sesion abierta = sesion(a, "Abierta", "EnCurso");
        Sesion cerrada = sesion(a, "Cerrada", "Finalizado");
        UUID convocado = convocar(abierta, "Aun Convocado");
        UUID ausente = convocar(cerrada, "Ausente Real");
        UUID conIntento = convocar(cerrada, "Si Participo");
        intento(participacion(conIntento), 1, true, a.id());
        UUID retirado = convocar(cerrada, "Retirado");
        jdbc.update("UPDATE \"Convocatoria\" SET \"fechaRetiro\"=now(), \"estadoAutorizacion\"='Retirado' WHERE \"idConvocatoria\"=?", retirado);
        int calificaciones = jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\"", Integer.class);
        String justificar = "{\"estado\":\"AUSENCIA_JUSTIFICADA\",\"motivo\":\"Incapacidad medica\"}";

        // Invalid transitions: still convoked, participated, withdrawn, unknown state.
        for (UUID c : List.of(convocado, conIntento, retirado)) {
            var r = enviar("POST", "/v1/docente/convocatorias/" + c + "/ausencia", a.token(), justificar);
            assertEquals(409, r.statusCode(), r.body());
            assertTrue(r.body().contains("TRANSICION_NO_VALIDA"), r.body());
        }
        assertEquals(400, enviar("POST", "/v1/docente/convocatorias/" + ausente + "/ausencia", a.token(), "{\"estado\":\"CERO\"}").statusCode());
        assertEquals(403, enviar("POST", "/v1/docente/convocatorias/" + ausente + "/ausencia", b.token(), justificar).statusCode());
        assertEquals(404, enviar("POST", "/v1/docente/convocatorias/" + UUID.randomUUID() + "/ausencia", a.token(), justificar).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"ClasificacionAusencia\"", Integer.class));

        // Valid: pending review -> justified -> not justified; the same state again is not a change.
        var j = enviar("POST", "/v1/docente/convocatorias/" + ausente + "/ausencia", a.token(), justificar);
        assertEquals(201, j.statusCode(), j.body());
        assertEquals(a.id().toString(), json.readTree(j.body()).get("idDocente").asString());
        assertEquals(409, enviar("POST", "/v1/docente/convocatorias/" + ausente + "/ausencia", a.token(), justificar).statusCode());
        assertEquals(201, enviar("POST", "/v1/docente/convocatorias/" + ausente + "/ausencia", a.token(),
                "{\"estado\":\"AUSENCIA_NO_JUSTIFICADA\"}").statusCode());

        var h = enviar("GET", "/v1/docente/convocatorias/" + ausente + "/ausencia", a.token(), null);
        assertEquals(200, h.statusCode(), h.body());
        JsonNode items = json.readTree(h.body()).get("items");
        assertEquals(2, items.size(), "full traceability");
        assertEquals("AUSENCIA_JUSTIFICADA", items.get(0).get("estado").asString());
        assertEquals("AUSENCIA_NO_JUSTIFICADA", items.get(1).get("estado").asString());
        assertEquals(403, enviar("GET", "/v1/docente/convocatorias/" + ausente + "/ausencia", b.token(), null).statusCode());

        var lista = json.readTree(enviar("GET", "/v1/docente/sesiones/" + cerrada.id() + "/participaciones", a.token(), null).body()).get("items");
        assertEquals("AUSENCIA_NO_JUSTIFICADA", item(lista, "Ausente Real").get("estadoParticipacion").asString());
        assertEquals("SIN_INTENTOS", item(lista, "Ausente Real").get("estadoEvaluacion").asString());
        assertEquals("RETIRADO", item(lista, "Retirado").get("estadoParticipacion").asString());
        assertEquals(calificaciones, jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\"", Integer.class), "never a zero");
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM \"ClasificacionAusencia\" WHERE \"idConvocatoria\"=?", ausente));
    }
}
