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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-CIERRE-DEFINITIVO-87 — N2 CANDIDATE (M6-R10) over the real APP, PostgreSQL 16.15 with the INTEGRAL candidate
 * schema (V014-V016 + V019 + V020, disposable container) and Mailpit. DD-02 B, M5 POST, M6 evaluation and the N2
 * release are switched on ONLY here. Real attempts (M5 HTTP), real grades (M6 HTTP), real student sessions (DD-02 B).
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiberacionN2QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        QA.propiedades(p, true);
        Path base = Path.of("src/main/resources/db").toAbsolutePath();
        p.add("spring.flyway.locations", () -> "filesystem:" + base.resolve("migration") + ",filesystem:"
                + base.resolve("dd02b-propuesta") + ",filesystem:" + base.resolve("m6-propuesta") + ",filesystem:"
                + base.resolve("d05-propuesta"));
        p.add("prisaly.m4.schema-contract", () -> "bd0052-v013-integral");
        p.add("prisaly.m6.evaluacion.enabled", () -> "true");   // QA only
        p.add("prisaly.m6.liberacion.enabled", () -> "true");   // QA only
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    UUID admin, docente, rubrica, sesion, p1, p2, o1b, o2a;
    String tokenDocente;

    @BeforeEach
    void preparar() throws Exception {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        admin = catalog.registerUser("Admin N2", "admin.n2." + UUID.randomUUID() + "@prisaly.test",
                RecorridoParticipacionQa.FIXTURE, IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        docente = c[1]; p1 = c[2]; p2 = c[3]; o1b = c[5]; o2a = c[6];
        rubrica = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\",\"definicionEvaluacion\") VALUES (?,?,?,'{}')",
                rubrica, c[0], "Rubrica N2 " + rubrica);
        jdbc.update("INSERT INTO \"RubricaPregunta\" (\"idRubricaPregunta\",\"idRubrica\",\"idPregunta\",\"valorPregunta\")"
                + " VALUES (gen_random_uuid(),?,?,6.00),(gen_random_uuid(),?,?,4.00)", rubrica, p1, rubrica, p2);
        sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], docente, true, 1800);
        tokenDocente = loginPersonal(jdbc.queryForObject("SELECT \"correo\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente));
    }

    /** A student convoked to the session, logged in through DD-02 B; returns {cookie, idIntento finalizado}. */
    private String[] estudianteConIntentoFinalizado(boolean responder) throws Exception {
        String correo = "n2." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID est = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        RecorridoParticipacionQa.convocar(jdbc, sesion, est);
        String cookie = QA.login(http, port, json, worker, jdbc, correo);
        var inicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookie);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asString();
        if (responder) {
            for (UUID[] r : new UUID[][] {{p1, o1b}, {p2, o2a}}) {
                assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento
                        + "/respuestas/" + r[0], "{\"idOpcionSeleccionada\":\"" + r[1] + "\",\"revisionEsperada\":0}", cookie).statusCode());
            }
        }
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookie).statusCode());
        return new String[] {cookie, intento};
    }

    @Test
    void liberacionUnicaPorElDocentePropietarioYConsultaExclusivaDelEstudianteTitular() throws Exception {
        String[] ana = estudianteConIntentoFinalizado(true), beto = estudianteConIntentoFinalizado(false);
        String intento = ana[1];

        // Without a grade: the student sees nothing; releasing is 404.
        assertNoDisponible(RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/calificacion", null, ana[0]));
        var sinNota = personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", tokenDocente);
        assertEquals(404, sinNota.statusCode(), sinNota.body());
        assertTrue(sinNota.body().contains("CALIFICACION_NO_ENCONTRADA"), sinNota.body());

        // Graded but NOT released: still the same uniform 404 for the student; teacher sees liberada=false.
        var evaluacion = personal("POST", "/v1/evaluacion/intentos/" + intento, tokenDocente, "{\"idRubrica\":\"" + rubrica + "\"}");
        assertEquals(201, evaluacion.statusCode(), evaluacion.body());
        assertFalse(json.readTree(evaluacion.body()).get("liberada").asBoolean());
        String valor = jdbc.queryForObject("SELECT \"valor\"::text FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid", String.class, intento);
        var oculta = RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/calificacion", null, ana[0]);
        assertNoDisponible(oculta);
        assertFalse(oculta.body().contains(valor), "an unreleased grade never leaks");

        // Only the owner teacher releases; another teacher with the same permissions gets 403 and nothing is written.
        String otro = otroDocente();
        var ajeno = personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", otro);
        assertEquals(403, ajeno.statusCode(), ajeno.body());
        assertEquals(0, liberaciones(intento));
        // The student cookie is not a personnel credential.
        int conCookie = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", null, ana[0]).statusCode();
        assertTrue(conCookie == 401 || conCookie == 403, "student cookie on a personnel route: " + conCookie);
        assertEquals(0, liberaciones(intento));

        // First release 201 with author and server instant; repeated 200 with the SAME author and instant.
        var primera = personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", tokenDocente);
        assertEquals(201, primera.statusCode(), primera.body());
        var n = json.readTree(primera.body());
        assertTrue(n.get("liberada").asBoolean());
        assertEquals(docente.toString(), n.get("idDocenteLiberador").asString());
        var segunda = personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", tokenDocente);
        assertEquals(200, segunda.statusCode(), segunda.body());
        assertEquals(n.get("fechaLiberacion").asString(), json.readTree(segunda.body()).get("fechaLiberacion").asString());
        assertEquals(1, liberaciones(intento));
        assertTrue(json.readTree(personal("GET", "/v1/evaluacion/intentos/" + intento, tokenDocente).body()).get("liberada").asBoolean());

        // The titular student now sees the released grade; Beto never sees Ana's.
        var propia = RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/calificacion", null, ana[0]);
        assertEquals(200, propia.statusCode(), propia.body());
        assertTrue(propia.body().contains("\"valor\":" + valor) && propia.body().contains("\"escala\":\"0-5\""), propia.body());
        assertFalse(propia.body().contains("detalle"), "no per-question detail in N2");
        assertTrue(propia.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        var cruzada = RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/calificacion", null, beto[0]);
        assertTrue(cruzada.statusCode() == 403 || cruzada.statusCode() == 404, cruzada.body());
        assertFalse(cruzada.body().contains(valor), cruzada.body());
        assertEquals(401, RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/calificacion", null, null).statusCode());

        // Irreversible at the database level too (V019 triggers).
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM \"LiberacionCalificacion\" WHERE \"idCalificacion\" IN (SELECT \"idCalificacion\" FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid)", intento));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE \"LiberacionCalificacion\" SET \"fechaLiberacion\"=now()"));
    }

    @Test
    void liberacionesConcurrentesProducenUnaSolaFila() throws Exception {
        String intento = estudianteConIntentoFinalizado(true)[1];
        assertEquals(201, personal("POST", "/v1/evaluacion/intentos/" + intento, tokenDocente, "{\"idRubrica\":\"" + rubrica + "\"}").statusCode());
        var listo = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            resultados.add(pool.submit(() -> { listo.await(); return personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", tokenDocente).statusCode(); }));
        }
        listo.countDown();
        int creadas = 0, repetidas = 0;
        for (var f : resultados) { int s = f.get(); if (s == 201) creadas++; else if (s == 200) repetidas++; }
        pool.shutdown();
        assertEquals(1, creadas, "exactly one release is created");
        assertEquals(7, repetidas);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"LiberacionCalificacion\" l JOIN \"Calificacion\" k USING (\"idCalificacion\")"
                + " WHERE k.\"idIntento\"=?::uuid", Integer.class, intento));
    }

    private void assertNoDisponible(HttpResponse<String> r) {
        assertEquals(404, r.statusCode(), r.body());
        assertTrue(r.body().contains("CALIFICACION_NO_DISPONIBLE"), r.body());
    }

    /** Releases of THIS attempt's grade (the container is shared by the test methods). */
    private int liberaciones(String intento) {
        return jdbc.queryForObject("SELECT count(*) FROM \"LiberacionCalificacion\" l JOIN \"Calificacion\" k"
                + " USING (\"idCalificacion\") WHERE k.\"idIntento\"=?::uuid", Integer.class, intento);
    }

    private String otroDocente() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole("DocN2-" + s, "Fixture");
        String correo = "otro.n2." + s + "@prisaly.test";
        catalog.registerUser("Otro N2", correo, RecorridoParticipacionQa.FIXTURE, "DocN2-" + s);
        return loginPersonal(correo);
    }

    private String loginPersonal(String correo) throws Exception {
        var r = personal("POST", "/v1/personal/autenticar", null,
                "{\"correo\":\"" + correo + "\",\"password\":\"" + RecorridoParticipacionQa.FIXTURE + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asString();
    }

    private HttpResponse<String> personal(String metodo, String ruta, String token) throws Exception {
        return personal(metodo, ruta, token, null);
    }

    private HttpResponse<String> personal(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + ruta)).method(metodo,
                cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) { b.header("Content-Type", "application/json"); }
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
