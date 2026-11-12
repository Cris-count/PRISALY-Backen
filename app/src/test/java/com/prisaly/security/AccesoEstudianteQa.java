package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.prisaly.m7.infrastructure.CifradorCodigoAccesoAesGcm;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * QA-only support for DD-02 alternative B (DC-DD02-B4-B5-01). Enables the candidate ONLY in isolated fixtures with
 * disposable random K-COD / K-SES / K-OUT per JVM, and obtains a student session through the real path:
 * {@code /solicitar} → encrypted Outbox row (M7) → {@code /validar}. The code is read by decrypting that real row with
 * the context's own K-OUT cipher; no SMTP is used and nothing is bypassed.
 */
final class AccesoEstudianteQa {
    static final String ORIGIN = "http://localhost:4200";
    static final String K_COD = clave("cod-qa"), K_SES = clave("ses-qa"), K_OUT = clave("out-qa");
    private static final Pattern CHALLENGE = Pattern.compile("\"challengeId\"\\s*:\\s*\"([0-9a-f-]{36})\"");

    private AccesoEstudianteQa() {
    }

    static String clave(String kid) {
        byte[] v = new byte[32];
        new SecureRandom().nextBytes(v);
        return kid + ":" + Base64.getEncoder().encodeToString(v);
    }

    /** Candidate B schema (V001-V009 + V013 + dd02b V014-V016) and the flag, ONLY for the calling fixture. */
    static void propiedades(DynamicPropertyRegistry p) {
        p.add("spring.flyway.locations", () -> {
            Path base = Path.of("src/main/resources/db").toAbsolutePath();
            if (!Files.isDirectory(base)) base = Path.of("app/src/main/resources/db").toAbsolutePath();
            return "filesystem:" + base.resolve("migration") + ",filesystem:" + base.resolve("dd02b-propuesta");
        });
        p.add("prisaly.m4.schema-contract", () -> "bd0052-v013-dd02b");
        p.add("prisaly.m1.student-access.enabled", () -> "true");
        p.add("prisaly.m1.student-access.claves.codigo", () -> K_COD);
        p.add("prisaly.m1.student-access.claves.sesion", () -> K_SES);
        p.add("prisaly.m7.codigo-acceso.clave-cifrado", () -> K_OUT);
        // Plain-HTTP test server only; the shipped default is Secure.
        p.add("prisaly.m1.student-access.cookie-segura", () -> "false");
        p.add("prisaly.m1.student-access.limites.solicitudes-por-origen", () -> "1000");
        p.add("prisaly.m1.student-access.limites.validaciones-fallidas-por-origen", () -> "1000");
        p.add("prisaly.cors.allowed-origins", () -> ORIGIN);
        p.add("prisaly.m7.codigo-acceso.retencion.intervalo-ms", () -> "3600000");
        p.add("prisaly.m1.student-access.retencion.intervalo-ms", () -> "3600000");
    }

    static String solicitar(HttpClient http, int port, String correo) throws Exception {
        var r = post(http, port, "/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}");
        assertEquals(202, r.statusCode(), r.body());
        Matcher m = CHALLENGE.matcher(r.body());
        if (!m.find()) throw new AssertionError("challengeId missing");
        return m.group(1);
    }

    /** Reads the code by decrypting the real encrypted Outbox row with the test K-OUT. */
    static String codigoDeLaCola(JdbcTemplate jdbc, CifradorCodigoAccesoAesGcm cifrador, String challenge) {
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT \"cargaCifrada\", \"nonce\", \"kidCifrado\" FROM \"EntregaCodigoAcceso\" WHERE \"challengeId\"=?::uuid", challenge);
        return cifrador.descifrar(challenge, (byte[]) fila.get("cargaCifrada"), (byte[]) fila.get("nonce"), (String) fila.get("kidCifrado"))
                .orElseThrow().codigo();
    }

    /** Full B login; returns the {@code name=value} session cookie pair. */
    static HttpResponse<String> validar(HttpClient http, int port, JdbcTemplate jdbc, CifradorCodigoAccesoAesGcm cifrador,
            String correo) throws Exception {
        String challenge = solicitar(http, port, correo);
        String codigo = codigoDeLaCola(jdbc, cifrador, challenge);
        return post(http, port, "/v1/acceso-estudiante/validar", "{\"challengeId\":\"" + challenge + "\",\"codigo\":\"" + codigo + "\"}");
    }

    static String login(HttpClient http, int port, JdbcTemplate jdbc, CifradorCodigoAccesoAesGcm cifrador, String correo) throws Exception {
        var r = validar(http, port, jdbc, cifrador, correo);
        assertEquals(204, r.statusCode(), r.body());
        return r.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    private static HttpResponse<String> post(HttpClient http, int port, String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).header("Origin", ORIGIN)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
