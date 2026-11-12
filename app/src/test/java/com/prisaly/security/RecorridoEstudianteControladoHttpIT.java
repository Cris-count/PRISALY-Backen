package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-M5-DD02-01 — CONTROLLED QA SCENARIO, NOT A RATIFIED POLICY.
 *
 * <p>Student journey over the real APP and PostgreSQL 16.15 on the routes that are published today
 * (GET attempt, PUT answer), with the opt-in DD-02 candidate (alternative A, V011/V012 proposals) enabled ONLY in
 * this isolated fixture. The academic chain (case, session, call, participation, attempt) is seeded by SQL
 * because the first start is denied in production until eligibility and the time limit are ratified;
 * that denial is asserted, not bypassed.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RecorridoEstudianteControladoHttpIT {
    static final String CLAVE = "ControlledPassword#123";
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("spring.flyway.locations", () -> {
            Path base = Path.of("src/main/resources/db").toAbsolutePath();
            if (!Files.isDirectory(base)) base = Path.of("app/src/main/resources/db").toAbsolutePath();
            return "filesystem:" + base.resolve("migration") + ",filesystem:" + base.resolve("dd02-propuesta");
        });
        p.add("prisaly.m4.schema-contract", () -> "bd0052-v013-dd02");
        p.add("prisaly.m1.student-access.enabled", () -> "true");
        p.add("prisaly.m1.student-access.hmac-secret", () -> "controlled-http-fixture-key-not-production");
        p.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        p.add("prisaly.m7.worker.enabled", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    // Per-test chain: student A owns an attempt; student B is invited to the same session with its own attempt.
    UUID sesion, intentoA, intentoB, preguntaA1, opcionA1a, opcionA1b, opcionOtraPregunta, convocatoriaA;
    String cookieA, cookieB, bearerDocente;

    @BeforeEach void cadena() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        UUID usuarioA = estudianteConCuenta("a" + s), usuarioB = estudianteConCuenta("b" + s);
        UUID estudianteA = idEstudiante(usuarioA), estudianteB = idEstudiante(usuarioB);
        String rolDocente = "DocRecorrido-" + s;
        catalog.createRole(rolDocente, "Controlled teacher fixture");
        String correoDocente = "doc" + s + "@recorrido.prisaly.test";
        UUID docente = catalog.registerUser("Docente recorrido", correoDocente, CLAVE, rolDocente).getIdUsuario();
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID();
        preguntaA1 = UUID.randomUUID();
        UUID pregunta2 = UUID.randomUUID();
        opcionA1a = UUID.randomUUID(); opcionA1b = UUID.randomUUID(); opcionOtraPregunta = UUID.randomUUID();
        sesion = UUID.randomUUID();
        convocatoriaA = UUID.randomUUID();
        UUID convocatoriaB = UUID.randomUUID(), participacionA = UUID.randomUUID(), participacionB = UUID.randomUUID();
        intentoA = UUID.randomUUID(); intentoB = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,600,'Publicado')",
                caso, docente, "Caso recorrido " + s);
        jdbc.update("INSERT INTO \"Escenario\" (\"idEscenario\",\"idCaso\",\"orden\") VALUES (?,?,1)", escenario, caso);
        jdbc.update("INSERT INTO \"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"estadoEdicion\",\"orden\") VALUES (?,?,'P1','Lista',1),(?,?,'P2','Lista',2)",
                preguntaA1, escenario, pregunta2, escenario);
        jdbc.update("INSERT INTO \"OpcionRespuesta\" (\"idOpcion\",\"idPregunta\",\"texto\",\"orden\") VALUES (?,?,'1A',1),(?,?,'1B',2),(?,?,'2A',1)",
                opcionA1a, preguntaA1, opcionA1b, preguntaA1, opcionOtraPregunta, pregunta2);
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio recorrido',now()-interval '1 hour',now()+interval '1 hour',3600)""", sesion, caso, docente);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?),(?,?,?)",
                convocatoriaA, sesion, estudianteA, convocatoriaB, sesion, estudianteB);
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?),(?,?)",
                participacionA, convocatoriaA, participacionB, convocatoriaB);
        jdbc.update("""
                INSERT INTO "Intento" ("idIntento","idParticipacion","numeroIntento","inicio","limiteTemporal","estado")
                VALUES (?,?,1,now(),now()+interval '1 hour','EnCurso'),(?,?,1,now(),now()+interval '1 hour','EnCurso')""",
                intentoA, participacionA, intentoB, participacionB);
        cookieA = login("a" + s);
        cookieB = login("b" + s);
        bearerDocente = bearer(correoDocente);
    }

    // 1 + 6: the owner reads and answers its own attempt; the answer is linked to that attempt and question.
    @Test void titularConsultaYRespondeSuIntentoVinculadoAlIntentoCorrecto() throws Exception {
        var consulta = get("/v1/estudiantes/intentos/" + intentoA, cookieA, null);
        assertEquals(200, consulta.statusCode(), consulta.body());
        JsonNode intento = json.readTree(consulta.body());
        assertEquals(intentoA.toString(), intento.get("idIntento").asText());
        assertEquals("EnCurso", intento.get("estado").asText());
        assertEquals(0, intento.get("respuestas").size());

        var guardada = put(intentoA, preguntaA1, opcionA1a, 0, cookieA, null);
        assertEquals(200, guardada.statusCode(), guardada.body());
        assertEquals(1, json.readTree(guardada.body()).get("revision").asInt());
        assertEquals(1, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=? AND \"idPregunta\"=? AND \"idOpcionSeleccionada\"=?",
                intentoA, preguntaA1, opcionA1a));
        assertEquals(0, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intentoB));

        // Expected-revision CAS (D06): a stale revision is 409 and does not overwrite.
        var obsoleta = put(intentoA, preguntaA1, opcionA1b, 0, cookieA, null);
        assertEquals(409, obsoleta.statusCode(), obsoleta.body());
        assertTrue(obsoleta.body().contains("REVISION_OBSOLETA"), obsoleta.body());
        var actualizada = put(intentoA, preguntaA1, opcionA1b, 1, cookieA, null);
        assertEquals(200, actualizada.statusCode(), actualizada.body());
        assertEquals(1, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=? AND \"idOpcionSeleccionada\"=?", intentoA, opcionA1b));
        assertEquals(1, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intentoA), "answer updated, not duplicated");
    }

    // 1: another student invited to the same session cannot read or write the owner's attempt.
    @Test void otroEstudianteNoAccedeNiEscribeElIntentoAjeno() throws Exception {
        var lectura = get("/v1/estudiantes/intentos/" + intentoA, cookieB, null);
        observar("ajeno-GET", lectura);
        assertEquals(403, lectura.statusCode(), lectura.body());
        var escritura = put(intentoA, preguntaA1, opcionA1a, 0, cookieB, null);
        assertEquals(403, escritura.statusCode(), escritura.body());
        assertFalse(escritura.body().contains(intentoA.toString()) && escritura.body().contains("respuestas"));
        assertEquals(0, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intentoA));
        assertEquals(200, get("/v1/estudiantes/intentos/" + intentoB, cookieB, null).statusCode(), "B still reads its own");
    }

    // 3: a teacher token is never a student identity; client identity headers are ignored.
    @Test void tokenDocenteYCabecerasManipuladasNoSonIdentidadEstudiantil() throws Exception {
        var docente = get("/v1/estudiantes/intentos/" + intentoA, null, bearerDocente);
        assertTrue(docente.statusCode() == 401 || docente.statusCode() == 403, docente.statusCode() + " " + docente.body());
        var escritura = put(intentoA, preguntaA1, opcionA1a, 0, null, bearerDocente);
        observar("docente-GET", docente); observar("docente-PUT", escritura);
        assertTrue(escritura.statusCode() == 401 || escritura.statusCode() == 403, escritura.statusCode() + " " + escritura.body());
        var manipulada = http.send(HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/" + intentoA))
                .header("Cookie", cookieB).header("X-Student-Id", "ignored").header("X-Estudiante", intentoA.toString())
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, manipulada.statusCode(), manipulada.body());
        assertEquals(0, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intentoA));
    }

    // 6: an option of another question, or a malformed body, never persists.
    @Test void opcionDeOtraPreguntaYCuerpoInvalidoNoPersisten() throws Exception {
        var otraPregunta = put(intentoA, preguntaA1, opcionOtraPregunta, 0, cookieA, null);
        observar("opcion-otra-pregunta", otraPregunta);
        assertTrue(otraPregunta.statusCode() == 400 || otraPregunta.statusCode() == 409, otraPregunta.statusCode() + " " + otraPregunta.body());
        var sinRevision = send("PUT", "/v1/estudiantes/intentos/" + intentoA + "/respuestas/" + preguntaA1, cookieA,
                "{\"idOpcionSeleccionada\":\"" + opcionA1a + "\"}");
        assertEquals(400, sinRevision.statusCode(), sinRevision.body());
        assertTrue(sinRevision.body().contains("VALIDATION_ERROR"));
        var uuidInvalido = get("/v1/estudiantes/intentos/no-es-uuid", cookieA, null);
        assertEquals(400, uuidInvalido.statusCode(), uuidInvalido.body());
        assertEquals(0, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intentoA));
    }

    // 2 + 4 + 5 + 7: start and finish are NOT published (pending D02/D07/eligibility/time limit): no attempt is
    // created or closed through HTTP, and the existing attempt keeps its state.
    @Test void inicioYFinalizacionNoEstanPublicadosYNoAlteranDatos() throws Exception {
        int intentosAntes = cantidad("SELECT count(*) FROM \"Intento\"");
        var inicio = send("POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", cookieA, "{}");
        assertTrue(inicio.statusCode() == 404 || inicio.statusCode() == 405, inicio.statusCode() + " " + inicio.body());
        var finalizar = send("POST", "/v1/estudiantes/intentos/" + intentoA + "/finalizar", cookieA, "{}");
        observar("POST-iniciar", inicio); observar("POST-finalizar", finalizar);
        assertTrue(finalizar.statusCode() == 404 || finalizar.statusCode() == 405, finalizar.statusCode() + " " + finalizar.body());
        assertEquals(intentosAntes, cantidad("SELECT count(*) FROM \"Intento\""));
        assertEquals("EnCurso", jdbc.queryForObject("SELECT \"estado\" FROM \"Intento\" WHERE \"idIntento\"=?", String.class, intentoA));
    }

    // 8: no grade exists nor is published: M6 has no HTTP route and nothing is written to Calificacion.
    @Test void sinCalificacionPublicadaNiRutaDeResultado() throws Exception {
        put(intentoA, preguntaA1, opcionA1a, 0, cookieA, null);
        var resultado = get("/v1/estudiantes/participaciones/" + UUID.randomUUID() + "/resultado", cookieA, null);
        assertEquals(404, resultado.statusCode(), resultado.body());
        assertEquals(0, cantidad("SELECT count(*) FROM \"Calificacion\""));
        assertEquals(0, cantidad("SELECT count(*) FROM \"RetroalimentacionGeneral\""));
    }

    // ---------- helpers ----------
    /** Records the exact status and body code observed, for the manual Postman guide (no secrets in bodies). */
    private static void observar(String caso, HttpResponse<String> r) {
        System.out.println("RECORRIDO-OBSERVADO " + caso + " " + r.statusCode() + " " + r.body());
    }
    private UUID estudianteConCuenta(String alias) {
        String rol = "EstRecorrido-" + alias;
        catalog.createRole(rol, "Controlled student fixture");
        UUID usuario = catalog.registerUser("Estudiante " + alias, alias + "@recorrido.prisaly.test", CLAVE, rol).getIdUsuario();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), usuario);
        return usuario;
    }
    private UUID idEstudiante(UUID usuario) {
        return jdbc.queryForObject("SELECT \"idEstudiante\" FROM \"Estudiante\" WHERE \"idUsuario\"=?", UUID.class, usuario);
    }
    private int cantidad(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private String login(String alias) throws Exception {
        var r = send("POST", "/v1/acceso-estudiante/autenticar", null,
                "{\"correo\":\"" + alias + "@recorrido.prisaly.test\",\"contrasena\":\"" + CLAVE + "\"}");
        assertEquals(204, r.statusCode(), r.body());
        return r.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }
    private String bearer(String correo) throws Exception {
        var r = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private HttpResponse<String> get(String path, String cookie, String token) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) b.header("Cookie", cookie);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> put(UUID intento, UUID pregunta, UUID opcion, int revision, String cookie, String token) throws Exception {
        var b = HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/" + intento + "/respuestas/" + pregunta))
                .header("Origin", "http://localhost:4200").header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"idOpcionSeleccionada\":\"" + opcion + "\",\"revisionEsperada\":" + revision + "}"));
        if (cookie != null) b.header("Cookie", cookie);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> send(String method, String path, String cookie, String body) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).header("Origin", "http://localhost:4200")
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
