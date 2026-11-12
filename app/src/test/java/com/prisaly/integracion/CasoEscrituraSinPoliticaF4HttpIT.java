package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
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
 * DC-CIERRE-MODULAR-01, decision F4-H1-alcance (NOT taken here). CHARACTERIZATION of the CURRENT semantics:
 * M3 writes are authorized by M1 permissions (PD-01/PD-02) plus ownership, and do NOT consult the F4 read policy.
 * A QA read policy that makes only BORRADOR visible (test-only values, not ratified) shows the observable difference:
 * once published, the owner can no longer read the case (403), yet still archives it with the ETag returned by the
 * publication (200). If F4-H1-alcance is ratified as "writes also require F4", the archive below must become 403 and
 * this test must be updated together with that change.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CasoEscrituraSinPoliticaF4HttpIT {
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
        r.add("prisaly.m3.lectura.permiso", () -> "CONSULTAR_CASO");
        r.add("prisaly.m3.lectura.estados-visibles", () -> "BORRADOR");
        r.add("prisaly.m3.lectura.requiere-activo", () -> "true");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void laEscrituraNoConsultaLaPoliticaDeLecturaF4() throws Exception {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        UUID admin = catalog.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", CLAVE, IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        String correo = UUID.randomUUID() + "@prisaly.test";
        Usuario u = catalog.registerUser("Docente", correo, CLAVE, rol);
        for (String p : new String[] {PersonalAuthorizationController.PERMISSION_CREAR_CASO, "CONSULTAR_CASO", "MODIFICAR_CASO",
                "PUBLICAR_CASO", "ARCHIVAR_CASO"}) {
            catalog.assignPermission(u.getIdUsuario(), permiso(p), admin);
        }
        String token = login(correo);

        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), pregunta = UUID.randomUUID();
        assertEquals(201, send(token, "POST", "/v1/casos", null,
                "{\"idCaso\":\"" + caso + "\",\"titulo\":\"Caso\",\"tiempoEstimadoSegundos\":300}").statusCode());
        String etag = etag(token, caso);
        etag = post(token, caso + "/escenarios", etag, "{\"idEscenario\":\"" + escenario + "\",\"orden\":1}");
        etag = post(token, caso + "/escenarios/" + escenario + "/preguntas", etag,
                "{\"idPregunta\":\"" + pregunta + "\",\"enunciado\":\"P\",\"orden\":1}");
        post(token, caso + "/preguntas/" + pregunta + "/opciones", etag,
                "{\"idOpcion\":\"" + UUID.randomUUID() + "\",\"texto\":\"C\",\"esCorrecta\":true,\"explicacion\":\"E\",\"orden\":1}");
        var rubrica = send(token, "PUT", "/v1/casos/" + caso + "/rubrica", etag(token, caso),
                "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":1}]}");
        assertEquals(200, rubrica.statusCode(), rubrica.body());

        var publicado = send(token, "POST", "/v1/casos/" + caso + "/publicar", etag(token, caso), null);
        assertEquals(200, publicado.statusCode(), publicado.body());
        String etagPublicado = publicado.headers().firstValue("ETag").orElseThrow();

        // F4 read policy (QA variant): PUBLICADO is not visible, so the owner cannot read it any more.
        assertEquals(403, send(token, "GET", "/v1/casos/" + caso, null, null).statusCode());
        // Current semantics: the write is decided by ARCHIVAR_CASO + ownership + If-Match, not by F4.
        var archivado = send(token, "POST", "/v1/casos/" + caso + "/archivar", etagPublicado, null);
        assertEquals(200, archivado.statusCode(), archivado.body());
        assertEquals("Archivado", jdbc.queryForObject("SELECT \"estado\" FROM \"Caso\" WHERE \"idCaso\"=?", String.class, caso));
    }

    private String etag(String token, UUID caso) throws Exception {
        var r = send(token, "GET", "/v1/casos/" + caso, null, null);
        assertEquals(200, r.statusCode(), r.body());
        return r.headers().firstValue("ETag").orElseThrow();
    }

    private String post(String token, String path, String etag, String body) throws Exception {
        var r = send(token, "POST", "/v1/casos/" + path, etag, body);
        assertEquals(201, r.statusCode(), r.body());
        return r.headers().firstValue("ETag").orElseThrow();
    }

    private HttpResponse<String> send(String token, String metodo, String path, String etag, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).header("Authorization", "Bearer " + token)
                .method(metodo, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) { b.header("Content-Type", "application/json"); }
        if (etag != null) { b.header("If-Match", etag); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String correo) throws Exception {
        var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/personal/autenticar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }

    private Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }
}
