package com.prisaly.integracion;

import com.prisaly.m1.domain.model.Usuario;
import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import com.prisaly.m3.application.service.CasoTransicionService;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** F6/PD-02 over the productive composition and PostgreSQL 16.15. */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CasoPublicacionHttpIT {
    static final String CONSULTAR = "CONSULTAR_CASO", MODIFICAR = "MODIFICAR_CASO", PUBLICAR = "PUBLICAR_CASO", ARCHIVAR = "ARCHIVAR_CASO";
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

    @BeforeEach void administrador() {
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        admin = catalog.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", "Fixture#Clave-2026", IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    @Test void cicloCompletoBorradorPublicadoArchivado() throws Exception {
        Cuenta c = cuenta(PUBLICAR, ARCHIVAR);
        UUID caso = casoCompleto(c);
        var p = accion(c, caso, "publicar", etag(caso, c));
        assertEquals(200, p.statusCode(), p.body());
        assertEquals("Publicado", estado(caso));
        var a = accion(c, caso, "archivar", p.headers().firstValue("ETag").orElseThrow());
        assertEquals(200, a.statusCode(), a.body());
        assertEquals("Archivado", estado(caso));
    }

    @Test void transicionesNoPermitidas() throws Exception {
        Cuenta c = cuenta(PUBLICAR, ARCHIVAR);
        UUID caso = casoCompleto(c);
        var borrador = accion(c, caso, "archivar", etag(caso, c));
        assertEquals(409, borrador.statusCode(), borrador.body());
        assertTrue(borrador.body().contains("CASE_NOT_PUBLISHED"));
        assertEquals("Borrador", estado(caso));
        String etag = accion(c, caso, "publicar", etag(caso, c)).headers().firstValue("ETag").orElseThrow();
        var dos = accion(c, caso, "publicar", etag);
        assertEquals(409, dos.statusCode());
        assertTrue(dos.body().contains("ALREADY_PUBLISHED"));
        String arch = accion(c, caso, "archivar", etag).headers().firstValue("ETag").orElseThrow();
        assertEquals(409, accion(c, caso, "publicar", arch).statusCode());
        assertEquals(409, accion(c, caso, "archivar", arch).statusCode());
        assertEquals("Archivado", estado(caso));
    }

    @Test void permisosIndependientesSinPrivilegiosPorDefecto() throws Exception {
        Cuenta soloEdita = cuenta(), soloPublica = cuenta(PUBLICAR), soloArchiva = cuenta(ARCHIVAR);
        UUID a = casoCompleto(soloEdita), b = casoCompleto(soloPublica), d = casoCompleto(soloArchiva);
        assertEquals(403, accion(soloEdita, a, "publicar", etag(a, soloEdita)).statusCode());
        assertEquals(403, accion(soloEdita, a, "archivar", etag(a, soloEdita)).statusCode());
        assertEquals(403, accion(soloArchiva, d, "publicar", etag(d, soloArchiva)).statusCode());
        assertEquals("Borrador", estado(d));
        String etag = accion(soloPublica, b, "publicar", etag(b, soloPublica)).headers().firstValue("ETag").orElseThrow();
        assertEquals(403, accion(soloPublica, b, "archivar", etag).statusCode());
        assertEquals("Publicado", estado(b));
    }

    @Test void administradorSinPermisoNoPublicaYAjenoTampoco() throws Exception {
        Cuenta duenio = cuenta(PUBLICAR, ARCHIVAR), ajeno = cuenta(PUBLICAR, ARCHIVAR);
        UUID caso = casoCompleto(duenio);
        String etag = etag(caso, duenio);
        assertEquals(403, accion(ajeno, caso, "publicar", etag).statusCode());
        String correo = UUID.randomUUID() + "@prisaly.test";
        catalog.registerUser("Admin2", correo, "Fixture#Clave-2026", IdentityCatalogService.ROL_ADMINISTRADOR);
        assertEquals(403, accion(new Cuenta(null, login(correo)), caso, "publicar", etag).statusCode());
        assertEquals("Borrador", estado(caso));
    }

    @Test void rubricaIncompletaImpidePublicar() throws Exception {
        Cuenta c = cuenta(PUBLICAR);
        UUID caso = casoSinRubrica(c);
        var sinRubrica = accion(c, caso, "publicar", etag(caso, c));
        assertEquals(409, sinRubrica.statusCode(), sinRubrica.body());
        assertTrue(sinRubrica.body().contains("INCOMPLETE_RUBRIC"));
        guardarRubrica(c, caso, "{\"nombre\":\"Sin criterios\"}");
        assertEquals(409, accion(c, caso, "publicar", etag(caso, c)).statusCode());
        assertEquals("Borrador", estado(caso));
    }

    @Test void precondicionesHttp() throws Exception {
        Cuenta c = cuenta(PUBLICAR);
        UUID caso = casoCompleto(c);
        assertEquals(428, accion(c, caso, "publicar", null).statusCode());
        assertEquals(412, accion(c, caso, "publicar", "\"m3-v2:" + "0".repeat(64) + "\"").statusCode());
        assertEquals("Borrador", estado(caso));
    }

    /**
     * N1/F4-H1: permission and ownership are decided before the If-Match precondition. A non-owner holding every
     * M1 permission is answered 403 whatever validator it sends (absent, malformed, stale or the owner's valid one),
     * and nothing is persisted; the owner receives 428/400/412 for the real precondition cause.
     */
    @Test void ajenoConTodosLosPermisosRecibe403AntesDeLaPrecondicion() throws Exception {
        Cuenta duenio = cuenta(PUBLICAR, ARCHIVAR), ajeno = cuenta(PUBLICAR, ARCHIVAR);
        UUID caso = casoCompleto(duenio);
        String valido = etag(caso, duenio), obsoleto = "\"m3-v2:" + "0".repeat(64) + "\"";
        java.util.Map<String, String> validadores = new java.util.LinkedHashMap<>();
        validadores.put("ausente", null);
        validadores.put("malformado", "W/\"x\"");
        validadores.put("obsoleto", obsoleto);
        validadores.put("valido-del-duenio", valido);
        for (var v : validadores.entrySet()) {
            for (String accion : List.of("publicar", "archivar")) {
                var r = accion(ajeno, caso, accion, v.getValue());
                assertEquals(403, r.statusCode(), accion + "/" + v.getKey() + ": " + r.body());
            }
            var patch = escribir(ajeno, "PATCH", caso.toString(), v.getValue(), "{\"titulo\":\"Ajeno\"}");
            assertEquals(403, patch.statusCode(), "PATCH/" + v.getKey() + ": " + patch.body());
            var rubrica = escribir(ajeno, "PUT", caso + "/rubrica", v.getValue(),
                    "{\"nombre\":\"X\",\"criterios\":[{\"id\":\"x\",\"descripcion\":\"X\",\"puntaje\":1}]}");
            assertEquals(403, rubrica.statusCode(), "rubrica/" + v.getKey() + ": " + rubrica.body());
            var escenario = escribir(ajeno, "POST", caso + "/escenarios", v.getValue(),
                    "{\"idEscenario\":\"" + UUID.randomUUID() + "\",\"orden\":9}");
            assertEquals(403, escenario.statusCode(), "escenario/" + v.getKey() + ": " + escenario.body());
        }
        assertEquals(valido, etag(caso, duenio), "a rejected write must not change the aggregate");
        assertEquals("Borrador", estado(caso));
        assertEquals(428, accion(duenio, caso, "publicar", null).statusCode());
        assertEquals(400, accion(duenio, caso, "publicar", "W/\"x\"").statusCode());
        assertEquals(412, accion(duenio, caso, "publicar", obsoleto).statusCode());
        assertEquals(428, accion(duenio, caso, "archivar", null).statusCode());
        assertEquals("Borrador", estado(caso));
        assertEquals(200, accion(duenio, caso, "publicar", valido).statusCode());
    }

    private HttpResponse<String> escribir(Cuenta c, String metodo, String path, String etag, String body) throws Exception {
        var b = req("/v1/casos/" + path, c).header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(body));
        if (etag != null) { b.header("If-Match", etag); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void publicacionesConcurrentesTienenUnSoloGanador() throws Exception {
        Cuenta c = cuenta(PUBLICAR);
        UUID caso = casoCompleto(c);
        String etag = etag(caso, c);
        List<CompletableFuture<HttpResponse<String>>> llamadas = new ArrayList<>();
        for (int i = 0; i < 4; i++) { llamadas.add(http.sendAsync(peticion(c, caso, "publicar", etag), HttpResponse.BodyHandlers.ofString())); }
        var estados = new ArrayList<Integer>();
        for (var l : llamadas) { estados.add(l.get().statusCode()); }
        assertEquals(1, estados.stream().filter(s -> s == 200).count(), estados.toString());
        assertTrue(estados.stream().allMatch(s -> s == 200 || s == 412 || s == 409), estados.toString());
        assertEquals("Publicado", estado(caso));
    }

    @Test void archivarYPublicarConcurrentesNoProducenEstadosInvalidos() throws Exception {
        Cuenta c = cuenta(PUBLICAR, ARCHIVAR);
        UUID caso = casoCompleto(c);
        String etag = accion(c, caso, "publicar", etag(caso, c)).headers().firstValue("ETag").orElseThrow();
        var uno = http.sendAsync(peticion(c, caso, "archivar", etag), HttpResponse.BodyHandlers.ofString());
        var dos = http.sendAsync(peticion(c, caso, "archivar", etag), HttpResponse.BodyHandlers.ofString());
        var estados = List.of(uno.get().statusCode(), dos.get().statusCode()).stream().sorted().collect(Collectors.toList());
        assertEquals(200, estados.get(0), estados.toString());
        assertTrue(estados.get(1) == 412 || estados.get(1) == 409, estados.toString());
        assertEquals("Archivado", estado(caso));
    }

    @Test void archivadoNoSePuedeProgramarYUnCasoBloqueadoSePuedeArchivar() throws Exception {
        Cuenta c = cuenta(PUBLICAR, ARCHIVAR);
        UUID caso = casoCompleto(c);
        String etag = accion(c, caso, "publicar", etag(caso, c)).headers().firstValue("ETag").orElseThrow();
        sesion(caso, c.id(), 1);
        assertNotNull(jdbc.queryForObject("SELECT \"fechaBloqueoAcademico\" FROM \"Caso\" WHERE \"idCaso\"=?", Timestamp.class, caso));
        var a = accion(c, caso, "archivar", etag(caso, c));
        assertEquals(200, a.statusCode(), a.body());
        assertThrows(RuntimeException.class, () -> sesion(caso, c.id(), 2));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Sesion\" WHERE \"idCaso\"=?", Integer.class, caso));
        assertNotNull(etag);
    }

    @Test void registraQuienEjecutoYCuando() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(CasoTransicionService.LOGGER_AUDITORIA);
        ListAppender<ILoggingEvent> lista = new ListAppender<>();
        lista.start();
        logger.addAppender(lista);
        try {
            Cuenta c = cuenta(PUBLICAR);
            UUID caso = casoCompleto(c);
            accion(c, caso, "publicar", etag(caso, c));
            accion(c, caso, "archivar", etag(caso, c));
            var lineas = lista.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(lineas.stream().anyMatch(l -> l.contains("accion=PUBLICAR caso=" + caso + " usuario=" + c.id() + " fecha=") && l.endsWith("resultado=EXITOSO")), lineas.toString());
            assertTrue(lineas.stream().anyMatch(l -> l.contains("accion=ARCHIVAR caso=" + caso) && l.endsWith("resultado=RECHAZADO:FORBIDDEN")), lineas.toString());
        } finally { logger.detachAppender(lista); }
    }

    private void sesion(UUID caso, UUID docente, int dia) {
        Instant inicio = Instant.parse("2030-01-01T10:00:00Z").plusSeconds(86400L * dia);
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico","inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'E',?,?,60)""", UUID.randomUUID(), caso, docente, Timestamp.from(inicio), Timestamp.from(inicio.plusSeconds(3600)));
    }
    private String estado(UUID caso) { return jdbc.queryForObject("SELECT \"estado\" FROM \"Caso\" WHERE \"idCaso\"=?", String.class, caso); }
    private HttpRequest peticion(Cuenta c, UUID caso, String accion, String etag) {
        var b = req("/v1/casos/" + caso + "/" + accion, c).POST(HttpRequest.BodyPublishers.noBody());
        if (etag != null) { b.header("If-Match", etag); }
        return b.build();
    }
    private HttpResponse<String> accion(Cuenta c, UUID caso, String accion, String etag) throws Exception {
        return http.send(peticion(c, caso, accion, etag), HttpResponse.BodyHandlers.ofString());
    }
    private String etag(UUID caso, Cuenta c) throws Exception {
        return http.send(req("/v1/casos/" + caso, c).GET().build(), HttpResponse.BodyHandlers.ofString()).headers().firstValue("ETag").orElseThrow();
    }
    private UUID casoCompleto(Cuenta c) throws Exception {
        UUID caso = casoSinRubrica(c);
        guardarRubrica(c, caso, "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":1}]}");
        return caso;
    }
    private void guardarRubrica(Cuenta c, UUID caso, String body) throws Exception {
        var r = http.send(req("/v1/casos/" + caso + "/rubrica", c).header("Content-Type", "application/json").header("If-Match", etag(caso, c))
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
    }
    private UUID casoSinRubrica(Cuenta c) throws Exception {
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), pregunta = UUID.randomUUID();
        var creado = http.send(req("/v1/casos", c).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idCaso\":\"" + caso + "\",\"titulo\":\"Caso\",\"tiempoEstimadoSegundos\":300}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, creado.statusCode(), creado.body());
        String etag = etag(caso, c);
        etag = post(c, caso + "/escenarios", etag, "{\"idEscenario\":\"" + escenario + "\",\"orden\":1}");
        etag = post(c, caso + "/escenarios/" + escenario + "/preguntas", etag, "{\"idPregunta\":\"" + pregunta + "\",\"enunciado\":\"P\",\"orden\":1}");
        post(c, caso + "/preguntas/" + pregunta + "/opciones", etag, "{\"idOpcion\":\"" + UUID.randomUUID() + "\",\"texto\":\"C\",\"esCorrecta\":true,\"explicacion\":\"E\",\"orden\":1}");
        return caso;
    }
    private String post(Cuenta c, String path, String etag, String body) throws Exception {
        var r = http.send(req("/v1/casos/" + path, c).header("Content-Type", "application/json").header("If-Match", etag)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, r.statusCode(), r.body());
        return r.headers().firstValue("ETag").orElseThrow();
    }
    private HttpRequest.Builder req(String path, Cuenta c) { return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + c.token()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private Cuenta cuenta(String... extra) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        String correo = UUID.randomUUID() + "@prisaly.test";
        Usuario u = catalog.registerUser("Docente", correo, "Fixture#Clave-2026", rol);
        catalog.assignPermission(u.getIdUsuario(), permiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), admin); // DM1-02: setup only
        catalog.assignPermission(u.getIdUsuario(), permiso(CONSULTAR), admin);
        catalog.assignPermission(u.getIdUsuario(), permiso(MODIFICAR), admin);
        for (String p : extra) { catalog.assignPermission(u.getIdUsuario(), permiso(p), admin); }
        return new Cuenta(u.getIdUsuario(), login(correo));
    }
    private String login(String correo) throws Exception {
        var r = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"Fixture#Clave-2026\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }
    private Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }
}
