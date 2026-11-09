package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;
import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.UUID;
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

/** Real HTTP + PostgreSQL. Candidate DD-02 enabled ONLY in this isolated acceptance fixture. */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StudentSessionControlledHttpIT {
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
        p.add("prisaly.m4.schema-contract", () -> "bd0052-dd02-v012");
        p.add("prisaly.m1.student-access.enabled", () -> "true");
        p.add("prisaly.m1.student-access.hmac-secret", () -> "controlled-http-fixture-key-not-production");
        p.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
    }
    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test void validCookieReachesM5WithoutPersonnelBearerAndRevocationTakesEffect() throws Exception {
        String cookie = login(student());
        String path = "/v1/estudiantes/intentos/" + UUID.randomUUID();
        var response = get(path, cookie);
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("INTENTO_NO_ENCONTRADO"));
        assertEquals(401, get("/v1/personal/me", cookie).statusCode());
        assertEquals(204, send("POST", "/v1/acceso-estudiante/cerrar-sesion", cookie, "{}").statusCode());
        assertEquals(401, get(path, cookie).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Intento\"", Integer.class));
    }

    @Test void inactiveStudentAndExpiredSessionAreRejected() throws Exception {
        String email = student();
        String cookie = login(email);
        jdbc.update("UPDATE \"Usuario\" SET \"estado\"='Inactivo' WHERE \"correo\"=?", email);
        assertEquals(401, get("/v1/estudiantes/intentos/" + UUID.randomUUID(), cookie).statusCode());
        String active = student();
        String expiredCookie = login(active);
        jdbc.update("""
                UPDATE "SesionEstudiante" SET "fechaCreacion"=now()-interval '10 hours',
                  "fechaUltimaActividad"=now()-interval '3 hours', "fechaExpiracion"=now()-interval '2 hours'
                WHERE "idUsuario"=(SELECT "idUsuario" FROM "Usuario" WHERE "correo"=?)
                """, active);
        assertEquals(401, get("/v1/estudiantes/intentos/" + UUID.randomUUID(), expiredCookie).statusCode());
    }

    @Test void browserCredentialsAreRestrictedAndCookieHasSecurityAttributes() throws Exception {
        var response = send("POST", "/v1/acceso-estudiante/autenticar", null,
                "{\"correo\":\"" + student() + "\",\"contrasena\":\"ControlledPassword#123\"}");
        assertEquals(204, response.statusCode());
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Path=/api"));
        var bad = HttpRequest.newBuilder(uri("/v1/acceso-estudiante/cerrar-sesion"))
                .header("Origin", "https://attacker.invalid").header("Cookie", cookie.split(";", 2)[0])
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(403, http.send(bad, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private String student() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String role = "StudentHttp-" + suffix;
        String email = suffix + "@student-http.prisaly.test";
        catalog.createRole(role, "Controlled student fixture");
        var user = catalog.registerUser("Student fixture", email, "ControlledPassword#123", role);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), user.getIdUsuario());
        return email;
    }
    private String login(String email) throws Exception {
        var r = send("POST", "/v1/acceso-estudiante/autenticar", null,
                "{\"correo\":\"" + email + "\",\"contrasena\":\"ControlledPassword#123\"}");
        assertEquals(204, r.statusCode());
        return r.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> send(String method, String path, String cookie, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).header("Origin", "http://localhost:4200")
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
