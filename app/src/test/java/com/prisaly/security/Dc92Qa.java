package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m1.application.service.IdentityCatalogService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-92 QA fixture: the DC-92 candidate schema (integral + V021-V023, disposable container), DD-02 B, M5 POST, M6
 * evaluation and N2, switched on ONLY in the calling test context. Reuses {@link RecorridoParticipacionQa}.
 */
final class Dc92Qa {
    private Dc92Qa() { }

    static void propiedades(RecorridoParticipacionQa qa, DynamicPropertyRegistry p) {
        qa.propiedades(p, true);
        Path base = Path.of("src/main/resources/db").toAbsolutePath();
        p.add("spring.flyway.locations", () -> "filesystem:" + base.resolve("migration") + ",filesystem:"
                + base.resolve("dd02b-propuesta") + ",filesystem:" + base.resolve("m6-propuesta") + ",filesystem:"
                + base.resolve("d05-propuesta") + ",filesystem:" + base.resolve("dc92-propuesta"));
        p.add("prisaly.m4.schema-contract", () -> "bd0052-v013-integral-dc92");
        p.add("prisaly.m6.evaluacion.enabled", () -> "true");
        p.add("prisaly.m6.liberacion.enabled", () -> "true");
    }

    /** Published case with a 6+4 rubric; returns {caso, docente, p1, p2, o1a, o1b, o2a, rubrica}. */
    static UUID[] casoConRubrica(IdentityCatalogService catalog, JdbcTemplate jdbc) {
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID rubrica = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\",\"definicionEvaluacion\") VALUES (?,?,?,'{}')",
                rubrica, c[0], "Rubrica DC92 " + rubrica);
        jdbc.update("INSERT INTO \"RubricaPregunta\" (\"idRubricaPregunta\",\"idRubrica\",\"idPregunta\",\"valorPregunta\")"
                + " VALUES (gen_random_uuid(),?,?,6.00),(gen_random_uuid(),?,?,4.00)", rubrica, c[2], rubrica, c[3]);
        return new UUID[] {c[0], c[1], c[2], c[3], c[4], c[5], c[6], rubrica};
    }

    static String correo(String prefijo) {
        return prefijo + "." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
    }

    static String loginPersonal(HttpClient http, int port, ObjectMapper json, String correo) throws Exception {
        var r = personal(http, port, "POST", "/v1/personal/autenticar", null,
                "{\"correo\":\"" + correo + "\",\"password\":\"" + RecorridoParticipacionQa.FIXTURE + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asString();
    }

    static String tokenDocente(HttpClient http, int port, ObjectMapper json, JdbcTemplate jdbc, UUID docente) throws Exception {
        return loginPersonal(http, port, json,
                jdbc.queryForObject("SELECT \"correo\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente));
    }

    /** Another personnel account (not the case creator, no permissions). */
    static String otroDocente(IdentityCatalogService catalog, HttpClient http, int port, ObjectMapper json) throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole("Doc92-" + s, "Fixture");
        String correo = "otro92." + s + "@prisaly.test";
        catalog.registerUser("Otro 92", correo, RecorridoParticipacionQa.FIXTURE, "Doc92-" + s);
        return loginPersonal(http, port, json, correo);
    }

    static HttpResponse<String> personal(HttpClient http, int port, String metodo, String ruta, String token, String cuerpo)
            throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + ruta)).method(metodo,
                cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) { b.header("Content-Type", "application/json"); }
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Starts (or resumes) through the real M5 route and returns the response. */
    static HttpResponse<String> iniciar(HttpClient http, int port, UUID sesion, String cookie) throws Exception {
        return RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookie);
    }

    static HttpResponse<String> finalizar(HttpClient http, int port, String intento, String cookie) throws Exception {
        return RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookie);
    }
}
