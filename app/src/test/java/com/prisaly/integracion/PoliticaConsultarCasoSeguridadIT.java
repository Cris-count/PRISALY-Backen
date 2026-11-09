package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
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
import tools.jackson.databind.ObjectMapper;

/**
 * PD-01 conditions over the productive composition and PostgreSQL 16.15. The read policy is set
 * here with requiere-activo=true because its productive activation (F4) is not yet authorized.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PoliticaConsultarCasoSeguridadIT {
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
    UUID actor;

    record Cuenta(UUID id, String token) { }

    @BeforeEach void responsable() {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        actor = catalog.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", "Secret#1",
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    @Test void conPermisoYPropietarioResponde200CuatroCamposYEtagFuerte() throws Exception {
        Cuenta c = docente(true, false);
        var r = get(crearCaso(c), c);
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.headers().firstValue("ETag").orElseThrow().matches("\"m3-v2:[0-9a-f]{64}\""));
        assertEquals(4, json.readTree(r.body()).size());
    }

    @Test void sinPermisoResponde403SinEtag() throws Exception {
        Cuenta c = docente(false, false);
        var r = get(crearCaso(c), c);
        assertEquals(403, r.statusCode());
        assertTrue(r.headers().firstValue("ETag").isEmpty());
    }

    @Test void ajenoConAmbosPermisosResponde403() throws Exception {
        Cuenta duenio = docente(true, true), ajeno = docente(true, true);
        UUID caso = crearCaso(duenio);
        String etag = etag(caso, duenio);
        assertEquals(403, get(caso, ajeno).statusCode());
        assertEquals(403, patch(caso, ajeno, etag).statusCode());
    }

    @Test void borradorPublicadoYArchivadoSonConsultables() throws Exception {
        Cuenta c = docente(true, false);
        UUID caso = crearCaso(c);
        for (String estado : new String[] {"Borrador", "Publicado", "Archivado"}) {
            jdbc.update("UPDATE \"Caso\" SET \"estado\"=? WHERE \"idCaso\"=?", estado, caso);
            assertEquals(200, get(caso, c).statusCode(), estado);
        }
    }

    @Test void soloBorradorEsEditableYPatchExigeIfMatch() throws Exception {
        Cuenta c = docente(true, true);
        UUID caso = crearCaso(c);
        assertEquals(428, patch(caso, c, null).statusCode());
        assertEquals(204, patch(caso, c, etag(caso, c)).statusCode());
        for (String estado : new String[] {"Publicado", "Archivado"}) {
            jdbc.update("UPDATE \"Caso\" SET \"estado\"=? WHERE \"idCaso\"=?", estado, caso);
            assertEquals(409, patch(caso, c, etag(caso, c)).statusCode(), estado);
        }
    }

    @Test void condicion2_editarSinConsultarSeDeniegaAunConEtagVigente() throws Exception {
        Cuenta c = docente(true, true);
        UUID caso = crearCaso(c);
        String etag = etag(caso, c);
        catalog.disablePermissionAssignment(c.id(), permiso(CONSULTAR), actor);
        assertEquals(403, patch(caso, c, etag).statusCode());
        assertEquals("Caso", jdbc.queryForObject("SELECT \"nombre\" FROM \"Caso\" WHERE \"idCaso\"=?", String.class, caso));
    }

    @Test void condicion2_editarSinModificarSeDeniega() throws Exception {
        Cuenta c = docente(true, false);
        UUID caso = crearCaso(c);
        assertEquals(403, patch(caso, c, etag(caso, c)).statusCode());
    }

    @Test void condicion6_unaCuentaEstudianteNoRecibeElPermiso() {
        UUID estudiante = usuario().getIdUsuario();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), estudiante);
        var error = assertThrows(IdentityDomainException.class, () -> catalog.assignPermission(estudiante, permiso(CONSULTAR), actor));
        assertEquals(IdentityCatalogService.CODE_STUDENT_NOT_ALLOWED, error.code());
    }

    @Test void revocarElPermisoTieneEfectoEnLaSiguientePeticion() throws Exception {
        Cuenta c = docente(true, false);
        UUID caso = crearCaso(c);
        assertEquals(200, get(caso, c).statusCode());
        catalog.disablePermissionAssignment(c.id(), permiso(CONSULTAR), actor);
        assertEquals(403, get(caso, c).statusCode());
    }

    @Test void permisosEfectivosVisiblesEnSesion() throws Exception {
        Cuenta c = docente(true, false);
        var me = http.send(req("/v1/personal/me", c).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, me.statusCode());
        assertTrue(me.body().contains(CONSULTAR), me.body());
        assertFalse(me.body().contains(MODIFICAR), me.body());
    }

    @Test void cuentaInactivaNoConsultaAunqueTengaElPermiso() throws Exception {
        Cuenta c = docente(true, false);
        UUID caso = crearCaso(c);
        catalog.deactivateUser(c.id());
        var r = get(caso, c);
        assertTrue(r.statusCode() == 401 || r.statusCode() == 403, "status " + r.statusCode());
    }

    @Test void conRequiereActivoTrueUnCasoInactivoSeDeniega() throws Exception {
        Cuenta c = docente(true, false);
        UUID caso = crearCaso(c);
        jdbc.update("UPDATE \"Caso\" SET \"activo\"=false WHERE \"idCaso\"=?", caso);
        assertEquals(403, get(caso, c).statusCode());
    }

    private String etag(UUID caso, Cuenta c) throws Exception {
        return get(caso, c).headers().firstValue("ETag").orElseThrow();
    }
    private UUID crearCaso(Cuenta c) throws Exception {
        UUID caso = UUID.randomUUID();
        var r = http.send(req("/v1/casos", c).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idCaso\":\"" + caso + "\",\"titulo\":\"Caso\",\"tiempoEstimadoSegundos\":300}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, r.statusCode(), r.body());
        return caso;
    }
    private HttpRequest.Builder req(String path, Cuenta c) {
        return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + c.token());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private HttpResponse<String> get(UUID caso, Cuenta c) throws Exception {
        return http.send(req("/v1/casos/" + caso, c).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> patch(UUID caso, Cuenta c, String ifMatch) throws Exception {
        var b = req("/v1/casos/" + caso, c).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Editado\"}"));
        if (ifMatch != null) { b.header("If-Match", ifMatch); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private UsuarioEntity usuario() {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Fixture");
        return catalog.registerUser("Fixture", UUID.randomUUID() + "@prisaly.test", "Secret#1", rol);
    }
    private Cuenta docente(boolean consultar, boolean modificar) throws Exception {
        UsuarioEntity u = usuario();
        catalog.assignPermission(u.getIdUsuario(), permiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), null);
        if (consultar) { catalog.assignPermission(u.getIdUsuario(), permiso(CONSULTAR), actor); }
        if (modificar) { catalog.assignPermission(u.getIdUsuario(), permiso(MODIFICAR), actor); }
        var login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + u.getCorreo() + "\",\"password\":\"Secret#1\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return new Cuenta(u.getIdUsuario(), json.readTree(login.body()).get("accessToken").asText());
    }
    private Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }
}
