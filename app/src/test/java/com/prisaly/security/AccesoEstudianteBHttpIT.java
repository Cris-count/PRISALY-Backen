package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.application.service.PurgaAccesoEstudianteService;
import com.prisaly.m7.infrastructure.CifradorCodigoAccesoAesGcm;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-DD02-B4-B5-01 — CONTROLLED QA SCENARIO, NOT AN ACTIVATION. DD-02 alternative B over the real APP, PostgreSQL 16.15
 * with the optional candidates V014-V016 ({@code db/dd02b-propuesta}, contract {@code bd0052-v013-dd02b}) and a
 * Mailpit QA capture mailbox (never the institutional SMTP). Disposable random keys per run; recipients use the
 * {@code .test} domain. The cookie is not marked Secure here only because the test server is plain HTTP
 * ({@code cookie-segura=false}); the shipped default is Secure.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class AccesoEstudianteBHttpIT {
    static final String ORIGIN = AccesoEstudianteQa.ORIGIN;
    static final String FIXTURE = "ControlledPassword#123";
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    @SuppressWarnings("resource")
    static final GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.31.2")
            .withExposedPorts(1025, 8025).waitingFor(Wait.forListeningPorts(1025, 8025));
    static final String K_COD = AccesoEstudianteQa.K_COD, K_SES = AccesoEstudianteQa.K_SES, K_OUT = AccesoEstudianteQa.K_OUT;
    static {
        postgres.start();
        mailpit.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        AccesoEstudianteQa.propiedades(p);
        p.add("prisaly.m7.worker.enabled", () -> "false");
        p.add("prisaly.m7.codigo-acceso.worker.enabled", () -> "true");
        p.add("prisaly.m7.codigo-acceso.worker.lote", () -> "20");
        p.add("prisaly.m7.codigo-acceso.worker.intervalo-ms", () -> "3600000");
        p.add("prisaly.m7.correo.remitente", () -> "no-reply@prisaly.test");
        p.add("spring.mail.host", mailpit::getHost);
        p.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityCatalogService catalog;
    @Autowired ObjectMapper json;
    @Autowired CifradorCodigoAccesoAesGcm cifrador;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    @Autowired ConfiguracionCodigoAccesoM7.PurgaEntregas purgaCola;
    @Autowired PurgaAccesoEstudianteService purgaM1;
    final HttpClient http = HttpClient.newHttpClient();

    // ---------------------------------------------------------------- tests

    @Test
    void recorridoCompletoConEntregaRealAlBuzonDeCapturaQA(CapturedOutput salida) throws Exception {
        String correo = "e2e." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = estudiante(correo);
        UUID intento = cadenaConIntento(estudiante);

        var solicitud = post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", ORIGIN, null);
        assertEquals(202, solicitud.statusCode(), solicitud.body());
        String challenge = json.readTree(solicitud.body()).get("challengeId").asText();
        // Other tests of this shared context may leave pending rows; this challenge's own row is asserted below.
        assertTrue(worker.ejecutarAhora() >= 1, "the code worker delivered through SMTP");
        String codigo = codigoDelBuzon(correo);
        assertEquals("ENVIADA", jdbc.queryForObject("SELECT \"estado\" FROM \"EntregaCodigoAcceso\" WHERE \"challengeId\"=?::uuid", String.class, challenge));
        assertNull(jdbc.queryForObject("SELECT \"cargaCifrada\" FROM \"EntregaCodigoAcceso\" WHERE \"challengeId\"=?::uuid", byte[].class, challenge));

        var validacion = post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + challenge + "\",\"codigo\":\"" + codigo + "\"}", ORIGIN, null);
        assertEquals(204, validacion.statusCode(), validacion.body());
        String setCookie = validacion.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Strict") && setCookie.contains("Path=/api"));
        String cookie = setCookie.split(";", 2)[0];

        assertEquals(200, get("/v1/estudiantes/intentos/" + intento, cookie).statusCode());
        assertEquals(401, get("/v1/personal/me", cookie).statusCode(), "a student cookie is not a personnel identity");
        assertEquals(204, post("/v1/acceso-estudiante/cerrar-sesion", "{}", ORIGIN, cookie).statusCode());
        assertEquals(401, get("/v1/estudiantes/intentos/" + intento, cookie).statusCode());
        assertEquals(1, cuenta("SELECT count(*) FROM \"IntentoAccesoEstudiante\" WHERE \"idEstudiante\"=? AND \"evento\"='CIERRE' AND \"resultado\"='SESION_REVOCADA'", estudiante));
        assertEquals(401, post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + challenge + "\",\"codigo\":\"" + codigo + "\"}", ORIGIN, null).statusCode(), "single use");

        // No secret in clear anywhere: database rows, audit, logs.
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"IntentoAccesoEstudiante\" WHERE \"resultado\" LIKE ? OR \"origen\" LIKE ?", Integer.class, "%" + codigo + "%", "%" + codigo + "%"));
        assertFalse(salida.getAll().contains(codigo));
        assertFalse(salida.getAll().contains(cookie.split("=", 2)[1]));
        for (String k : List.of(K_COD, K_SES, K_OUT)) assertFalse(salida.getAll().contains(k.split(":", 2)[1]));
        assertFalse(salida.getAll().contains(correo), "the recipient is never logged");
    }

    @Test
    void laRespuestaNoRevelaSiExisteLaCuentaYElLimiteTampoco() throws Exception {
        String registrado = "enum." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        String inexistente = "nadie." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        estudiante(registrado);
        JsonNode a = json.readTree(post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + registrado + "\"}", ORIGIN, null).body());
        JsonNode b = json.readTree(post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + inexistente + "\"}", ORIGIN, null).body());
        assertEquals(a.size(), b.size());
        assertEquals(a.get("mensaje").asText(), b.get("mensaje").asText());
        assertEquals(36, b.get("challengeId").asText().length());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"EntregaCodigoAcceso\" WHERE \"challengeId\"=?::uuid", Integer.class, b.get("challengeId").asText()));
        var reenvioA = post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + registrado + "\"}", ORIGIN, null);
        var reenvioB = post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + inexistente + "\"}", ORIGIN, null);
        assertEquals(429, reenvioA.statusCode());
        assertEquals(429, reenvioB.statusCode());
        assertEquals(reenvioA.body(), reenvioB.body());
        assertTrue(reenvioA.headers().firstValue("Retry-After").isPresent() && reenvioB.headers().firstValue("Retry-After").isPresent());
        // A decoy challenge validates exactly like a wrong code.
        var señuelo = post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + b.get("challengeId").asText() + "\",\"codigo\":\"12345678\"}", ORIGIN, null);
        var errado = post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + a.get("challengeId").asText() + "\",\"codigo\":\"00000000\"}", ORIGIN, null);
        assertEquals(401, señuelo.statusCode());
        assertEquals(señuelo.body(), errado.body());
    }

    @Test
    void origenAusenteOAjenoEsRechazadoEnLasRutasQueCambianEstado() throws Exception {
        String correo = "origen." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        assertEquals(403, post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", null, null).statusCode());
        assertEquals(403, post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", "https://attacker.invalid", null).statusCode());
        assertEquals(403, post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + UUID.randomUUID() + "\",\"codigo\":\"12345678\"}", "null", null).statusCode());
        assertEquals(403, post("/v1/acceso-estudiante/cerrar-sesion", "{}", "https://attacker.invalid", null).statusCode());
        assertEquals(202, post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", ORIGIN, null).statusCode());
        for (String retirada : List.of("activar", "autenticar")) {
            assertTrue(post("/v1/acceso-estudiante/" + retirada, "{}", ORIGIN, null).statusCode() >= 403, retirada + " retired");
        }
    }

    @Test
    void validacionesConcurrentesDelMismoCodigoTienenUnSoloGanador() throws Exception {
        String correo = "conc." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        estudiante(correo);
        String challenge = json.readTree(post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", ORIGIN, null).body()).get("challengeId").asText();
        String codigo = codigoDeLaCola(challenge);
        int hilos = 8;
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(hilos);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                inicio.await();
                return post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + challenge + "\",\"codigo\":\"" + codigo + "\"}", ORIGIN, null).statusCode();
            }));
        }
        inicio.countDown();
        int ganadores = 0;
        for (var r : resultados) if (r.get() == 204) ganadores++; else assertEquals(401, r.get());
        pool.shutdown();
        assertEquals(1, ganadores);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"SesionEstudiante\" s JOIN \"Estudiante\" e ON e.\"idEstudiante\"=s.\"idEstudiante\" JOIN \"Usuario\" u ON u.\"idUsuario\"=e.\"idUsuario\" WHERE u.\"correo\"=?", Integer.class, correo));
    }

    @Test
    void cincoFallosAgotanElCodigoYUnoNuevoRevocaElAnterior() throws Exception {
        String correo = "agot." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        estudiante(correo);
        String challenge = json.readTree(post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", ORIGIN, null).body()).get("challengeId").asText();
        String codigo = codigoDeLaCola(challenge);
        String erroneo = codigo.equals("11111111") ? "22222222" : "11111111";
        for (int i = 0; i < 5; i++) {
            assertEquals(401, post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + challenge + "\",\"codigo\":\"" + erroneo + "\"}", ORIGIN, null).statusCode());
        }
        assertEquals(5, jdbc.queryForObject("SELECT \"intentosFallidos\" FROM \"CredencialAccesoEstudiante\" WHERE \"challengeId\"=?::uuid", Integer.class, challenge));
        assertEquals(401, post("/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + challenge + "\",\"codigo\":\"" + codigo + "\"}", ORIGIN, null).statusCode());
    }

    @Test
    void falloAlEncolarDeshaceLaCredencialYLaRespuestaEsLaMisma() throws Exception {
        String correo = "cola." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = estudiante(correo);
        jdbc.execute("ALTER TABLE \"EntregaCodigoAcceso\" RENAME TO \"EntregaCodigoAccesoFuera\"");
        try {
            var r = post("/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", ORIGIN, null);
            assertEquals(202, r.statusCode(), "DD02-12a: same answer");
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"CredencialAccesoEstudiante\" WHERE \"idEstudiante\"=?", Integer.class, estudiante));
        } finally {
            jdbc.execute("ALTER TABLE \"EntregaCodigoAccesoFuera\" RENAME TO \"EntregaCodigoAcceso\"");
        }
    }

    @Test
    void lasCabecerasDeIpDelClienteNoCambianElOrigenRegistrado() throws Exception {
        String correo = "xff." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = estudiante(correo);
        var req = HttpRequest.newBuilder(uri("/v1/acceso-estudiante/solicitar")).header("Origin", ORIGIN)
                .header("Content-Type", "application/json").header("X-Forwarded-For", "203.0.113.99")
                .header("X-Real-IP", "203.0.113.98").POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\"}")).build();
        assertEquals(202, http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
        String origen = jdbc.queryForObject("SELECT \"origen\" FROM \"IntentoAccesoEstudiante\" WHERE \"idEstudiante\"=? AND \"evento\"='SOLICITUD'", String.class, estudiante);
        assertFalse(origen.contains("203.0.113"), origen);
        // And the per-email limit applies whatever the forwarded header says.
        var segundo = HttpRequest.newBuilder(uri("/v1/acceso-estudiante/solicitar")).header("Origin", ORIGIN)
                .header("Content-Type", "application/json").header("X-Forwarded-For", "198.51.100.1")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\"}")).build();
        assertEquals(429, http.send(segundo, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void laPurgaEliminaLoVencidoSegunLaRetencionAprobada() throws Exception {
        String correo = "purga." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        UUID estudiante = estudiante(correo);
        UUID usuario = jdbc.queryForObject("SELECT \"idUsuario\" FROM \"Estudiante\" WHERE \"idEstudiante\"=?", UUID.class, estudiante);
        UUID vieja = UUID.randomUUID(), reciente = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "CredencialAccesoEstudiante" ("idCredencial","idUsuario","idEstudiante","challengeId","codigoHmac","kidCodigo",
                  "fechaCreacion","fechaExpiracion","fechaConsumo") VALUES (?,?,?,?,'\\x00','cod-qa',now()-interval '2 days',now()-interval '2 days'+interval '5 minutes',now()-interval '2 days'+interval '1 minute')""",
                UUID.randomUUID(), usuario, estudiante, vieja);
        jdbc.update("""
                INSERT INTO "CredencialAccesoEstudiante" ("idCredencial","idUsuario","idEstudiante","challengeId","codigoHmac","kidCodigo",
                  "fechaCreacion","fechaExpiracion","fechaConsumo") VALUES (?,?,?,?,'\\x00','cod-qa',now()-interval '1 hour',now()-interval '1 hour'+interval '5 minutes',now()-interval '59 minutes')""",
                UUID.randomUUID(), usuario, estudiante, reciente);
        jdbc.update("""
                INSERT INTO "SesionEstudiante" ("idSesionEstudiante","idUsuario","idEstudiante","sesionTokenHmac","kidSesion","fechaCreacion","fechaExpiracion","fechaUltimaActividad")
                VALUES (?,?,?,?,'ses-qa',now()-interval '40 days',now()-interval '40 days'+interval '8 hours',now()-interval '40 days')""",
                UUID.randomUUID(), usuario, estudiante, new byte[] {9, 9, (byte) estudiante.hashCode()});
        jdbc.update("INSERT INTO \"IntentoAccesoEstudiante\" (\"idIntentoAcceso\",\"idEstudiante\",\"evento\",\"resultado\",\"origen\",\"fecha\") VALUES (?,?,'SOLICITUD','X','o',now()-interval '400 days')", UUID.randomUUID(), estudiante);
        String pendiente = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO "EntregaCodigoAcceso" ("idEntrega","challengeId","claveIdempotencia","cargaCifrada","nonce","kidCifrado","estado","fechaCreacion","fechaExpiracion")
                VALUES (?,?::uuid,?, '\\x01','\\x02','out-qa','PENDIENTE', now()-interval '6 minutes', now()-interval '1 minute')""",
                UUID.randomUUID(), pendiente, "purga:" + pendiente);

        purgaM1.purgar();
        purgaCola.ejecutar();

        assertEquals(0, cuenta("SELECT count(*) FROM \"CredencialAccesoEstudiante\" WHERE \"challengeId\"=?", vieja));
        assertEquals(1, cuenta("SELECT count(*) FROM \"CredencialAccesoEstudiante\" WHERE \"challengeId\"=?", reciente));
        assertEquals(0, cuenta("SELECT count(*) FROM \"SesionEstudiante\" WHERE \"idEstudiante\"=?", estudiante));
        assertEquals(0, cuenta("SELECT count(*) FROM \"IntentoAccesoEstudiante\" WHERE \"idEstudiante\"=? AND \"fecha\" < now()-interval '365 days'", estudiante));
        Map<String, Object> fila = jdbc.queryForMap("SELECT \"estado\", \"cargaCifrada\" FROM \"EntregaCodigoAcceso\" WHERE \"challengeId\"=?::uuid", pendiente);
        assertEquals("CANCELADA_VENCIDA", fila.get("estado"));
        assertNull(fila.get("cargaCifrada"));
    }

    // ---------------------------------------------------------------- helpers

    private int cuenta(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    /** Active student account; the fixture staff password is irrelevant to B, which never uses it. Returns idEstudiante. */
    private UUID estudiante(String correo) {
        String rol = "EstB-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Fixture QA DD-02 B");
        UUID usuario = catalog.registerUser("Estudiante QA", correo, FIXTURE, rol).getIdUsuario();
        UUID estudiante = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);
        return estudiante;
    }

    /** Minimal academic chain with an EnCurso attempt (first start stays blocked by pending decisions). */
    private UUID cadenaConIntento(UUID estudiante) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole("DocB-" + s, "Fixture");
        UUID docente = catalog.registerUser("Docente QA", "docb." + s + "@prisaly.test", FIXTURE, "DocB-" + s).getIdUsuario();
        UUID caso = UUID.randomUUID(), sesion = UUID.randomUUID(), conv = UUID.randomUUID(),
                part = UUID.randomUUID(), intento = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,600,'Publicado')", caso, docente, "Caso B " + s);
        jdbc.update("INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\",\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\") VALUES (?,?,?,'Espacio',now()-interval '1 hour',now()+interval '1 hour',3600)", sesion, caso, docente);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)", conv, sesion, estudiante);
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", part, conv);
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\") VALUES (?,?,1,now(),now()+interval '1 hour','EnCurso')", intento, part);
        return intento;
    }

    private String codigoDeLaCola(String challenge) { return AccesoEstudianteQa.codigoDeLaCola(jdbc, cifrador, challenge); }

    /** QA capture mailbox: reads the message delivered by M7 over SMTP to Mailpit and extracts the 8-digit code. */
    private String codigoDelBuzon(String correo) throws Exception {
        String base = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
        for (int i = 0; i < 50; i++) {
            JsonNode lista = json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/search?query=to:" + correo)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            if (lista.get("messages") != null && lista.get("messages").size() > 0) {
                JsonNode m = lista.get("messages").get(0);
                assertFalse(m.get("Subject").asText().matches(".*\\d{8}.*"), "never in the subject");
                String texto = json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/message/" + m.get("ID").asText())).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body()).get("Text").asText();
                Matcher codigo = Pattern.compile("\\b(\\d{8})\\b").matcher(texto);
                assertTrue(codigo.find(), "code in the body");
                return codigo.group(1);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No message in the QA capture mailbox for the test recipient");
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private HttpResponse<String> post(String path, String body, String origin, String cookie) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) b.header("Origin", origin);
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
