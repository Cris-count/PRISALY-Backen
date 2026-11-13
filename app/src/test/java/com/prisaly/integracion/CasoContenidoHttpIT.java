package com.prisaly.integracion;

import com.prisaly.m1.domain.model.Usuario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
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
 * Content API over the productive composition: real M1 MODIFICAR_CASO, real ownership, the
 * productive strong ETag codec and PostgreSQL 16.15. The read policy is set here because its
 * productive activation (F4) is not yet authorized.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CasoContenidoHttpIT {
    private static final String LECTURA = "CONSULTAR_CASO";

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
        // I1: F4 comes from application.yml as delivered (LECTURA; BORRADOR,PUBLICADO,ARCHIVADO; requiere-activo=false).
    }

    @LocalServerPort
    int port;
    @Autowired
    IdentityCatalogService catalog;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void elPropietarioConstruyeElContenidoCompletoEncadenandoEtags() throws Exception {
        String token = docente("completo.contenido@prisaly.test", true);
        UUID caso = crearCaso(token);
        String etag = etag(caso, token);
        UUID escenario = UUID.randomUUID(), pregunta = UUID.randomUUID();

        var e = post(token, caso + "/escenarios", etag,
                "{\"idEscenario\":\"" + escenario + "\",\"titulo\":\"E\",\"contexto\":\"Contexto\",\"orden\":1}");
        assertEquals(201, e.statusCode(), e.body());
        etag = e.headers().firstValue("ETag").orElseThrow();
        var q = post(token, caso + "/escenarios/" + escenario + "/preguntas", etag,
                "{\"idPregunta\":\"" + pregunta + "\",\"enunciado\":\"Pregunta\",\"orden\":1}");
        assertEquals(201, q.statusCode(), q.body());
        etag = q.headers().firstValue("ETag").orElseThrow();
        var o1 = opcion(token, caso, pregunta, etag, true, 1);
        assertEquals(201, o1.statusCode(), o1.body());
        etag = o1.headers().firstValue("ETag").orElseThrow();
        var o2 = opcion(token, caso, pregunta, etag, false, 2);
        assertEquals(201, o2.statusCode(), o2.body());
        etag = o2.headers().firstValue("ETag").orElseThrow();

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM \"OpcionRespuesta\" WHERE \"idPregunta\"=? AND \"explicacion\" IS NOT NULL", Integer.class, pregunta));

        var segundaCorrecta = opcion(token, caso, pregunta, etag, true, 3);
        assertEquals(409, segundaCorrecta.statusCode(), segundaCorrecta.body());
        assertTrue(segundaCorrecta.body().contains("CONTENT_CONFLICT"), segundaCorrecta.body());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM \"OpcionRespuesta\" WHERE \"idPregunta\"=?", Integer.class, pregunta));

        assertEquals(412, opcion(token, caso, pregunta, "\"m3-v2:" + "0".repeat(64) + "\"", false, 4).statusCode());
        assertEquals(428, opcion(token, caso, pregunta, null, false, 4).statusCode());
        assertEquals(404, opcion(token, caso, UUID.randomUUID(), etag, false, 4).statusCode());
    }

    @Test
    void sinPermisoRealOAjenoSon403YCasoPublicado409() throws Exception {
        String sinPermiso = docente("sinpermiso.contenido@prisaly.test", false);
        String ajeno = docente("ajeno.contenido@prisaly.test", true);
        String duenio = docente("duenio.contenido@prisaly.test", true);
        UUID caso = crearCaso(duenio);
        String etag = etag(caso, duenio);
        String cuerpo = "{\"idEscenario\":\"" + UUID.randomUUID() + "\",\"orden\":1}";
        assertEquals(403, post(sinPermiso, caso + "/escenarios", etag, cuerpo).statusCode());
        assertEquals(403, post(ajeno, caso + "/escenarios", etag, cuerpo).statusCode());

        jdbc.update("UPDATE \"Caso\" SET \"estado\"='Publicado' WHERE \"idCaso\"=?", caso);
        var publicado = post(duenio, caso + "/escenarios", etag(caso, duenio), cuerpo);
        assertEquals(409, publicado.statusCode(), publicado.body());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Escenario\" WHERE \"idCaso\"=?", Integer.class, caso));
    }

    @Test
    void elPermisoRealTambienHabilitaElPatchDelPropietario() throws Exception {
        String token = docente("patch.real@prisaly.test", true);
        UUID caso = crearCaso(token);
        var respuesta = http.send(HttpRequest.newBuilder(uri("/v1/casos/" + caso))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .header("If-Match", etag(caso, token))
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Editado real\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, respuesta.statusCode(), respuesta.body());
        assertEquals("Editado real", jdbc.queryForObject("SELECT \"nombre\" FROM \"Caso\" WHERE \"idCaso\"=?", String.class, caso));
    }

    private HttpResponse<String> opcion(String token, UUID caso, UUID pregunta, String etag, boolean correcta, int orden) throws Exception {
        return post(token, caso + "/preguntas/" + pregunta + "/opciones", etag, "{\"idOpcion\":\"" + UUID.randomUUID()
                + "\",\"texto\":\"T\",\"esCorrecta\":" + correcta + ",\"explicacion\":\"Porque\",\"orden\":" + orden + "}");
    }

    private HttpResponse<String> post(String token, String path, String ifMatch, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri("/v1/casos/" + path)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (ifMatch != null) { request.header("If-Match", ifMatch); }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String etag(UUID caso, String token) throws Exception {
        var r = http.send(HttpRequest.newBuilder(uri("/v1/casos/" + caso)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return r.headers().firstValue("ETag").orElseThrow();
    }

    private UUID crearCaso(String token) throws Exception {
        UUID caso = UUID.randomUUID();
        var r = http.send(HttpRequest.newBuilder(uri("/v1/casos")).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idCaso\":\"" + caso + "\",\"titulo\":\"Caso\",\"tiempoEstimadoSegundos\":300}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, r.statusCode(), r.body());
        return caso;
    }

    private String docente(String correo, boolean modificar) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        Usuario user = catalog.registerUser("Docente", correo, "Fixture#Clave-2026", rol);
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        UUID asignador = catalog.registerUser("Asignador", "asignador." + correo, "Fixture#Clave-2026",
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        // DM1-02: CREAR_CASO is now assigned by the administrator as well (setup only).
        catalog.assignPermission(user.getIdUsuario(), permiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), asignador);
        catalog.assignPermission(user.getIdUsuario(), permiso(LECTURA), asignador);
        if (modificar) { catalog.assignPermission(user.getIdUsuario(), permiso("MODIFICAR_CASO"), asignador); }
        var login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"Fixture#Clave-2026\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return objectMapper.readTree(login.body()).get("accessToken").asText();
    }

    private Integer permiso(String codigo) {
        var existentes = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return existentes.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : existentes.get(0);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api" + path);
    }
}
