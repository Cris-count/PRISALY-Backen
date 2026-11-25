package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m1.application.service.IdentityCatalogService;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * D-05 (DC-77) shared fixture: real server, PostgreSQL 16.15 and Mailpit v1.31.2 (the QA mail sink). Tokens are read
 * from the delivered mail, exactly as a teacher would; they are never printed by the tests.
 */
abstract class ActivacionDocenteItBase {
    static final String ROL_DOCENTE = "DocenteIT";
    static final String CLAVE_ADMIN = "Fixture#Clave-2026";
    static final Pattern TOKEN = Pattern.compile("(?m)^([A-Za-z0-9_-]{43})$");

    /** Each subclass owns its containers: contexts with and without the candidate schema must not share a database. */
    static PostgreSQLContainer nuevoPostgres() {
        PostgreSQLContainer c = new PostgreSQLContainer("postgres:16.15");
        c.start();
        return c;
    }

    @SuppressWarnings("resource")
    static GenericContainer<?> nuevoMailpit() {
        GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.2"))
                .withExposedPorts(1025, 8025).waitingFor(Wait.forListeningPorts(1025, 8025));
        c.start();
        return c;
    }

    abstract GenericContainer<?> mailpit();

    static void base(DynamicPropertyRegistry r, PostgreSQLContainer postgres, GenericContainer<?> mailpit) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        r.add("prisaly.m1.administracion.rol-docente", () -> ROL_DOCENTE);
        r.add("spring.mail.host", mailpit::getHost);
        r.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    static void activacionEncendida(DynamicPropertyRegistry r, int maxPorToken, int maxPorOrigen) {
        r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/d05-propuesta");
        r.add("prisaly.m4.schema-contract", () -> "bd0052-v013-d05");
        r.add("prisaly.m1.activacion-docente.enabled", () -> "true");
        r.add("prisaly.m1.activacion-docente.vigencia", () -> "PT1H");
        r.add("prisaly.m1.activacion-docente.remitente", () -> "no-responder@prisaly.test");
        r.add("prisaly.m1.activacion-docente.intentos.max-por-token", () -> maxPorToken);
        r.add("prisaly.m1.activacion-docente.intentos.max-por-origen", () -> maxPorOrigen);
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    UUID admin;
    String tokenAdmin;

    void prepararAdministrador() throws Exception {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        if (jdbc.queryForObject("SELECT count(*) FROM \"Rol\" WHERE \"nombre\"=?", Integer.class, ROL_DOCENTE) == 0) {
            catalog.createRole(ROL_DOCENTE, "Rol docente de prueba");
        }
        String correo = "admin." + UUID.randomUUID() + "@prisaly.test";
        admin = catalog.registerUser("Admin IT", correo, CLAVE_ADMIN, IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        tokenAdmin = login(correo, CLAVE_ADMIN);
    }

    URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) { b.header("Content-Type", "application/json"); }
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    String login(String correo, String clave) throws Exception {
        var r = send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + correo + "\",\"password\":\"" + clave + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }

    HttpResponse<String> activar(String token, String clave) throws Exception {
        return send("POST", "/v1/personal/activar", null, "{\"token\":\"" + token + "\",\"password\":\"" + clave + "\"}");
    }

    UUID alta(String correo) throws Exception {
        var r = send("POST", "/v1/admin/docentes", tokenAdmin, "{\"nombre\":\"Docente D05\",\"correo\":\"" + correo + "\"}");
        assertEquals(201, r.statusCode(), r.body());
        JsonNode body = json.readTree(r.body());
        assertEquals(4, body.size(), "UsuarioDocente contract unchanged");
        return UUID.fromString(body.get("idUsuario").asText());
    }

    static void error(HttpResponse<String> r, int status, String code) {
        assertEquals(status, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"code\":\"" + code + "\""), r.body());
        assertFalse(r.body().matches("(?is).*(exception|jdbc:|passwordhash|stacktrace|hashtoken).*"), r.body());
    }

    // ---------- Mailpit ----------
    private JsonNode mailpitGet(String path) throws Exception {
        var r = http.send(HttpRequest.newBuilder(URI.create("http://" + mailpit().getHost() + ":"
                + mailpit().getMappedPort(8025) + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body());
    }

    int correosPara(String correo) throws Exception {
        return mailpitGet("/api/v1/search?query=" + URLEncoder.encode("to:\"" + correo + "\"", StandardCharsets.UTF_8))
                .get("messages").size();
    }

    /** Waits for the {@code n}-th invitation of {@code correo} and returns its code (newest first in Mailpit). */
    String tokenDelCorreo(String correo, int n) throws Exception {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (correosPara(correo) < n) {
            assertTrue(System.nanoTime() < limite, "invitation not delivered");
            Thread.sleep(100);
        }
        JsonNode mensajes = mailpitGet("/api/v1/search?query="
                + URLEncoder.encode("to:\"" + correo + "\"", StandardCharsets.UTF_8)).get("messages");
        assertEquals(n, mensajes.size());
        JsonNode mensaje = mailpitGet("/api/v1/message/" + mensajes.get(0).get("ID").asText());
        assertEquals("PRISALY: activa tu cuenta docente", mensaje.get("Subject").asText());
        String texto = mensaje.get("Text").asText();
        assertFalse(texto.matches("(?is).*contrase[nñ]a\\s*:.*"), "the mail never carries a password");
        Matcher m = TOKEN.matcher(texto);
        assertTrue(m.find(), "the mail carries the activation code");
        return m.group(1);
    }
}
