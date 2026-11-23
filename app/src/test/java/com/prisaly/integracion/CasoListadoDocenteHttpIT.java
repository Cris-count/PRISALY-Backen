package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.model.Usuario;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
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
 * DC-BE-CIERRE-FUNCIONAL-NOCTURNO-67 — CANDIDATE {@code GET /v1/casos} over the real APP and PostgreSQL 16.15, with the
 * read policy as shipped in {@code application.yml} (CONSULTAR_CASO; Borrador, Publicado, Archivado; inactive too).
 * {@code prisaly.m3.listado.enabled} is turned on ONLY here. Fictitious data.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CasoListadoDocenteHttpIT {
    static final String CLAVE = "Fixture#Clave-2026";
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        r.add("prisaly.m3.listado.enabled", () -> "true"); // QA only
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    UUID admin;

    record Cuenta(UUID id, String token) { }

    @BeforeEach void administrador() {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        admin = catalog.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", CLAVE,
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    @Test
    void listaSoloLosCasosPropiosIncluidosArchivadosEInactivos() throws Exception {
        Cuenta a = docente(true), b = docente(true);
        UUID borrador = caso(a, "Borrador", true), publicado = caso(a, "Publicado", true),
                archivadoInactivo = caso(a, "Archivado", false), ajeno = caso(b, "Borrador", true);
        var r = listar(a);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("no-store", r.headers().firstValue("Cache-Control").orElse(""));
        var items = json.readTree(r.body()).get("items");
        List<String> ids = StreamSupport.stream(items.spliterator(), false).map(n -> n.get("idCaso").asString()).toList();
        assertEquals(3, ids.size(), r.body());
        assertTrue(ids.containsAll(List.of(borrador.toString(), publicado.toString(), archivadoInactivo.toString())));
        assertFalse(ids.contains(ajeno.toString()), "never another teacher's case");
        assertEquals(4, items.get(0).size(), "only idCaso, titulo, tiempoEstimadoSegundos, estado");
        assertFalse(r.body().contains("idDocente") || r.body().contains("historia"));
        var rb = listar(b);
        assertEquals(1, json.readTree(rb.body()).get("items").size());
    }

    @Test
    void sinPermisoDeLecturaEs403AunqueNoTengaCasosYSinTokenEs401() throws Exception {
        Cuenta sinLectura = docente(false);
        caso(sinLectura, "Borrador", true);
        var r = listar(sinLectura);
        assertEquals(403, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"code\":\"FORBIDDEN\""), r.body());
        assertFalse(r.body().contains("idCaso"));
        var anon = http.send(HttpRequest.newBuilder(uri("/v1/casos")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anon.statusCode(), anon.body());
        Cuenta vacio = docente(true);
        var v = listar(vacio);
        assertEquals(200, v.statusCode());
        assertEquals(0, json.readTree(v.body()).get("items").size());
    }

    @Test
    void laRevocacionDelPermisoSurteEfectoEnLaSiguientePeticion() throws Exception {
        Cuenta a = docente(true);
        caso(a, "Borrador", true);
        assertEquals(200, listar(a).statusCode());
        catalog.disablePermissionAssignment(a.id(), permiso("CONSULTAR_CASO"), admin);
        assertEquals(403, listar(a).statusCode());
    }

    private HttpResponse<String> listar(Cuenta c) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/v1/casos")).header("Authorization", "Bearer " + c.token()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private UUID caso(Cuenta c, String estado, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\",\"activo\")"
                + " VALUES (?,?,?,600,?,?)", id, c.id(), "Caso listado " + id.toString().substring(0, 8), estado, activo);
        return id;
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private Cuenta docente(boolean consultar) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        Usuario u = catalog.registerUser("Docente", UUID.randomUUID() + "@prisaly.test", CLAVE, rol);
        if (consultar) { catalog.assignPermission(u.getIdUsuario(), permiso("CONSULTAR_CASO"), admin); }
        var login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + u.getCorreo() + "\",\"password\":\"" + CLAVE + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return new Cuenta(u.getIdUsuario(), json.readTree(login.body()).get("accessToken").asString());
    }

    private Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }
}
