package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m7.infrastructure.CifradorCodigoAccesoAesGcm;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Lectura real HTTP y PostgreSQL 16.15. DD-02 se habilita solo en esta fixture QA
 * con claves desechables; no cambia la configuración productiva.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.springframework.context.annotation.Import(ContenidoIntentoEstudianteHttpIT.SnapshotConfig.class)
class ContenidoIntentoEstudianteHttpIT {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class SnapshotConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.prisaly.m3.api.ContenidoCasoEstudiantilPort contenidoConVerificacionSnapshot(
                @org.springframework.beans.factory.annotation.Qualifier("contenidoCasoEstudiantilJdbcAdapter")
                com.prisaly.m3.api.ContenidoCasoEstudiantilPort delegado, JdbcTemplate jdbc) {
            return id -> {
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals("repeatable read", jdbc.queryForObject("SHOW transaction_isolation", String.class));
                assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
                return delegado.consultar(id);
            };
        }
    }

    static final String CLAVE = "ControlledPassword#123";
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        // DC-DD02-B4-B5-01: candidate DD-02 alternative B (V014-V016 dd02b), disposable keys, QA fixture only.
        AccesoEstudianteQa.propiedades(p);
        p.add("prisaly.m7.worker.enabled", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired CifradorCodigoAccesoAesGcm cifrador;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    // Per-test chain: student A owns an attempt; student B is invited to the same session with its own attempt.
    UUID sesion, intentoA, intentoB, preguntaA1, opcionA1a, opcionA1b, opcionOtraPregunta, convocatoriaA;
    String cookieA, cookieB, bearerDocente;

    void cadena(int reserva) throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        UUID usuarioA = estudianteConCuenta("a" + s), usuarioB = estudianteConCuenta("b" + s);
        UUID estudianteA = idEstudiante(usuarioA), estudianteB = idEstudiante(usuarioB);
        String rolDocente = "DocRecorrido-" + s;
        catalog.createRole(rolDocente, "Controlled teacher fixture");
        String correoDocente = "doc" + s + "@recorrido.prisaly.test";
        UUID docente = catalog.registerUser("Docente recorrido", correoDocente, CLAVE, rolDocente).getIdUsuario();
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID();
        preguntaA1 = UUID.randomUUID();
        UUID pregunta2 = UUID.randomUUID();
        opcionA1a = UUID.randomUUID(); opcionA1b = UUID.randomUUID(); opcionOtraPregunta = UUID.randomUUID();
        sesion = UUID.randomUUID();
        convocatoriaA = UUID.randomUUID();
        UUID convocatoriaB = UUID.randomUUID(), participacionA = UUID.randomUUID(), participacionB = UUID.randomUUID();
        intentoA = UUID.randomUUID(); intentoB = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,600,'Publicado')",
                caso, docente, "Caso recorrido " + s);
        jdbc.update("INSERT INTO \"Escenario\" (\"idEscenario\",\"idCaso\",\"orden\") VALUES (?,?,1)", escenario, caso);
        jdbc.update("INSERT INTO \"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"estadoEdicion\",\"orden\") VALUES (?,?,'P1','Lista',1),(?,?,'P2','Lista',2)",
                preguntaA1, escenario, pregunta2, escenario);
        jdbc.update("INSERT INTO \"OpcionRespuesta\" (\"idOpcion\",\"idPregunta\",\"texto\",\"orden\") VALUES (?,?,'1A',1),(?,?,'1B',2),(?,?,'2A',1)",
                opcionA1a, preguntaA1, opcionA1b, preguntaA1, opcionOtraPregunta, pregunta2);
        jdbc.update("UPDATE \"Caso\" SET \"historia\"='Historia persistida', \"desarrolloSituacional\"='Desarrollo persistido' WHERE \"idCaso\"=?", caso);
        jdbc.update("UPDATE \"Escenario\" SET \"titulo\"='Escenario persistido', \"contexto\"='Contexto persistido', \"instrucciones\"='Instrucciones persistidas' WHERE \"idEscenario\"=?", escenario);
        jdbc.update("UPDATE \"OpcionRespuesta\" SET \"esCorrecta\"=true, \"explicacion\"='SOLUCION-NO-PUBLICABLE' WHERE \"idOpcion\"=?", opcionA1a);
        if (reserva == 1) jdbc.update("UPDATE \"Pregunta\" SET \"visible\"=false WHERE \"idPregunta\"=?", preguntaA1);
        if (reserva == 2) jdbc.update("UPDATE \"Escenario\" SET \"activo\"=false WHERE \"idEscenario\"=?", escenario);
        if (reserva == 3) jdbc.update("UPDATE \"Pregunta\" SET \"estadoEdicion\"='Borrador' WHERE \"idPregunta\"=?", preguntaA1);
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio recorrido',now()-interval '1 hour',now()+interval '1 hour',3600)""", sesion, caso, docente);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?),(?,?,?)",
                convocatoriaA, sesion, estudianteA, convocatoriaB, sesion, estudianteB);
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?),(?,?)",
                participacionA, convocatoriaA, participacionB, convocatoriaB);
        jdbc.update("""
                INSERT INTO "Intento" ("idIntento","idParticipacion","numeroIntento","inicio","limiteTemporal","estado")
                VALUES (?,?,1,now(),now()+interval '1 hour','EnCurso'),(?,?,1,now(),now()+interval '1 hour','EnCurso')""",
                intentoA, participacionA, intentoB, participacionB);
        cookieA = login("a" + s);
        cookieB = login("b" + s);
        bearerDocente = bearer(correoDocente);
    }


    private String ruta() { return "/v1/estudiantes/intentos/" + intentoA + "/contenido"; }

    @Test void contenidoRealDelCasoProgramadoSinSolucionesNiEscrituras() throws Exception {
        cadena(0);
        String version = jdbc.queryForObject("SELECT xmin::text FROM \"Intento\" WHERE \"idIntento\"=?", String.class, intentoA);
        var r = get(ruta(), cookieA, null);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("no-store", r.headers().firstValue("Cache-Control").orElseThrow());
        var dto = json.readTree(r.body());
        assertEquals(intentoA.toString(), dto.get("idIntento").asText());
        assertEquals(sesion.toString(), dto.get("idSesion").asText());
        assertEquals(jdbc.queryForObject("SELECT \"idCaso\"::text FROM \"Sesion\" WHERE \"idSesion\"=?",String.class,sesion), dto.get("caso").get("idCaso").asText());
        assertEquals("Historia persistida", dto.get("caso").get("historia").asText());
        var e = dto.get("caso").get("escenarios").get(0);
        assertEquals("Contexto persistido", e.get("contexto").asText());
        assertEquals("Instrucciones persistidas", e.get("instrucciones").asText());
        assertEquals(2, e.get("preguntas").size());
        var p = e.get("preguntas").get(0);
        assertEquals(preguntaA1.toString(), p.get("idPregunta").asText());
        assertEquals(opcionA1a.toString(), p.get("opciones").get(0).get("idOpcion").asText());
        assertEquals("1B", p.get("opciones").get(1).get("texto").asText());
        for (String prohibido : new String[]{"esCorrecta", "explicacion", "SOLUCION-NO-PUBLICABLE", "rubrica", "peso", "idDocente", "guion"}) {
            assertFalse(r.body().contains(prohibido), prohibido);
        }
        assertEquals(version, jdbc.queryForObject("SELECT xmin::text FROM \"Intento\" WHERE \"idIntento\"=?", String.class, intentoA));
        assertEquals(0, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intentoA));
        // Existing GET/PUT contracts still work with the IDs delivered by this route.
        assertEquals(200, get("/v1/estudiantes/intentos/"+intentoA,cookieA,null).statusCode());
        assertEquals(200, put(intentoA,preguntaA1,opcionA1a,0,cookieA,null).statusCode());
        assertEquals(r.body(), get(ruta(), cookieA,null).body());
    }

    @Test void identidadTitularidadYNoEnumeracionDeContenido() throws Exception {
        cadena(0);
        assertEquals(401,get(ruta(),null,null).statusCode());
        assertEquals(403,get(ruta(),cookieB,null).statusCode());
        int personal = get(ruta(),null,bearerDocente).statusCode();
        assertTrue(personal==401 || personal==403);
        var manipulada = http.send(HttpRequest.newBuilder(uri(ruta())).header("Cookie",cookieB)
                .header("X-Student-Id",intentoA.toString()).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,manipulada.statusCode());
        assertFalse(manipulada.body().contains("Historia persistida"));
        assertEquals(404,get("/v1/estudiantes/intentos/"+UUID.randomUUID()+"/contenido",cookieA,null).statusCode());
        assertEquals(400,get("/v1/estudiantes/intentos/no-uuid/contenido",cookieA,null).statusCode());
        assertEquals(get(ruta(),cookieA,null).body(),get(ruta()+"?idCaso="+UUID.randomUUID()+"&idEstudiante="+UUID.randomUUID(),cookieA,null).body());
    }

    @Test void noEntregaPreguntasOcultasNiSeleccionaUnSubconjunto() throws Exception { verificarReserva(1); }
    @Test void noEntregaEscenariosInactivos() throws Exception { verificarReserva(2); }
    @Test void noEntregaPreguntasBorrador() throws Exception { verificarReserva(3); }
    private void verificarReserva(int modo) throws Exception {
        cadena(modo);
        var r=get(ruta(),cookieA,null);
        assertEquals(409,r.statusCode(),r.body());
        assertTrue(r.body().contains("CONTENIDO_INTENTO_NO_DISPONIBLE"));
        assertFalse(r.body().contains("Historia persistida"));
        assertEquals(0,cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?",intentoA));
    }

    @Test void finalizarNoPublicaSolucionesNiCambiaLaLecturaExistente() throws Exception {
        cadena(0);
        String antes=get(ruta(),cookieA,null).body();
        jdbc.update("UPDATE \"Intento\" SET \"estado\"='Finalizado', \"fin\"=now(), \"motivoFinalizacion\"='ENTREGA_ESTUDIANTE' WHERE \"idIntento\"=?",intentoA);
        var despues=get(ruta(),cookieA,null);
        assertEquals(200,despues.statusCode(),despues.body());
        assertEquals(antes,despues.body());
    }

    private static void observar(String caso, HttpResponse<String> r) {
        System.out.println("RECORRIDO-OBSERVADO " + caso + " " + r.statusCode() + " " + r.body());
    }
    private UUID estudianteConCuenta(String alias) {
        String rol = "EstRecorrido-" + alias;
        catalog.createRole(rol, "Controlled student fixture");
        UUID usuario = catalog.registerUser("Estudiante " + alias, alias + "@recorrido.prisaly.test", CLAVE, rol).getIdUsuario();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), usuario);
        return usuario;
    }
    private UUID idEstudiante(UUID usuario) {
        return jdbc.queryForObject("SELECT \"idEstudiante\" FROM \"Estudiante\" WHERE \"idUsuario\"=?", UUID.class, usuario);
    }
    private int cantidad(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private String login(String alias) throws Exception {
        return AccesoEstudianteQa.login(http, port, jdbc, cifrador, alias + "@recorrido.prisaly.test");
    }
    private String bearer(String correo) throws Exception {
        var r = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private HttpResponse<String> get(String path, String cookie, String token) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) b.header("Cookie", cookie);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> put(UUID intento, UUID pregunta, UUID opcion, int revision, String cookie, String token) throws Exception {
        var b = HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/" + intento + "/respuestas/" + pregunta))
                .header("Origin", "http://localhost:4200").header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"idOpcionSeleccionada\":\"" + opcion + "\",\"revisionEsperada\":" + revision + "}"));
        if (cookie != null) b.header("Cookie", cookie);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> send(String method, String path, String cookie, String body) throws Exception {
        var b = HttpRequest.newBuilder(uri(path)).header("Origin", "http://localhost:4200")
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
