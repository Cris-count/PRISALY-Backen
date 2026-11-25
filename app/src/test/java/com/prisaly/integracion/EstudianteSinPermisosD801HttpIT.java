package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

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
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * D-80-1 (DC-BE-CIERRE-SEGURIDAD-INTEGRACION-81) over the real server and PostgreSQL 16.15, official schema only:
 * a student registered by P-78 receives no personnel permission (403-free, 422 PERMISSION_NOT_ALLOWED_FOR_STUDENT,
 * nothing written), a pre-existing assignment grants nothing at runtime, and teachers keep their behaviour.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EstudianteSinPermisosD801HttpIT {
    static final String CLAVE = "Fixture#Clave-2026";
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        r.add("prisaly.m1.alta-estudiantes.enabled", () -> "true"); // QA only
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    UUID admin;
    String tokenAdmin;

    @BeforeEach void preparar() throws Exception {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        jdbc.execute(Files.readString(Path.of("../docs/provision/pd01-p78-rol-estudiante.sql")));
        for (String codigo : new String[] {"GESTIONAR_SESION", "CREAR_CASO", "CONSULTAR_CASO", "PERMISO_FUTURO_D801"}) {
            jdbc.update("INSERT INTO \"Permiso\" (\"codigo\",\"nombre\") VALUES (?,?) ON CONFLICT (\"codigo\") DO NOTHING",
                    codigo, codigo);
        }
        String correo = "admin81." + UUID.randomUUID() + "@prisaly.test";
        admin = catalog.registerUser("Admin 81", correo, CLAVE, IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        tokenAdmin = login(correo);
    }

    @Test
    void elEstudianteDadoDeAltaNoRecibeNingunPermisoDePersonalYNadaSeEscribe() throws Exception {
        var alta = send("POST", "/v1/admin/estudiantes", tokenAdmin,
                "{\"nombre\":\"Estudiante 81\",\"correo\":\"est81." + UUID.randomUUID() + "@prisaly.test\"}");
        assertEquals(201, alta.statusCode(), alta.body());
        UUID estudiante = UUID.fromString(json.readTree(alta.body()).get("idUsuario").asString());
        String antes = huella(estudiante);
        for (String codigo : new String[] {"GESTIONAR_SESION", "CREAR_CASO", "CONSULTAR_CASO", "PERMISO_FUTURO_D801"}) {
            var r = send("PUT", "/v1/admin/usuarios/" + estudiante + "/permisos/" + permiso(codigo), tokenAdmin, null);
            assertEquals(422, r.statusCode(), codigo + " " + r.body());
            assertTrue(r.body().contains("\"code\":\"PERMISSION_NOT_ALLOWED_FOR_STUDENT\""), r.body());
        }
        assertEquals(antes, huella(estudiante), "rejected attempts do not modify the database");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?",
                Integer.class, estudiante));
    }

    @Test
    void unaAsignacionPreexistenteNoOtorgaPrivilegiosEnTiempoDeEjecucion() throws Exception {
        // Legacy data simulated with a fixture: a personnel-style account later linked to a student row, with an
        // enabled GESTIONAR_SESION row created before the guard existed.
        String correo = "legado81." + UUID.randomUUID() + "@prisaly.test";
        String rol = "R81-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente fixture 81");
        UUID cuenta = catalog.registerUser("Legado 81", correo, CLAVE, rol).getIdUsuario();
        catalog.assignPermission(cuenta, permiso("GESTIONAR_SESION"), admin);
        String token = login(correo);
        assertEquals(200, send("GET", "/m4/sesiones", token, null).statusCode(), "before: a teacher with the permission");

        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), cuenta);
        var me = send("GET", "/v1/personal/me", token, null);
        assertEquals(200, me.statusCode(), me.body());
        assertEquals(0, json.readTree(me.body()).get("permisos").size(), me.body());
        var sesiones = send("GET", "/m4/sesiones", token, null);
        assertEquals(403, sesiones.statusCode(), sesiones.body());
        assertTrue(sesiones.body().contains("M4-SIN-PERMISO"), sesiones.body());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT \"habilitado\" FROM \"UsuarioPermiso\" WHERE"
                + " \"idUsuario\"=? AND \"idPermiso\"=?", Boolean.class, cuenta, permiso("GESTIONAR_SESION")),
                "the stored row is untouched; only its effect is denied");
        assertEquals(204, send("DELETE", "/v1/admin/usuarios/" + cuenta + "/permisos/" + permiso("GESTIONAR_SESION"),
                tokenAdmin, null).statusCode(), "the administrator can still revoke it");
    }

    @Test
    void elDocenteConservaElComportamientoAutorizado() throws Exception {
        String correo = "doc81." + UUID.randomUUID() + "@prisaly.test";
        String rol = "R81-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente fixture 81");
        UUID docente = catalog.registerUser("Docente 81", correo, CLAVE, rol).getIdUsuario();
        for (String codigo : new String[] {"GESTIONAR_SESION", "CREAR_CASO"}) {
            var r = send("PUT", "/v1/admin/usuarios/" + docente + "/permisos/" + permiso(codigo), tokenAdmin, null);
            assertEquals(200, r.statusCode(), codigo + " " + r.body());
        }
        String token = login(correo);
        assertEquals(200, send("GET", "/m4/sesiones", token, null).statusCode());
        var me = send("GET", "/v1/personal/me", token, null);
        assertEquals(200, me.statusCode(), me.body());
        assertTrue(me.body().contains("GESTIONAR_SESION") && me.body().contains("CREAR_CASO"), me.body());
    }

    private String huella(UUID usuario) {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM \"UsuarioPermiso\") || '/' || (SELECT count(*) FROM"
                + " \"Usuario\") || '/' || (SELECT count(*) FROM \"Estudiante\") || '/' || coalesce((SELECT"
                + " \"fechaActualizacion\"::text FROM \"Usuario\" WHERE \"idUsuario\"=?),'-')", String.class, usuario);
    }

    private Integer permiso(String codigo) {
        return jdbc.queryForObject("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\"=?", Integer.class, codigo);
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) { b.header("Content-Type", "application/json"); }
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String correo) throws Exception {
        var r = send("POST", "/v1/personal/autenticar", null,
                "{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asString();
    }
}
