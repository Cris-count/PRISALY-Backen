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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P-78 (DC-BE-D78-1-AUTORIZACION-CONTROLADA-79) over the real server and PostgreSQL 16.15 with the OFFICIAL schema
 * only: administrator-only registration, no credential, student protections of M1, atomicity, and eligibility: a
 * teacher convokes the registered student through the real M4 route and M7 records the notice.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AltaEstudiantesHttpIT {
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
        r.add("prisaly.m1.alta-estudiantes.enabled", () -> "true");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();
    String tokenAdmin;

    @BeforeEach void preparar() throws Exception {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        if (jdbc.queryForObject("SELECT count(*) FROM \"Rol\" WHERE \"nombre\"=?", Integer.class, ROL_DOCENTE) == 0) {
            catalog.createRole(ROL_DOCENTE, "Rol docente de prueba");
        }
        // The shipped PD-01 extension, exactly as an operator applies it (idempotent).
        jdbc.execute(Files.readString(Path.of("../docs/provision/pd01-p78-rol-estudiante.sql")));
        jdbc.update("INSERT INTO \"Permiso\" (\"codigo\",\"nombre\") VALUES ('GESTIONAR_SESION','Gestionar sesion'),"
                + "('CONSULTAR_CASO','Consultar caso') ON CONFLICT (\"codigo\") DO NOTHING");
        String correo = "admin." + UUID.randomUUID() + "@prisaly.test";
        catalog.registerUser("Admin IT", correo, CLAVE, IdentityCatalogService.ROL_ADMINISTRADOR);
        tokenAdmin = login(correo, CLAVE);
    }

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
    private static void error(HttpResponse<String> r, int status, String code) {
        assertEquals(status, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"code\":\"" + code + "\""), r.body());
        assertFalse(r.body().matches("(?is).*(exception|jdbc:|passwordhash|stacktrace).*"), r.body());
    }
    private static String correo() { return "est." + UUID.randomUUID() + "@prisaly.test"; }
    private HttpResponse<String> alta(String token, String nombre, String correo) throws Exception {
        return send("POST", "/v1/admin/estudiantes", token, "{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\"}");
    }
    private int usuariosCon(String correo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Usuario\" WHERE lower(\"correo\")=lower(?)", Integer.class, correo);
    }

    @Test
    void elAdministradorDaDeAltaUnEstudianteSinCredencialYConLasProteccionesDeM1() throws Exception {
        String c = correo();
        var r = alta(tokenAdmin, "Estudiante QA", c);
        assertEquals(201, r.statusCode(), r.body());
        assertTrue(r.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode body = json.readTree(r.body());
        assertEquals(5, body.size(), "idUsuario, idEstudiante, nombre, correo, estado");
        assertEquals("Activo", body.get("estado").asText());
        assertFalse(r.body().toLowerCase().contains("password"));
        UUID usuario = UUID.fromString(body.get("idUsuario").asText());
        UUID estudiante = UUID.fromString(body.get("idEstudiante").asText());

        assertNull(jdbc.queryForObject("SELECT \"passwordHash\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, usuario));
        assertEquals("ESTUDIANTE", jdbc.queryForObject("SELECT r.\"nombre\" FROM \"Usuario\" u JOIN \"Rol\" r ON"
                + " r.\"idRol\"=u.\"idRol\" WHERE u.\"idUsuario\"=?", String.class, usuario));
        assertEquals(usuario, jdbc.queryForObject("SELECT \"idUsuario\" FROM \"Estudiante\" WHERE \"idEstudiante\"=?",
                UUID.class, estudiante));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=?", Integer.class, usuario));

        var busqueda = send("GET", "/v1/admin/estudiantes?correo=" + c.toUpperCase(), tokenAdmin, null);
        assertEquals(200, busqueda.statusCode(), busqueda.body());
        assertEquals(estudiante.toString(), json.readTree(busqueda.body()).get("items").get(0).get("idEstudiante").asText());

        // No personnel access and no permissions for a student (DM1-02b), whatever password is tried.
        error(send("POST", "/v1/personal/autenticar", null, "{\"correo\":\"" + c + "\",\"password\":\"" + CLAVE + "\"}"), 401, "AUTH_FAILED");
        int permiso = jdbc.queryForObject("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\"='GESTIONAR_SESION'", Integer.class);
        error(send("PUT", "/v1/admin/usuarios/" + usuario + "/permisos/" + permiso, tokenAdmin, null), 422,
                "PERMISSION_NOT_ALLOWED_FOR_STUDENT");
        // Duplicates (case-insensitive), docente-staff collisions and invalid input.
        error(alta(tokenAdmin, "Otro", c.toUpperCase()), 409, "DUPLICATE_EMAIL");
        error(alta(tokenAdmin, "", "no-es-correo"), 400, "VALIDATION_ERROR");
        error(send("POST", "/v1/admin/estudiantes", tokenAdmin, "{no json"), 400, "VALIDATION_ERROR");
        error(send("GET", "/v1/admin/estudiantes", tokenAdmin, null), 400, "VALIDATION_ERROR");
        assertEquals(1, usuariosCon(c));
    }

    @Test
    void soloUnAdministradorActivoPuedeDarDeAltaOConsultar() throws Exception {
        String correoDocente = "doc." + UUID.randomUUID() + "@prisaly.test";
        catalog.registerUser("Docente IT", correoDocente, CLAVE, ROL_DOCENTE);
        String tokenDocente = login(correoDocente, CLAVE);
        String c = correo();
        error(alta(null, "Sin token", c), 401, "UNAUTHENTICATED");
        error(alta("token-falsificado", "Falso", c), 401, "UNAUTHENTICATED");
        error(alta(tokenDocente, "Docente", c), 403, "ADMIN_REQUIRED");
        error(send("GET", "/v1/admin/estudiantes?correo=" + c, tokenDocente, null), 403, "ADMIN_REQUIRED");
        error(send("GET", "/v1/admin/estudiantes?correo=" + c, null, null), 401, "UNAUTHENTICATED");
        assertEquals(0, usuariosCon(c), "rejected callers create nothing");
    }

    @Test
    void siFallaLaFilaEstudianteNoQuedaUsuario() throws Exception {
        jdbc.execute("CREATE FUNCTION it_rechaza_estudiante() RETURNS trigger LANGUAGE plpgsql AS"
                + " $$ BEGIN RAISE EXCEPTION 'IT-RECHAZO'; END $$");
        jdbc.execute("CREATE TRIGGER it_rechaza_estudiante BEFORE INSERT ON \"Estudiante\" FOR EACH ROW"
                + " EXECUTE FUNCTION it_rechaza_estudiante()");
        String c = correo();
        try {
            var r = alta(tokenAdmin, "Atomicidad", c);
            assertEquals(500, r.statusCode(), r.body());
            assertFalse(r.body().contains("IT-RECHAZO"), "no internal detail leaks");
        } finally {
            jdbc.execute("DROP TRIGGER it_rechaza_estudiante ON \"Estudiante\"");
            jdbc.execute("DROP FUNCTION it_rechaza_estudiante()");
        }
        assertEquals(0, usuariosCon(c), "user and student row are created or rolled back together");
        assertEquals(201, alta(tokenAdmin, "Atomicidad", c).statusCode(), "the same email is free afterwards");
    }

    @Test
    void elEstudianteRegistradoEsConvocableYM7RegistraElAviso() throws Exception {
        var r = alta(tokenAdmin, "Convocable QA", correo());
        assertEquals(201, r.statusCode(), r.body());
        UUID estudiante = UUID.fromString(json.readTree(r.body()).get("idEstudiante").asText());

        // Teacher with GESTIONAR_SESION granted through the audited administrative route.
        String correoDocente = "doc." + UUID.randomUUID() + "@prisaly.test";
        UUID docente = catalog.registerUser("Docente IT", correoDocente, CLAVE, ROL_DOCENTE).getIdUsuario();
        for (String codigo : new String[] {"GESTIONAR_SESION", "CONSULTAR_CASO"}) {
            int id = jdbc.queryForObject("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\"=?", Integer.class, codigo);
            assertEquals(200, send("PUT", "/v1/admin/usuarios/" + docente + "/permisos/" + id, tokenAdmin, null).statusCode());
        }
        String tokenDocente = login(correoDocente, CLAVE);
        // Published case and not-started session of that teacher (disposable fixture, as in the M4-M5 journey ITs).
        UUID caso = UUID.randomUUID(), sesion = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"historia\",\"tiempoEstimadoSegundos\",\"estado\")"
                + " VALUES (?,?,'Caso P-78','Historia',600,'Publicado')", caso, docente);
        jdbc.update("INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\","
                + "\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\") VALUES (?,?,?,'Sesion P-78',"
                + "now()+interval '1 day',now()+interval '1 day 2 hours',1800)", sesion, caso, docente);

        var convocada = send("POST", "/m4/sesiones/" + sesion + "/convocatorias", tokenDocente,
                "{\"estudianteId\":\"" + estudiante + "\"}");
        assertEquals(201, convocada.statusCode(), convocada.body());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Convocatoria\" WHERE \"idSesion\"=? AND \"idEstudiante\"=?",
                Integer.class, sesion, estudiante));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Aviso\" a JOIN \"Estudiante\" e ON"
                + " e.\"idUsuario\"=a.\"idUsuarioDestino\" WHERE e.\"idEstudiante\"=?", Integer.class, estudiante),
                "M7 records the convocation notice for the student's user");
        var duplicada = send("POST", "/m4/sesiones/" + sesion + "/convocatorias", tokenDocente,
                "{\"estudianteId\":\"" + estudiante + "\"}");
        assertEquals(409, duplicada.statusCode(), duplicada.body());
        var listado = send("GET", "/m4/sesiones/" + sesion + "/convocatorias", tokenDocente, null);
        assertEquals(200, listado.statusCode(), listado.body());
        assertTrue(listado.body().contains(estudiante.toString()), listado.body());
    }
}
