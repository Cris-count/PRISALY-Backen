package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-M1-04 over the real server and PostgreSQL 16.15: administrative API (DM1-04), DM1-02/02b, revocation effective on
 * the next request, password policy on change (DM1-08) and repeated-attempt protection (DM1-06).
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdministracionM1HttpIT {
    static final String ROL_DOCENTE = "DocenteIT";
    static final String CLAVE = "Fixture#Clave-2026";
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        r.add("prisaly.m1.administracion.rol-docente", () -> ROL_DOCENTE);
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    UUID admin;
    String tokenAdmin;

    @BeforeEach void administrador() throws Exception {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        if (jdbc.queryForObject("SELECT count(*) FROM \"Rol\" WHERE \"nombre\"=?", Integer.class, ROL_DOCENTE) == 0) {
            catalog.createRole(ROL_DOCENTE, "Rol docente de prueba");
        }
        for (String codigo : new String[] {"CREAR_CASO", "GESTIONAR_SESION", "CONSULTAR_CASO", "PUBLICAR_CASO", "ARCHIVAR_CASO"}) {
            jdbc.update("INSERT INTO \"Permiso\" (\"codigo\",\"nombre\") VALUES (?,?) ON CONFLICT (\"codigo\") DO NOTHING", codigo, codigo);
        }
        String correo = "admin." + UUID.randomUUID() + "@prisaly.test";
        admin = catalog.registerUser("Admin IT", correo, CLAVE, IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        tokenAdmin = login(correo, CLAVE);
    }

    // ---------- helpers ----------
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) { b.header("Content-Type", "application/json"); }
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String login(String correo, String clave) throws Exception {
        var r = send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + correo + "\",\"password\":\"" + clave + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }
    private int idPermiso(String codigo) {
        return jdbc.queryForObject("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\"=?", Integer.class, codigo);
    }
    private UUID docenteConClave(String correo) {
        return catalog.registerUser("Docente IT", correo, CLAVE, ROL_DOCENTE).getIdUsuario();
    }
    private static void error(HttpResponse<String> r, int status, String code) {
        assertEquals(status, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"code\":\"" + code + "\""), r.body());
        assertFalse(r.body().matches("(?is).*(exception|jdbc:|passwordhash|stacktrace).*"), r.body());
    }

    // ---------- DM1-04 ----------
    @Test void altaDeDocenteSinCredencialUtilizable() throws Exception {
        String correo = "nuevo." + UUID.randomUUID() + "@prisaly.test";
        var r = send("POST", "/v1/admin/docentes", tokenAdmin, "{\"nombre\":\"Docente Nuevo\",\"correo\":\"" + correo + "\"}");
        assertEquals(201, r.statusCode(), r.body());
        JsonNode body = json.readTree(r.body());
        assertEquals(correo, body.get("correo").asText());
        assertEquals("Activo", body.get("estado").asText());
        assertEquals(4, body.size(), "UsuarioDocente has exactly idUsuario, nombre, correo, estado");
        assertFalse(r.body().toLowerCase().contains("password"));
        UUID id = UUID.fromString(body.get("idUsuario").asText());
        assertNull(jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, id));
        assertEquals(ROL_DOCENTE, jdbc.queryForObject("SELECT r.\"nombre\" FROM \"Usuario\" u JOIN \"Rol\" r ON r.\"idRol\"=u.\"idRol\" WHERE u.\"idUsuario\"=?", String.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, id));
        error(send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}"), 401, "AUTH_FAILED");
        error(send("POST", "/v1/admin/docentes", tokenAdmin, "{\"nombre\":\"Otro\",\"correo\":\"" + correo.toUpperCase() + "\"}"), 409, "DUPLICATE_EMAIL");
        error(send("POST", "/v1/admin/docentes", tokenAdmin, "{\"nombre\":\"\",\"correo\":\"no-es-correo\"}"), 400, "VALIDATION_ERROR");
        error(send("POST", "/v1/admin/docentes", tokenAdmin, "{no json"), 400, "VALIDATION_ERROR");
    }

    @Test void consultasDeRolesYPermisos() throws Exception {
        var roles = send("GET", "/v1/admin/roles", tokenAdmin, null);
        assertEquals(200, roles.statusCode());
        assertTrue(roles.body().contains("\"nombre\":\"ADMINISTRADOR\""), roles.body());
        var permisos = send("GET", "/v1/admin/permisos", tokenAdmin, null);
        assertEquals(200, permisos.statusCode());
        assertTrue(permisos.body().contains("\"codigo\":\"CREAR_CASO\"") && permisos.body().contains("\"codigo\":\"GESTIONAR_SESION\""));
        assertTrue(json.readTree(permisos.body()).get("items").isArray());
    }

    @Test void asignarConsultarRevocarYReasignar() throws Exception {
        UUID docente = docenteConClave("d." + UUID.randomUUID() + "@prisaly.test");
        int crear = idPermiso("CREAR_CASO");
        String ruta = "/v1/admin/usuarios/" + docente + "/permisos/" + crear;
        var asignada = send("PUT", ruta, tokenAdmin, null);
        assertEquals(200, asignada.statusCode(), asignada.body());
        assertTrue(json.readTree(asignada.body()).get("habilitado").asBoolean());
        assertEquals(admin, jdbc.queryForObject("SELECT \"idAdministradorAsignador\" FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?", UUID.class, docente, crear));
        error(send("PUT", ruta, tokenAdmin, null), 409, "DUPLICATE_ASSIGNMENT");
        assertTrue(send("GET", "/v1/admin/usuarios/" + docente + "/permisos", tokenAdmin, null).body().contains("CREAR_CASO"));
        assertEquals(204, send("DELETE", ruta, tokenAdmin, null).statusCode());
        assertFalse(send("GET", "/v1/admin/usuarios/" + docente + "/permisos", tokenAdmin, null).body().contains("CREAR_CASO"));
        error(send("DELETE", ruta, tokenAdmin, null), 404, "PERMISSION_NOT_FOUND");
        // A revoked assignment can be granted again (re-enabled, same row).
        assertEquals(200, send("PUT", ruta, tokenAdmin, null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=? AND \"habilitado\"", Integer.class, docente, crear));
        error(send("PUT", "/v1/admin/usuarios/" + UUID.randomUUID() + "/permisos/" + crear, tokenAdmin, null), 404, "USER_NOT_FOUND");
        error(send("PUT", "/v1/admin/usuarios/" + docente + "/permisos/999999", tokenAdmin, null), 404, "PERMISSION_NOT_FOUND");
        error(send("GET", "/v1/admin/usuarios/" + UUID.randomUUID() + "/permisos", tokenAdmin, null), 404, "USER_NOT_FOUND");
        error(send("PUT", "/v1/admin/usuarios/no-es-uuid/permisos/" + crear, tokenAdmin, null), 400, "VALIDATION_ERROR");
    }

    @Test void laRevocacionSeAplicaEnLaSiguientePeticion() throws Exception {
        String correo = "rev." + UUID.randomUUID() + "@prisaly.test";
        UUID docente = docenteConClave(correo);
        int crear = idPermiso("CREAR_CASO");
        assertEquals(200, send("PUT", "/v1/admin/usuarios/" + docente + "/permisos/" + crear, tokenAdmin, null).statusCode());
        String token = login(correo, CLAVE);
        assertEquals(200, send("GET", "/v1/personal/autorizacion/crear-caso", token, null).statusCode());
        assertEquals(204, send("DELETE", "/v1/admin/usuarios/" + docente + "/permisos/" + crear, tokenAdmin, null).statusCode());
        assertEquals(403, send("GET", "/v1/personal/autorizacion/crear-caso", token, null).statusCode());
    }

    @Test void sinCredencialDocenteYAdministradorInactivoNoAdministran() throws Exception {
        for (String[] ruta : new String[][] {{"GET", "/v1/admin/roles"}, {"GET", "/v1/admin/permisos"},
                {"PUT", "/v1/admin/usuarios/" + admin + "/permisos/1"}, {"DELETE", "/v1/admin/usuarios/" + admin + "/permisos/1"}}) {
            error(send(ruta[0], ruta[1], null, null), 401, "UNAUTHENTICATED");
        }
        String correo = "sinpriv." + UUID.randomUUID() + "@prisaly.test";
        UUID docente = docenteConClave(correo);
        String token = login(correo, CLAVE);
        error(send("GET", "/v1/admin/roles", token, null), 403, "ADMIN_REQUIRED");
        error(send("PUT", "/v1/admin/usuarios/" + docente + "/permisos/" + idPermiso("CREAR_CASO"), token, null), 403, "ADMIN_REQUIRED");
        error(send("POST", "/v1/admin/docentes", token, "{\"nombre\":\"X\",\"correo\":\"x." + UUID.randomUUID() + "@prisaly.test\"}"), 403, "ADMIN_REQUIRED");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, docente));
        String correoAdmin2 = "admin2." + UUID.randomUUID() + "@prisaly.test";
        UUID admin2 = catalog.registerUser("Admin 2", correoAdmin2, CLAVE, IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        String token2 = login(correoAdmin2, CLAVE);
        catalog.deactivateUser(admin2);
        error(send("GET", "/v1/admin/roles", token2, null), 401, "UNAUTHENTICATED");
    }

    @Test void autoasignacionPermitidaYProhibidaDm102b() throws Exception {
        assertEquals(200, send("PUT", "/v1/admin/usuarios/" + admin + "/permisos/" + idPermiso("CREAR_CASO"), tokenAdmin, null).statusCode());
        assertEquals(200, send("PUT", "/v1/admin/usuarios/" + admin + "/permisos/" + idPermiso("GESTIONAR_SESION"), tokenAdmin, null).statusCode());
        for (String codigo : new String[] {"PUBLICAR_CASO", "ARCHIVAR_CASO"}) {
            error(send("PUT", "/v1/admin/usuarios/" + admin + "/permisos/" + idPermiso(codigo), tokenAdmin, null), 403,
                    "PERMISSION_SELF_ASSIGNMENT_NOT_ALLOWED");
        }
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, admin));
    }

    @Test void estudianteNoRecibePermisosControlados() throws Exception {
        UUID estudiante = docenteConClave("est." + UUID.randomUUID() + "@prisaly.test");
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), estudiante);
        error(send("PUT", "/v1/admin/usuarios/" + estudiante + "/permisos/" + idPermiso("CONSULTAR_CASO"), tokenAdmin, null), 422,
                "PERMISSION_NOT_ALLOWED_FOR_STUDENT");
        String correoEst = "est2." + UUID.randomUUID() + "@prisaly.test";
        UUID est2 = docenteConClave(correoEst);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), est2);
        error(send("GET", "/v1/admin/roles", login(correoEst, CLAVE), null), 403, "ADMIN_REQUIRED");
    }

    // ---------- DM1-08 ----------
    @Test void cambioDeContrasenaAplicaLaPolitica() throws Exception {
        String correo = "clave." + UUID.randomUUID() + "@prisaly.test";
        docenteConClave(correo);
        String token = login(correo, CLAVE);
        String ruta = "/v1/personal/contrasena";
        error(send("PUT", ruta, token, "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"corta\"}"), 422, "PASSWORD_TOO_SHORT");
        error(send("PUT", ruta, token, "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"" + "a".repeat(73) + "\"}"), 422, "PASSWORD_TOO_LONG");
        error(send("PUT", ruta, token, "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"Password1234\"}"), 422, "PASSWORD_COMMON");
        error(send("PUT", ruta, token, "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"x" + correo + "\"}"), 422, "PASSWORD_CONTAINS_EMAIL");
        error(send("PUT", ruta, token, "{\"actual\":\"incorrecta-123\",\"nueva\":\"una frase nueva y larga\"}"), 401, "AUTH_FAILED");
        error(send("PUT", ruta, null, "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"una frase nueva y larga\"}"), 401, "UNAUTHENTICATED");
        String hashAntes = jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"correo\"=?", String.class, correo);
        assertEquals(204, send("PUT", ruta, token, "{\"actual\":\"" + CLAVE + "\",\"nueva\":\"una frase nueva y larga\"}").statusCode());
        assertNotEquals(hashAntes, jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"correo\"=?", String.class, correo));
        login(correo, "una frase nueva y larga");
        error(send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}"), 401, "AUTH_FAILED");
    }

    // ---------- DM1-06 ----------
    @Test void cincoFallosDeUnaCuentaDevuelven429ConRetryAfter() throws Exception {
        String correo = "limite." + UUID.randomUUID() + "@prisaly.test";
        docenteConClave(correo);
        for (int i = 0; i < 5; i++) {
            error(send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + correo + "\",\"password\":\"mala-" + i + "\"}"), 401, "AUTH_FAILED");
        }
        var limitada = send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}");
        error(limitada, 429, "AUTH_RATE_LIMITED");
        long retry = Long.parseLong(limitada.headers().firstValue("Retry-After").orElseThrow());
        assertTrue(retry > 0 && retry <= 900, "Retry-After=" + retry);
        assertFalse(limitada.body().contains("accessToken"));
        // Another account from the same origin is not affected (5 failures < 20 per origin).
        String otro = "otro." + UUID.randomUUID() + "@prisaly.test";
        docenteConClave(otro);
        login(otro, CLAVE);
        // Unknown accounts are limited exactly the same way (no enumeration).
        String inexistente = "noexiste." + UUID.randomUUID() + "@prisaly.invalid";
        for (int i = 0; i < 5; i++) {
            error(send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + inexistente + "\",\"password\":\"x-" + i + "\"}"), 401, "AUTH_FAILED");
        }
        error(send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + inexistente + "\",\"password\":\"x\"}"), 429, "AUTH_RATE_LIMITED");
    }
}
