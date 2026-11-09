package com.prisaly.integracion;

import com.prisaly.m1.domain.model.Usuario;
import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoPublicationAdapter;
import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** F5/PD-06 over the productive composition and PostgreSQL 16.15, with requiere-activo=true. */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CasoRubricaHttpIT {
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
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager manager;
    final HttpClient http = HttpClient.newHttpClient();
    UUID admin;

    record Cuenta(UUID id, String token) { }
    record Fix(UUID caso, UUID pregunta, String etag) { }

    @BeforeEach void administrador() {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        admin = catalog.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", "Secret#1", IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    String cuerpo(UUID pregunta) {
        return "{\"nombre\":\"Rubrica\",\"descripcion\":\"D\",\"puntajeTotal\":5.00,\"criterios\":[{\"id\":\"c1\",\"descripcion\":\"Claridad\",\"puntaje\":3,\"ponderacion\":0.60},{\"id\":\"c2\",\"descripcion\":\"Juicio\",\"puntaje\":2}],"
                + "\"preguntas\":[{\"idPregunta\":\"" + pregunta + "\",\"valorPregunta\":2.50,\"criterio\":\"Base\"}]}";
    }

    @Test void creaConsultaYPersisteSinPerdida() throws Exception {
        Cuenta c = docente(true, true);
        Fix f = fixture(c);
        assertEquals(404, get(f.caso(), c).statusCode());
        var guardada = put(c, f.caso(), f.etag(), cuerpo(f.pregunta()));
        assertEquals(200, guardada.statusCode(), guardada.body());
        JsonNode r = json.readTree(get(f.caso(), c).body());
        assertTrue(r.get("completa").asBoolean());
        assertEquals("c1", r.get("criterios").get(0).get("id").asText());
        assertEquals(3, r.get("criterios").get(0).get("puntaje").asInt());
        assertEquals(0, r.get("criterios").get(0).get("ponderacion").decimalValue().compareTo(new java.math.BigDecimal("0.60")));
        assertEquals("c2", r.get("criterios").get(1).get("id").asText());
        assertEquals(0, r.get("preguntas").get(0).get("valorPregunta").decimalValue().compareTo(new java.math.BigDecimal("2.50")));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Rubrica\" WHERE \"idCaso\"=?", Integer.class, f.caso()));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT (\"definicionEvaluacion\"->>'version')::int FROM \"Rubrica\" WHERE \"idCaso\"=?", Integer.class, f.caso()));
    }

    @Test void editarReemplazaSinDuplicarYCambiaLaEtag() throws Exception {
        Cuenta c = docente(true, true);
        Fix f = fixture(c);
        String etag = put(c, f.caso(), f.etag(), cuerpo(f.pregunta())).headers().firstValue("ETag").orElseThrow();
        assertNotEquals(f.etag(), etag);
        UUID rubrica = jdbc.queryForObject("SELECT \"idRubrica\" FROM \"Rubrica\" WHERE \"idCaso\"=?", UUID.class, f.caso());
        var editada = put(c, f.caso(), etag, "{\"nombre\":\"Nueva\",\"criterios\":[{\"id\":\"c9\",\"descripcion\":\"X\",\"puntaje\":1}]}");
        assertEquals(200, editada.statusCode(), editada.body());
        assertEquals(rubrica, jdbc.queryForObject("SELECT \"idRubrica\" FROM \"Rubrica\" WHERE \"idCaso\"=?", UUID.class, f.caso()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"RubricaPregunta\" WHERE \"idRubrica\"=?", Integer.class, rubrica));
        JsonNode r = json.readTree(editada.body());
        assertEquals(1, r.get("criterios").size());
        assertEquals(412, put(c, f.caso(), etag, cuerpo(f.pregunta())).statusCode());
    }

    @Test void cambiarSoloUnCriterioCambiaLaEtag() throws Exception {
        Cuenta c = docente(true, true);
        Fix f = fixture(c);
        var a = put(c, f.caso(), f.etag(), "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":1}]}");
        var b = put(c, f.caso(), a.headers().firstValue("ETag").orElseThrow(), "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":2}]}");
        assertEquals(200, b.statusCode(), b.body());
        assertNotEquals(a.headers().firstValue("ETag").orElseThrow(), b.headers().firstValue("ETag").orElseThrow());
    }

    @Test void preguntaDeOtroCasoSeRechazaSinCambios() throws Exception {
        Cuenta c = docente(true, true);
        Fix f = fixture(c), otro = fixture(c);
        assertEquals(404, put(c, f.caso(), f.etag(), cuerpo(otro.pregunta())).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Rubrica\" WHERE \"idCaso\"=?", Integer.class, f.caso()));
    }

    @Test void permisosPropiedadYEstudiante() throws Exception {
        Cuenta duenio = docente(true, true), ajeno = docente(true, true), soloLectura = docente(true, false), sinNada = docente(false, false);
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), sinNada.id());
        Fix f = fixture(duenio);
        String cuerpo = cuerpo(f.pregunta());
        String etag = put(duenio, f.caso(), f.etag(), cuerpo).headers().firstValue("ETag").orElseThrow();
        assertEquals(403, get(f.caso(), ajeno).statusCode());
        assertEquals(403, put(ajeno, f.caso(), etag, cuerpo).statusCode());
        assertEquals(403, get(f.caso(), sinNada).statusCode());
        assertEquals(403, put(sinNada, f.caso(), etag, cuerpo).statusCode());
        assertEquals(403, put(soloLectura, f.caso(), etag, cuerpo).statusCode());
        catalog.disablePermissionAssignment(duenio.id(), permiso(CONSULTAR), admin);
        assertEquals(403, put(duenio, f.caso(), etag, cuerpo).statusCode());
    }

    @Test void soloEnBorradorYExigeIfMatch() throws Exception {
        Cuenta c = docente(true, true);
        Fix f = fixture(c);
        assertEquals(428, put(c, f.caso(), null, cuerpo(f.pregunta())).statusCode());
        jdbc.update("UPDATE \"Caso\" SET \"estado\"='Publicado' WHERE \"idCaso\"=?", f.caso());
        String etag = http.send(req("/v1/casos/" + f.caso(), c).GET().build(), HttpResponse.BodyHandlers.ofString()).headers().firstValue("ETag").orElseThrow();
        var r = put(c, f.caso(), etag, cuerpo(f.pregunta()));
        assertEquals(409, r.statusCode(), r.body());
    }

    @Test void laPublicacionExigeRubricaCompletaYAceptaLaPersistida() throws Exception {
        Cuenta c = docente(true, true);
        Fix f = fixture(c);
        var publicar = new CasoPublicationAdapter(emf, manager, new CaseAuthorizationPort() {
            @Override public boolean canCreateCase(String u, String d) { return false; }
            @Override public boolean canModifyCase(String u, String i) { return false; }
            @Override public boolean canPublishCase(String u, String i) { return true; }
        });
        assertEquals("INCOMPLETE_RUBRIC", assertThrows(CasoDomainException.class,
                () -> publicar.publish(c.id().toString(), f.caso(), sinComillas(f.etag()))).code());
        var incompleta = put(c, f.caso(), f.etag(), "{\"nombre\":\"Sin criterios\"}");
        assertEquals(200, incompleta.statusCode(), incompleta.body());
        assertFalse(json.readTree(incompleta.body()).get("completa").asBoolean());
        String etag = incompleta.headers().firstValue("ETag").orElseThrow();
        assertEquals("INCOMPLETE_RUBRIC", assertThrows(CasoDomainException.class,
                () -> publicar.publish(c.id().toString(), f.caso(), sinComillas(etag))).code());
        var completa = put(c, f.caso(), etag, cuerpo(f.pregunta()));
        publicar.publish(c.id().toString(), f.caso(), sinComillas(completa.headers().firstValue("ETag").orElseThrow()));
        assertEquals("Publicado", jdbc.queryForObject("SELECT \"estado\" FROM \"Caso\" WHERE \"idCaso\"=?", String.class, f.caso()));
    }

    @Test void patchDelCasoConservaLaRubrica() throws Exception {
        Cuenta c = docente(true, true);
        Fix f = fixture(c);
        String etag = put(c, f.caso(), f.etag(), cuerpo(f.pregunta())).headers().firstValue("ETag").orElseThrow();
        var r = http.send(req("/v1/casos/" + f.caso(), c).header("Content-Type", "application/json").header("If-Match", etag)
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Otro\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, r.statusCode(), r.body());
        assertEquals(2, json.readTree(get(f.caso(), c).body()).get("criterios").size());
    }

    private static String sinComillas(String etag) { return etag.substring(1, etag.length() - 1); }

    private Fix fixture(Cuenta c) throws Exception {
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), pregunta = UUID.randomUUID();
        var creado = http.send(req("/v1/casos", c).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idCaso\":\"" + caso + "\",\"titulo\":\"Caso\",\"tiempoEstimadoSegundos\":300}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, creado.statusCode(), creado.body());
        String etag = http.send(req("/v1/casos/" + caso, c).GET().build(), HttpResponse.BodyHandlers.ofString()).headers().firstValue("ETag").orElseThrow();
        etag = post(c, caso + "/escenarios", etag, "{\"idEscenario\":\"" + escenario + "\",\"orden\":1}");
        etag = post(c, caso + "/escenarios/" + escenario + "/preguntas", etag, "{\"idPregunta\":\"" + pregunta + "\",\"enunciado\":\"P\",\"orden\":1}");
        etag = post(c, caso + "/preguntas/" + pregunta + "/opciones", etag, "{\"idOpcion\":\"" + UUID.randomUUID() + "\",\"texto\":\"C\",\"esCorrecta\":true,\"explicacion\":\"E\",\"orden\":1}");
        return new Fix(caso, pregunta, etag);
    }
    private String post(Cuenta c, String path, String etag, String body) throws Exception {
        var r = http.send(req("/v1/casos/" + path, c).header("Content-Type", "application/json").header("If-Match", etag)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, r.statusCode(), r.body());
        return r.headers().firstValue("ETag").orElseThrow();
    }
    private HttpResponse<String> put(Cuenta c, UUID caso, String etag, String body) throws Exception {
        var b = req("/v1/casos/" + caso + "/rubrica", c).header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
        if (etag != null) { b.header("If-Match", etag); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(UUID caso, Cuenta c) throws Exception {
        return http.send(req("/v1/casos/" + caso + "/rubrica", c).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest.Builder req(String path, Cuenta c) { return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + c.token()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private Cuenta docente(boolean consultar, boolean modificar) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        Usuario u = catalog.registerUser("Docente", UUID.randomUUID() + "@prisaly.test", "Secret#1", rol);
        catalog.assignPermission(u.getIdUsuario(), permiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), null);
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
