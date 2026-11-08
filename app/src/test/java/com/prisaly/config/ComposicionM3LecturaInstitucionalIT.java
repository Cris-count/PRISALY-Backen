package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-M3-RES-02: real HTTP (embedded server, /api context) over PostgreSQL 16.15 with the
 * institutional read adapter. The read policy below is a TEST FIXTURE configured through the same
 * properties Dirección Central would set; production keeps them blank (GET denied). Identity,
 * ownership, persistence, the write policy and the security chain are the productive ones.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ComposicionM3LecturaInstitucionalIT {

    private static final String PERMISO_FIXTURE = "PERMISO_LECTURA_FIXTURE";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        registry.add("prisaly.m3.lectura.permiso", () -> PERMISO_FIXTURE);
        registry.add("prisaly.m3.lectura.estados-visibles", () -> "BORRADOR");
        registry.add("prisaly.m3.lectura.requiere-activo", () -> "true");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private IdentityCatalogService catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void docenteConPermisoLeeSuCasoVisibleSinContenidoProtegido() throws Exception {
        String token = docente("lector.res02@prisaly.test", true);
        UUID caso = crearCaso(token, "Caso visible");

        HttpResponse<String> lectura = get("/v1/casos/" + caso, token);
        assertEquals(200, lectura.statusCode(), lectura.body());
        var json = objectMapper.readTree(lectura.body());
        assertEquals(caso.toString(), json.get("idCaso").asText());
        assertEquals(4, json.size());
        assertEquals("no-store", lectura.headers().firstValue("Cache-Control").orElseThrow());
        // No ratified ETag transport: GET carries no ETag in the productive composition.
        assertTrue(lectura.headers().firstValue("ETag").isEmpty());
    }

    @Test
    void sinPermisoDeLecturaCasoAjenoONoVisibleSon403YCasoInexistente404() throws Exception {
        String propietario = docente("propietario.res02@prisaly.test", true);
        String sinPermiso = docente("sinpermiso.res02@prisaly.test", false);
        String ajeno = docente("ajeno.res02@prisaly.test", true);
        UUID caso = crearCaso(propietario, "Caso protegido");

        assertEquals(403, get("/v1/casos/" + caso, sinPermiso).statusCode());
        assertEquals(403, get("/v1/casos/" + caso, ajeno).statusCode());
        assertEquals(404, get("/v1/casos/" + UUID.randomUUID(), propietario).statusCode());

        jdbc.update("UPDATE \"Caso\" SET \"activo\" = false WHERE \"idCaso\" = ?", caso);
        assertEquals(403, get("/v1/casos/" + caso, propietario).statusCode());
    }

    @Test
    void bearerInvalidoAusenteOCuentaInactivaSon401() throws Exception {
        UUID caso = UUID.randomUUID();
        assertEquals(401, get("/v1/casos/" + caso, null).statusCode());
        assertEquals(401, get("/v1/casos/" + caso, "token-inventado").statusCode());

        String token = docente("inactiva.res02@prisaly.test", true);
        UUID propio = crearCaso(token, "Caso de inactiva");
        UUID usuario = jdbc.queryForObject("SELECT \"idUsuario\" FROM \"Usuario\" WHERE \"correo\" = ?",
                UUID.class, "inactiva.res02@prisaly.test");
        catalog.deactivateUser(usuario);
        assertEquals(401, get("/v1/casos/" + propio, token).statusCode());
    }

    @Test
    void unTokenRevocadoPorCierreDeSesionDejaDeLeer() throws Exception {
        String token = docente("revocado.res03@prisaly.test", true);
        UUID caso = crearCaso(token, "Caso antes del cierre");
        assertEquals(200, get("/v1/casos/" + caso, token).statusCode());

        HttpResponse<String> cierre = http.send(HttpRequest.newBuilder(uri("/v1/personal/cerrar-sesion"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, cierre.statusCode());
        assertEquals(401, get("/v1/casos/" + caso, token).statusCode());
    }

    @Test
    void patchSigueDenegadoYNoEscribe() throws Exception {
        String token = docente("patch.res02@prisaly.test", true);
        UUID caso = crearCaso(token, "Sin edicion");
        HttpResponse<String> patch = http.send(HttpRequest.newBuilder(uri("/v1/casos/" + caso))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("If-Match", "\"m3-v2:" + "0".repeat(64) + "\"")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Editado\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, patch.statusCode());
        assertEquals("Sin edicion", jdbc.queryForObject(
                "SELECT \"nombre\" FROM \"Caso\" WHERE \"idCaso\" = ?", String.class, caso));
    }

    // A real infrastructure failure (permission table unavailable) must surface as a sanitized 500,
    // not as 401/403 and not as a successful read.
    @Test
    void unFalloTecnicoRealEsUn500Sanitizado() throws Exception {
        String token = docente("fallo.res02@prisaly.test", true);
        UUID caso = crearCaso(token, "Caso durante fallo");
        jdbc.execute("ALTER TABLE \"UsuarioPermiso\" RENAME TO \"UsuarioPermisoFueraDeServicio\"");
        HttpResponse<String> respuesta;
        try {
            respuesta = get("/v1/casos/" + caso, token);
        } finally {
            jdbc.execute("ALTER TABLE \"UsuarioPermisoFueraDeServicio\" RENAME TO \"UsuarioPermiso\"");
        }
        assertEquals(500, respuesta.statusCode(), respuesta.body());
        assertFalse(respuesta.body().contains("UsuarioPermiso"), respuesta.body());
        assertFalse(respuesta.body().contains("Caso durante fallo"), respuesta.body());
        assertFalse(respuesta.body().toLowerCase().contains("exception"), respuesta.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID crearCaso(String token, String titulo) throws Exception {
        UUID caso = UUID.randomUUID();
        HttpResponse<String> creado = http.send(HttpRequest.newBuilder(uri("/v1/casos"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"idCaso\":\"" + caso + "\",\"titulo\":\"" + titulo + "\",\"tiempoEstimadoSegundos\":300}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, creado.statusCode(), creado.body());
        return caso;
    }

    private String docente(String correo, boolean conPermisoDeLectura) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        UsuarioEntity user = catalog.registerUser("Docente", correo, "Secret#1", rol);
        catalog.assignPermission(user.getIdUsuario(), idPermiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), null);
        if (conPermisoDeLectura) {
            catalog.assignPermission(user.getIdUsuario(), idPermiso(PERMISO_FIXTURE), null);
        }
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"correo\":\"" + correo + "\",\"password\":\"Secret#1\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        String token = objectMapper.readTree(login.body()).get("accessToken").asText();
        assertNull(objectMapper.readTree(login.body()).get("password"));
        return token;
    }

    private Integer idPermiso(String codigo) {
        var existentes = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        return catalog.createPermission(codigo, codigo, null).getIdPermiso();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api" + path);
    }
}
