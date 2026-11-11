package com.prisaly.integracion;

import com.prisaly.m1.domain.model.Usuario;
import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import java.net.URI;
import java.net.http.*;
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
 * F2/PD-04 over the productive composition and PostgreSQL 16.15. The read policy is set here,
 * with requiere-activo=true, because its productive activation (F4) is not yet authorized.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CasoContenidoConsultaHttpIT {
    static final String CONSULTAR = "CONSULTAR_CASO", MODIFICAR = "MODIFICAR_CASO";
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
        r.add("prisaly.m3.lectura.permiso", () -> CONSULTAR);
        r.add("prisaly.m3.lectura.estados-visibles", () -> "BORRADOR,PUBLICADO,ARCHIVADO");
        r.add("prisaly.m3.lectura.requiere-activo", () -> "true");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    UUID admin;

    record Cuenta(UUID id, String token) { }
    record Contenido(UUID caso, UUID escenario, UUID pregunta, String etag) { }

    @BeforeEach void administrador() {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        admin = catalog.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", "Secret#1",
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    @Test void consultaCompletaDevuelveTodoElContenidoEnOrdenConLaMismaEtag() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        var r = get(x.caso() + "/contenido", c);
        assertEquals(200, r.statusCode(), r.body());
        JsonNode raiz = json.readTree(r.body());
        JsonNode pregunta = raiz.get("escenarios").get(0).get("preguntas").get(0);
        assertEquals(x.escenario().toString(), raiz.get("escenarios").get(0).get("idEscenario").asText());
        assertEquals("Por correcta", pregunta.get("opciones").get(0).get("explicacion").asText());
        assertTrue(pregunta.get("opciones").get(0).get("esCorrecta").asBoolean());
        assertEquals("Por incorrecta", pregunta.get("opciones").get(1).get("explicacion").asText());
        assertFalse(raiz.has("docenteId"));
        assertFalse(raiz.has("rubrica"));
        assertEquals(x.etag(), r.headers().firstValue("ETag").orElseThrow());
        assertEquals(x.etag(), get(x.caso().toString(), c).headers().firstValue("ETag").orElseThrow());
        assertEquals("no-store", r.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test void rutasDeEscenarioPreguntaYOpciones() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        assertEquals(1, json.readTree(get(x.caso() + "/escenarios", c).body()).size());
        assertEquals("E1", json.readTree(get(x.caso() + "/escenarios/" + x.escenario(), c).body()).get("titulo").asText());
        assertEquals("Pregunta", json.readTree(get(x.caso() + "/preguntas/" + x.pregunta(), c).body()).get("enunciado").asText());
        assertEquals(2, json.readTree(get(x.caso() + "/preguntas/" + x.pregunta() + "/opciones", c).body()).size());
    }

    @Test void elementoDeOtroCasoNoSeExponePorRutaAnidada() throws Exception {
        Cuenta c = docente(true, true);
        Contenido mio = contenido(c), otro = contenido(c);
        for (String ruta : new String[] {"/escenarios/" + otro.escenario(), "/preguntas/" + otro.pregunta(), "/preguntas/" + otro.pregunta() + "/opciones"}) {
            var r = get(mio.caso() + ruta, c);
            assertEquals(404, r.statusCode(), ruta);
            assertFalse(r.body().contains("Por correcta"), ruta);
        }
    }

    @Test void ajenoConPermisosNoConsultaNingunaRuta() throws Exception {
        Cuenta duenio = docente(true, true), ajeno = docente(true, true);
        Contenido x = contenido(duenio);
        for (String ruta : rutas(x)) {
            var r = get(ruta, ajeno);
            assertEquals(403, r.statusCode(), ruta);
            assertFalse(r.body().contains("Por correcta"), ruta);
            assertTrue(r.headers().firstValue("ETag").isEmpty(), ruta);
        }
    }

    @Test void soloModificarSinConsultarNoConsultaNingunaRuta() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        catalog.disablePermissionAssignment(c.id(), permiso(CONSULTAR), admin);
        for (String ruta : rutas(x)) { assertEquals(403, get(ruta, c).statusCode(), ruta); }
    }

    @Test void consultarSinModificarLeePeroNoEscribe() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        catalog.disablePermissionAssignment(c.id(), permiso(MODIFICAR), admin);
        var r = get(x.caso() + "/contenido", c);
        assertEquals(200, r.statusCode());
        var escritura = post(c, x.caso() + "/escenarios", r.headers().firstValue("ETag").orElseThrow(),
                "{\"idEscenario\":\"" + UUID.randomUUID() + "\",\"orden\":2}");
        assertEquals(403, escritura.statusCode());
    }

    @Test void losTresEstadosSonConsultablesYCasoInactivoSeDeniega() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        for (String estado : new String[] {"Borrador", "Publicado", "Archivado"}) {
            jdbc.update("UPDATE \"Caso\" SET \"estado\"=? WHERE \"idCaso\"=?", estado, x.caso());
            assertEquals(200, get(x.caso() + "/contenido", c).statusCode(), estado);
        }
        jdbc.update("UPDATE \"Caso\" SET \"activo\"=false WHERE \"idCaso\"=?", x.caso());
        assertEquals(403, get(x.caso() + "/contenido", c).statusCode());
    }

    @Test void cuentaEstudianteNoConsulta() throws Exception {
        Cuenta duenio = docente(true, true), estudiante = docente(false, false);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), estudiante.id());
        Contenido x = contenido(duenio);
        for (String ruta : rutas(x)) { assertEquals(403, get(ruta, estudiante).statusCode(), ruta); }
    }

    @Test void sinTokenResponde401() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        var r = http.send(HttpRequest.newBuilder(uri("/v1/casos/" + x.caso() + "/contenido")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, r.statusCode());
    }

    @Test void etagDeLaConsultaPermiteEditarYQuedaObsoletaTrasElCambio() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        String etag = get(x.caso() + "/contenido", c).headers().firstValue("ETag").orElseThrow();
        var ok = post(c, x.caso() + "/escenarios", etag, "{\"idEscenario\":\"" + UUID.randomUUID() + "\",\"titulo\":\"E2\",\"orden\":2}");
        assertEquals(201, ok.statusCode(), ok.body());
        assertNotEquals(etag, get(x.caso() + "/contenido", c).headers().firstValue("ETag").orElseThrow());
        assertEquals(412, post(c, x.caso() + "/escenarios", etag, "{\"idEscenario\":\"" + UUID.randomUUID() + "\",\"orden\":3}").statusCode());
        assertEquals(2, json.readTree(get(x.caso() + "/contenido", c).body()).get("escenarios").size());
    }

    @Test void corsPermiteAngularYExponeEtag() throws Exception {
        Cuenta c = docente(true, true);
        Contenido x = contenido(c);
        var previa = http.send(HttpRequest.newBuilder(uri("/v1/casos/" + x.caso() + "/contenido"))
                .header("Origin", "http://localhost:4200").header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, previa.statusCode());
        assertEquals("http://localhost:4200", previa.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        var real = http.send(req("/v1/casos/" + x.caso() + "/contenido", c).header("Origin", "http://localhost:4200").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(real.headers().firstValue("Access-Control-Expose-Headers").orElseThrow().contains("ETag"));
    }

    private String[] rutas(Contenido x) {
        String c = x.caso().toString();
        return new String[] {c + "/contenido", c + "/escenarios", c + "/escenarios/" + x.escenario(), c + "/preguntas/" + x.pregunta(), c + "/preguntas/" + x.pregunta() + "/opciones"};
    }

    private Contenido contenido(Cuenta c) throws Exception {
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), pregunta = UUID.randomUUID();
        var creado = http.send(req("/v1/casos", c).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idCaso\":\"" + caso + "\",\"titulo\":\"Caso\",\"tiempoEstimadoSegundos\":300}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, creado.statusCode(), creado.body());
        String etag = get(caso.toString(), c).headers().firstValue("ETag").orElseThrow();
        etag = escribir(c, caso + "/escenarios", etag, "{\"idEscenario\":\"" + escenario + "\",\"titulo\":\"E1\",\"contexto\":\"Contexto\",\"orden\":1}");
        etag = escribir(c, caso + "/escenarios/" + escenario + "/preguntas", etag, "{\"idPregunta\":\"" + pregunta + "\",\"enunciado\":\"Pregunta\",\"orden\":1}");
        etag = escribir(c, caso + "/preguntas/" + pregunta + "/opciones", etag, "{\"idOpcion\":\"" + UUID.randomUUID() + "\",\"texto\":\"I\",\"esCorrecta\":false,\"explicacion\":\"Por incorrecta\",\"orden\":2}");
        etag = escribir(c, caso + "/preguntas/" + pregunta + "/opciones", etag, "{\"idOpcion\":\"" + UUID.randomUUID() + "\",\"texto\":\"C\",\"esCorrecta\":true,\"explicacion\":\"Por correcta\",\"orden\":1}");
        return new Contenido(caso, escenario, pregunta, etag);
    }
    private String escribir(Cuenta c, String path, String etag, String body) throws Exception {
        var r = post(c, path, etag, body);
        assertEquals(201, r.statusCode(), r.body());
        return r.headers().firstValue("ETag").orElseThrow();
    }
    private HttpResponse<String> post(Cuenta c, String path, String etag, String body) throws Exception {
        return http.send(req("/v1/casos/" + path, c).header("Content-Type", "application/json").header("If-Match", etag)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, Cuenta c) throws Exception {
        return http.send(req("/v1/casos/" + path, c).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest.Builder req(String path, Cuenta c) {
        return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + c.token());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private Cuenta docente(boolean consultar, boolean modificar) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        Usuario u = catalog.registerUser("Docente", UUID.randomUUID() + "@prisaly.test", "Secret#1", rol);
        catalog.assignPermission(u.getIdUsuario(), permiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), admin); // DM1-02: setup only
        if (consultar) { catalog.assignPermission(u.getIdUsuario(), permiso(CONSULTAR), admin); }
        if (modificar) { catalog.assignPermission(u.getIdUsuario(), permiso(MODIFICAR), admin); }
        var login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + u.getCorreo() + "\",\"password\":\"Secret#1\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return new Cuenta(u.getIdUsuario(), json.readTree(login.body()).get("accessToken").asText());
    }
    private Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }
}
