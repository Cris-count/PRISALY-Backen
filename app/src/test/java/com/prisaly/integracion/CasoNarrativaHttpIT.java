package com.prisaly.integracion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;

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
 * DC-BE-SIM-INCREMENTO-A-15, teacher narrative routes over the real APP and PostgreSQL 16.15. <b>QA only</b>: the
 * switch {@code prisaly.m3.narrativa.publicacion.enabled} and the candidate schema V017 (contract {@code sim-a}) are
 * enabled in THIS context only; the shipped configuration keeps both off. Script contents are test data.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CasoNarrativaHttpIT {
    static final String CLAVE = "Fixture#Clave-2026";
    static final String GUION = """
            {"formato":"prisaly-guion-1","inicio":"entrada",
             "espacios":[{"idEspacio":"entrada","titulo":"Entrada (prueba)"}],
             "personajes":[{"idPersonaje":"guia","nombre":"Guía (prueba)"}],
             "intervenciones":[{"idIntervencion":"hola","idEspacio":"entrada","idPersonaje":"guia",
               "dialogos":[{"idPersonaje":"guia","texto":"Conversación informativa de prueba."}]}]}""";
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/sim-propuesta");
        r.add("prisaly.m4.schema-contract", () -> M4SchemaContract.V013_SIM_A);
        r.add("prisaly.m3.narrativa.publicacion.enabled", () -> "true");
        r.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
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
        admin = catalog.registerUser("Admin", UUID.randomUUID() + "@prisaly.test", CLAVE, IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    @Test void elAutorGuardaConsultaYPublicaElGuion() throws Exception {
        Cuenta autor = docente(true, true, true);
        UUID caso = caso(autor.id());
        var guardado = enviar("PUT", caso, "", autor, GUION);
        assertEquals(200, guardado.statusCode(), guardado.body());
        assertTrue(guardado.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode b = json.readTree(guardado.body());
        assertEquals("Borrador", b.get("estado").asText());
        assertEquals(1, b.get("version").asInt());
        assertTrue(b.get("definicion").isObject(), "the document is returned as JSON, not as a string");
        assertEquals("entrada", b.get("definicion").get("inicio").asText());

        var publicado = enviar("POST", caso, "/publicar", autor, null);
        assertEquals(200, publicado.statusCode(), publicado.body());
        assertEquals("Publicado", json.readTree(publicado.body()).get("estado").asText());

        var vista = enviar("GET", caso, "", autor, null);
        assertEquals(200, vista.statusCode(), vista.body());
        JsonNode v = json.readTree(vista.body());
        assertTrue(v.get("borrador").isNull());
        assertEquals(b.get("hashDefinicion").asText(), v.get("publicado").get("hashDefinicion").asText());
    }

    @Test void autoriaPermisosYAutenticacion() throws Exception {
        Cuenta autor = docente(true, true, true);
        UUID caso = caso(autor.id());
        Cuenta otro = docente(true, true, true);
        assertEquals(403, enviar("PUT", caso, "", otro, GUION).statusCode());
        assertEquals(403, enviar("GET", caso, "", otro, null).statusCode());
        // Owners lacking a permission: MODIFICAR_CASO for the draft, PUBLICAR_CASO for publishing (proposal D-SIM-8).
        Cuenta sinModificar = docente(true, false, true);
        assertEquals(403, enviar("PUT", caso(sinModificar.id()), "", sinModificar, GUION).statusCode());
        Cuenta sinPublicar = docente(true, true, false);
        UUID casoSinPublicar = caso(sinPublicar.id());
        assertEquals(200, enviar("PUT", casoSinPublicar, "", sinPublicar, GUION).statusCode());
        assertEquals(403, enviar("POST", casoSinPublicar, "/publicar", sinPublicar, null).statusCode());
        var anonimo = http.send(HttpRequest.newBuilder(uri("/v1/casos/" + caso + "/narrativa")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonimo.statusCode());
        assertEquals(400, http.send(HttpRequest.newBuilder(uri("/v1/casos/no-es-uuid/narrativa"))
                .header("Authorization", "Bearer " + autor.token()).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404, enviar("GET", UUID.randomUUID(), "", autor, null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"GuionNarrativoCaso\" WHERE \"idCaso\"=? AND \"estado\"='Publicado'",
                Integer.class, caso));
    }

    @Test void documentoInvalidoYBloqueoAcademico() throws Exception {
        Cuenta autor = docente(true, true, true);
        UUID caso = caso(autor.id());
        var invalido = enviar("PUT", caso, "", autor, GUION.replace("\"inicio\":\"entrada\"",
                "\"inicio\":\"entrada\",\"preguntas\":[{\"idPregunta\":\"x\",\"esCorrecta\":true}]"));
        assertEquals(422, invalido.statusCode(), invalido.body());
        JsonNode e = json.readTree(invalido.body());
        assertEquals("GUION_INVALIDO", e.get("code").asText());
        assertEquals("preguntas", e.get("errores").get(0).get("ruta").asText());
        assertEquals("CAMPO_ACADEMICO_PROHIBIDO", e.get("errores").get(0).get("motivo").asText());
        assertEquals(409, enviar("POST", caso, "/publicar", autor, null).statusCode(), "no draft yet");
        assertEquals(200, enviar("PUT", caso, "", autor, GUION).statusCode());
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio SIM',now()+interval '1 day',now()+interval '1 day 2 hours',1800)""",
                UUID.randomUUID(), caso, autor.id());
        var bloqueado = enviar("PUT", caso, "", autor, GUION);
        assertEquals(409, bloqueado.statusCode());
        assertEquals("CONTENIDO_BLOQUEADO", json.readTree(bloqueado.body()).get("code").asText());
        assertEquals("CONTENIDO_BLOQUEADO", json.readTree(enviar("POST", caso, "/publicar", autor, null).body()).get("code").asText());
    }

    /**
     * CB-05A, tramo Backend (DC-CB-03/04). Caso creado por HTTP → narrativa «Hospital + médico» con el catálogo canónico
     * → nueva consulta → fila real en PostgreSQL con la huella canónica recalculada. Una configuración incompatible se
     * rechaza sin tocar la fila.
     */
    static final String HOSPITAL_MEDICO = """
            {"formato":"prisaly-guion-1","inicio":"hospital",
             "espacios":[{"idEspacio":"hospital","titulo":"Hospital","recursoVisual":"edificio:hospital"}],
             "personajes":[{"idPersonaje":"dr-martinez","nombre":"Dr. Martínez","rol":"Médico tratante","recursoVisual":"npc:hosp-npc-doctor"}],
             "intervenciones":[{"idIntervencion":"medico-conversacion","idEspacio":"hospital","idPersonaje":"dr-martinez",
               "dialogos":[{"idPersonaje":"dr-martinez","texto":"Buenos días. Soy el médico encargado de la atención del paciente."},
                           {"idPersonaje":"dr-martinez","texto":"Durante la valoración se observaron dificultades emocionales relacionadas con su entorno familiar."},
                           {"idPersonaje":"dr-martinez","texto":"Puedes continuar tu investigación consultando a las instituciones correspondientes."}]}]}""";

    @Test void cb05aHospitalYMedicoPersistenEnPostgreSqlYSeRecuperanIguales() throws Exception {
        Cuenta autor = docente(true, true, true);
        UUID caso = UUID.randomUUID();
        var creado = http.send(HttpRequest.newBuilder(uri("/v1/casos")).header("Authorization", "Bearer " + autor.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idCaso\":\"" + caso + "\",\"titulo\":\"CB-05A Hospital y médico\","
                        + "\"tiempoEstimadoSegundos\":1800,\"historia\":\"Caso de prueba del Case Builder.\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, creado.statusCode(), creado.body());

        var guardado = enviar("PUT", caso, "", autor, HOSPITAL_MEDICO);
        assertEquals(200, guardado.statusCode(), guardado.body());
        JsonNode g = json.readTree(guardado.body());
        JsonNode enviado = json.readTree(HOSPITAL_MEDICO);
        assertEquals(enviado, g.get("definicion"));

        var vista = enviar("GET", caso, "", autor, null);
        assertEquals(200, vista.statusCode(), vista.body());
        JsonNode borrador = json.readTree(vista.body()).get("borrador");
        assertEquals(enviado, borrador.get("definicion"), "nivel 2: la nueva consulta devuelve la narrativa enviada");
        assertEquals(g.get("hashDefinicion").asText(), borrador.get("hashDefinicion").asText());

        var fila = jdbc.queryForMap("SELECT \"definicion\"::text AS d, \"hashDefinicion\" AS h, \"version\" AS v, \"estado\" AS e "
                + "FROM \"GuionNarrativoCaso\" WHERE \"idCaso\" = ?", caso);
        assertEquals(enviado, json.readTree((String) fila.get("d")), "nivel 3: PostgreSQL conserva el documento");
        String recalculada = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                com.prisaly.m3.infrastructure.persistence.jpa.JsonbCanonicalizer.canonicalize((String) fila.get("d"))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(recalculada, ((String) fila.get("h")).trim(), "nivel 3: la huella persistida es la del documento");
        assertEquals(1, ((Number) fila.get("v")).intValue());
        assertEquals("Borrador", fila.get("e"));

        var incompatible = enviar("PUT", caso, "", autor, HOSPITAL_MEDICO.replace("npc:hosp-npc-doctor", "npc:pol-npc-oficial"));
        assertEquals(422, incompatible.statusCode(), incompatible.body());
        assertTrue(incompatible.body().contains("PERSONAJE_FUERA_DE_SU_EDIFICIO"), incompatible.body());
        assertEquals(fila.get("h"), jdbc.queryForObject("SELECT \"hashDefinicion\" FROM \"GuionNarrativoCaso\" WHERE \"idCaso\" = ?",
                String.class, caso), "el rechazo no modifica la fila");
    }

    private UUID caso(UUID docente) {
        UUID caso = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,600,'Publicado')",
                caso, docente, "Caso narrativa " + caso.toString().substring(0, 8));
        return caso;
    }

    private HttpResponse<String> enviar(String metodo, UUID caso, String sufijo, Cuenta c, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(uri("/v1/casos/" + caso + "/narrativa" + sufijo)).header("Authorization", "Bearer " + c.token())
                .header("Content-Type", "application/json")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private Cuenta docente(boolean consultar, boolean modificar, boolean publicar) throws Exception {
        String rol = "RolSim-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        Usuario u = catalog.registerUser("Docente", UUID.randomUUID() + "@prisaly.test", CLAVE, rol);
        catalog.assignPermission(u.getIdUsuario(), permiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), admin);
        if (consultar) { catalog.assignPermission(u.getIdUsuario(), permiso("CONSULTAR_CASO"), admin); }
        if (modificar) { catalog.assignPermission(u.getIdUsuario(), permiso("MODIFICAR_CASO"), admin); }
        if (publicar) { catalog.assignPermission(u.getIdUsuario(), permiso("PUBLICAR_CASO"), admin); }
        var login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + u.getCorreo() + "\",\"password\":\"" + CLAVE + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return new Cuenta(u.getIdUsuario(), json.readTree(login.body()).get("accessToken").asText());
    }

    private Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }
}
