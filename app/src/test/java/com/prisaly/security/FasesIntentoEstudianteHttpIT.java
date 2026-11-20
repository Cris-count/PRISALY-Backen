package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m3.infrastructure.persistence.jdbc.GuionNarrativoJdbcAdapter;
import com.prisaly.m4.infrastructure.config.M4SchemaContract;
import com.prisaly.m7.infrastructure.CifradorCodigoAccesoAesGcm;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-SIM-B2B-B3-32: las rutas P1–P4 y las decisiones D-SIM-2, 3, 4 y 10 por HTTP real, con la sesión estudiantil de
 * M1 (DD-02 B), la cadena de seguridad, el Proxy de M5 y PostgreSQL 16.15. Esquema QA {@code bd0052-v013-dd02b-sim-ab}
 * con claves desechables; los interruptores se encienden solo en esta fixture y no cambian la configuración productiva.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Fases académicas P1–P4 por HTTP (B2b/B3) en PostgreSQL 16.15")
class FasesIntentoEstudianteHttpIT {

    static final String CLAVE = "ControlledPassword#123";
    static final String GUION = """
            {"formato":"prisaly-guion-1","inicio":"entrada",
             "espacios":[{"idEspacio":"entrada","titulo":"Entrada (prueba HTTP)"}],
             "personajes":[{"idPersonaje":"guia","nombre":"Guía (prueba HTTP)"}],
             "intervenciones":[]}""";

    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        AccesoEstudianteQa.propiedades(p);
        // DD-02 B + Incremento A/B candidatos: solo en esta fixture QA.
        p.add("spring.flyway.locations", () ->
                "classpath:db/migration,classpath:db/dd02b-propuesta,classpath:db/sim-propuesta,classpath:db/sim-propuesta-b");
        p.add("prisaly.m4.schema-contract", () -> M4SchemaContract.V013_DD02B_SIM_AB);
        p.add("prisaly.m3.narrativa.publicacion.enabled", () -> "true");
        p.add("prisaly.m5.exploracion.fases.enabled", () -> "true");
        p.add("prisaly.m5.exploracion.fases.http.enabled", () -> "true");
        // Solo para ejercer D-SIM-4 por el contrato existente POST /finalizar.
        p.add("prisaly.m5.inicio-finalizacion.publicacion.enabled", () -> "true");
        p.add("prisaly.m7.worker.enabled", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired CifradorCodigoAccesoAesGcm cifrador;
    @Autowired ObjectMapper json;
    @Autowired GuionNarrativoJdbcAdapter guiones;
    @Autowired PlatformTransactionManager transacciones;
    private final HttpClient http = HttpClient.newHttpClient();

    /** Intento A (con fases salvo que se indique), su dueño y un segundo estudiante de la misma sesión. */
    UUID intento, pregunta1, pregunta2, opcion1, casoActual, docenteActual;
    String cookie, cookieOtro, bearerDocente;

    enum Modo { CON_FASES, SIN_FASES, VENCIDO }

    void cadena(Modo modo) throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        UUID usuarioA = estudianteConCuenta("a" + s), usuarioB = estudianteConCuenta("b" + s);
        String rolDocente = "DocFases-" + s;
        catalog.createRole(rolDocente, "Controlled teacher fixture");
        String correoDocente = "doc" + s + "@fases.prisaly.test";
        UUID docente = catalog.registerUser("Docente fases", correoDocente, CLAVE, rolDocente).getIdUsuario();
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID();
        casoActual = caso;
        docenteActual = docente;
        pregunta1 = UUID.randomUUID(); pregunta2 = UUID.randomUUID(); opcion1 = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"historia\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,'Historia fases',600,'Publicado')",
                caso, docente, "Caso fases " + s);
        jdbc.update("INSERT INTO \"Escenario\" (\"idEscenario\",\"idCaso\",\"titulo\",\"orden\") VALUES (?,?,'Escenario fases',1)", escenario, caso);
        jdbc.update("INSERT INTO \"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"estadoEdicion\",\"orden\") VALUES (?,?,'ENUNCIADO-UNO','Lista',1),(?,?,'ENUNCIADO-DOS','Lista',2)",
                pregunta1, escenario, pregunta2, escenario);
        jdbc.update("INSERT INTO \"OpcionRespuesta\" (\"idOpcion\",\"idPregunta\",\"texto\",\"orden\") VALUES (?,?,'1A',1),(?,?,'2A',1)",
                opcion1, pregunta1, UUID.randomUUID(), pregunta2);
        UUID guion = modo == Modo.SIN_FASES ? null : new TransactionTemplate(transacciones).execute(tx -> {
            guiones.guardarBorrador(caso, docente, GUION);
            return guiones.publicarBorrador(caso).idGuion();
        });
        UUID sesion = UUID.randomUUID(), convocatoriaA = UUID.randomUUID(), convocatoriaB = UUID.randomUUID();
        UUID participacionA = UUID.randomUUID(), participacionB = UUID.randomUUID(), intentoB = UUID.randomUUID();
        intento = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio fases',now()-interval '1 hour',now()+interval '1 hour',3600)""", sesion, caso, docente);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?),(?,?,?)",
                convocatoriaA, sesion, idEstudiante(usuarioA), convocatoriaB, sesion, idEstudiante(usuarioB));
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?),(?,?)",
                participacionA, convocatoriaA, participacionB, convocatoriaB);
        String limite = modo == Modo.VENCIDO ? "now()-interval '1 minute'" : "now()+interval '1 hour'";
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\") VALUES"
                + " (?,?,1,now()-interval '30 minutes'," + limite + ",'EnCurso'),(?,?,1,now(),now()+interval '1 hour','EnCurso')",
                intento, participacionA, intentoB, participacionB);
        if (guion != null) {
            jdbc.update("INSERT INTO \"FaseIntento\" (\"idIntento\",\"idGuion\") VALUES (?,?)", intento, guion);
        }
        cookie = AccesoEstudianteQa.login(http, port, jdbc, cifrador, "a" + s + "@fases.prisaly.test");
        cookieOtro = AccesoEstudianteQa.login(http, port, jdbc, cifrador, "b" + s + "@fases.prisaly.test");
        bearerDocente = bearer(correoDocente);
    }

    // --- Recorrido completo con fases ----------------------------------------------------------------------------

    @Test @DisplayName("recorrido: exploración (narrativa sí, preguntas no) → cuestionario idempotente → respuestas → finalizar → narrativa cerrada")
    void recorridoConFases() throws Exception {
        cadena(Modo.CON_FASES);
        JsonNode fase = ok(get("fase"));
        assertEquals("EXPLORACION", fase.get("fase").asText());
        assertEquals("EnCurso", fase.get("estadoIntento").asText());
        assertTrue(fase.get("fechaInicioCuestionario").isNull());
        JsonNode narrativa = ok(get("narrativa"));
        assertEquals(1, narrativa.get("version").asInt());
        assertEquals("prisaly-guion-1", narrativa.get("definicion").get("formato").asText());
        assertEquals(jdbc.queryForObject("SELECT \"hashDefinicion\" FROM \"GuionNarrativoCaso\" WHERE \"idGuion\"=?::uuid", String.class,
                narrativa.get("idGuion").asText()), narrativa.get("hashDefinicion").asText());
        // D-SIM-2: sin preguntas en exploración, ni por /contenido ni por P4.
        sinPreguntas(error(get("contenido"), 409, "CUESTIONARIO_NO_INICIADO"));
        sinPreguntas(error(get("completitud"), 409, "CUESTIONARIO_NO_INICIADO"));
        error(put(pregunta1, opcion1, 0, cookie), 409, "CUESTIONARIO_NO_INICIADO");
        // P3 idempotente.
        JsonNode entrada = ok(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN));
        assertFalse(entrada.get("yaIniciado").asBoolean());
        JsonNode reintento = ok(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN));
        assertTrue(reintento.get("yaIniciado").asBoolean());
        assertEquals(entrada.get("fechaInicioCuestionario"), reintento.get("fechaInicioCuestionario"));
        // Recuperación tras recargar: la fase persistida.
        JsonNode recargada = ok(get("fase"));
        assertEquals("CUESTIONARIO", recargada.get("fase").asText());
        assertEquals(entrada.get("fechaInicioCuestionario"), recargada.get("fechaInicioCuestionario"));
        // R4: la narrativa se cierra; las preguntas ya se leen.
        error(get("narrativa"), 409, "EXPLORACION_CERRADA");
        JsonNode contenido = ok(get("contenido"));
        assertEquals(2, contenido.get("caso").get("escenarios").get(0).get("preguntas").size());
        assertEquals(200, put(pregunta1, opcion1, 0, cookie).statusCode());
        JsonNode completitud = ok(get("completitud"));
        assertEquals(2, completitud.get("preguntasPresentables").asInt());
        assertEquals(1, completitud.get("respondidas").asInt());
        assertEquals(pregunta2.toString(), completitud.get("sinResponder").get(0).asText());
        // Finalizar y D-SIM-10.
        assertEquals(200, post("finalizar", cookie, AccesoEstudianteQa.ORIGIN).statusCode());
        error(get("narrativa"), 409, "EXPLORACION_CERRADA");
        error(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN), 409, "INTENTO_FINALIZADO");
        JsonNode cerrada = ok(get("fase"));
        assertEquals("Finalizado", cerrada.get("estadoIntento").asText());
        assertEquals("CUESTIONARIO", cerrada.get("fase").asText());
    }

    @Test @DisplayName("D-SIM-4: POST /finalizar desde exploración, idempotente, sin nota; después preguntas ocultas y narrativa cerrada (D-SIM-2, D-SIM-10)")
    void finalizarDesdeExploracion() throws Exception {
        cadena(Modo.CON_FASES);
        error(post("finalizar", cookieOtro, AccesoEstudianteQa.ORIGIN), 403, "FORBIDDEN");
        var fin = post("finalizar", cookie, AccesoEstudianteQa.ORIGIN);
        assertEquals(200, fin.statusCode(), fin.body());
        var repetido = post("finalizar", cookie, AccesoEstudianteQa.ORIGIN);
        assertEquals(200, repetido.statusCode(), repetido.body());
        assertEquals(json.readTree(fin.body()).get("fin"), json.readTree(repetido.body()).get("fin"));
        assertEquals("ENTREGA_ESTUDIANTE", json.readTree(fin.body()).get("motivoFinalizacion").asText());
        for (String prohibido : List.of("nota", "calificacion", "valor", "aprobado")) {
            assertFalse(fin.body().toLowerCase().contains(prohibido), prohibido);
        }
        assertEquals(0, cantidad("SELECT count(*) FROM \"Calificacion\" WHERE \"idIntento\"=?", intento));
        JsonNode fase = ok(get("fase"));
        assertEquals("Finalizado", fase.get("estadoIntento").asText());
        assertEquals("EXPLORACION", fase.get("fase").asText());
        sinPreguntas(error(get("contenido"), 409, "CUESTIONARIO_NO_INICIADO"));
        sinPreguntas(error(get("completitud"), 409, "CUESTIONARIO_NO_INICIADO"));
        error(get("narrativa"), 409, "EXPLORACION_CERRADA");
        error(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN), 409, "INTENTO_FINALIZADO");
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?", String.class, intento));
        assertEquals(0, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intento));
    }

    @Test @DisplayName("D-SIM-3: P3 con el límite vencido responde 409 LIMITE_TEMPORAL_VENCIDO, sin escribir ni cerrar el intento")
    void limiteVencido() throws Exception {
        cadena(Modo.VENCIDO);
        error(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN), 409, "LIMITE_TEMPORAL_VENCIDO");
        error(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN), 409, "LIMITE_TEMPORAL_VENCIDO");
        JsonNode fase = ok(get("fase"));
        assertEquals("EXPLORACION", fase.get("fase").asText());
        assertEquals("EnCurso", fase.get("estadoIntento").asText(), "sin cierre automático por vencimiento");
        assertTrue(fase.get("fechaInicioCuestionario").isNull());
        sinPreguntas(error(get("contenido"), 409, "CUESTIONARIO_NO_INICIADO"));
    }

    @Test @DisplayName("DC-45: inicio real con fases (E1/L1) abre la exploración; tras el límite P3 sigue en 409 sin cerrar (D-SIM-3) y finalizar desde exploración registra TIEMPO_VENCIDO (D-SIM-4 + V1-c)")
    void inicioRealConFasesYVencimiento() throws Exception {
        cadena(Modo.CON_FASES);
        String s = UUID.randomUUID().toString().substring(0, 8);
        UUID usuarioC = estudianteConCuenta("c" + s);
        UUID sesionC = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio fases 45',now()-interval '1 hour',now()+interval '1 hour',3)""", sesionC, casoActual, docenteActual);
        jdbc.update("UPDATE \"Sesion\" SET \"estado\"='EnCurso', \"fechaInicioReal\"=clock_timestamp() WHERE \"idSesion\"=?", sesionC);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)",
                UUID.randomUUID(), sesionC, idEstudiante(usuarioC));
        cookie = AccesoEstudianteQa.login(http, port, jdbc, cifrador, "c" + s + "@fases.prisaly.test");

        var inicio = http.send(HttpRequest.newBuilder(uri("/v1/estudiantes/sesiones/" + sesionC + "/intentos"))
                .header("Cookie", cookie).header("Origin", AccesoEstudianteQa.ORIGIN)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, inicio.statusCode(), inicio.body());
        intento = UUID.fromString(json.readTree(inicio.body()).get("intento").get("idIntento").asText());
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?", String.class, intento),
                "the real start opens the exploration in the same transaction");
        assertEquals("EXPLORACION", ok(get("fase")).get("fase").asText());
        sinPreguntas(error(get("contenido"), 409, "CUESTIONARIO_NO_INICIADO")); // D-SIM-2

        long hasta = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT clock_timestamp() > \"limiteTemporal\" FROM \"Intento\" WHERE \"idIntento\"=?", Boolean.class, intento))) {
            assertTrue(System.nanoTime() < hasta, "PostgreSQL clock did not pass the limit");
            Thread.sleep(100);
        }
        error(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN), 409, "LIMITE_TEMPORAL_VENCIDO"); // D-SIM-3
        JsonNode fase = ok(get("fase"));
        assertEquals("EXPLORACION", fase.get("fase").asText());
        assertEquals("EnCurso", fase.get("estadoIntento").asText(), "D-SIM-3: P3 does not close the attempt");

        var fin = post("finalizar", cookie, AccesoEstudianteQa.ORIGIN); // D-SIM-4 from exploration, after the limit
        assertEquals(200, fin.statusCode(), fin.body());
        assertEquals("TIEMPO_VENCIDO", json.readTree(fin.body()).get("motivoFinalizacion").asText());
        var repetido = post("finalizar", cookie, AccesoEstudianteQa.ORIGIN);
        assertEquals(200, repetido.statusCode(), repetido.body());
        assertEquals(json.readTree(fin.body()).get("fin").asText(), json.readTree(repetido.body()).get("fin").asText());
        error(get("narrativa"), 409, "EXPLORACION_CERRADA"); // D-SIM-10
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?", String.class, intento));
        assertEquals(0, cantidad("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?", intento));
    }

    @Test @DisplayName("SIN_FASES: fase informada, /contenido y P4 como antes, P1 y P3 con su error tipado, respuestas sin cambios")
    void intentoSinFases() throws Exception {
        cadena(Modo.SIN_FASES);
        JsonNode fase = ok(get("fase"));
        assertEquals("SIN_FASES", fase.get("fase").asText());
        assertTrue(fase.get("fechaInicioExploracion").isNull());
        assertEquals(2, ok(get("contenido")).get("caso").get("escenarios").get(0).get("preguntas").size());
        assertEquals(2, ok(get("completitud")).get("preguntasPresentables").asInt());
        error(get("narrativa"), 409, "NARRATIVA_NO_DISPONIBLE");
        error(post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN), 409, "INTENTO_SIN_FASES");
        assertEquals(200, put(pregunta1, opcion1, 0, cookie).statusCode());
        assertEquals(0, cantidad("SELECT count(*) FROM \"FaseIntento\" WHERE \"idIntento\"=?", intento));
    }

    // --- Seguridad ------------------------------------------------------------------------------------------

    @Test @DisplayName("seguridad: sin sesión 401, otro estudiante 403, personal sin acceso, POST sin Origin autorizado 403, 404 y 400")
    void seguridad() throws Exception {
        cadena(Modo.CON_FASES);
        for (String ruta : List.of("narrativa", "fase", "completitud")) {
            assertEquals(401, send("GET", ruta, null, null, null).statusCode(), ruta);
            error(send("GET", ruta, cookieOtro, null, null), 403, "FORBIDDEN");
            int personal = send("GET", ruta, null, null, bearerDocente).statusCode();
            assertTrue(personal == 401 || personal == 403, ruta + " " + personal);
        }
        assertEquals(401, send("POST", "cuestionario", null, AccesoEstudianteQa.ORIGIN, null).statusCode());
        error(post("cuestionario", cookieOtro, AccesoEstudianteQa.ORIGIN), 403, "FORBIDDEN");
        assertEquals(403, post("cuestionario", cookie, null).statusCode(), "POST sin Origin");
        assertEquals(403, post("cuestionario", cookie, "https://atacante.example").statusCode(), "POST con Origin ajeno");
        int personal = send("POST", "cuestionario", null, AccesoEstudianteQa.ORIGIN, bearerDocente).statusCode();
        assertTrue(personal == 401 || personal == 403, String.valueOf(personal));
        assertEquals("Exploracion", jdbc.queryForObject("SELECT \"fase\" FROM \"FaseIntento\" WHERE \"idIntento\"=?", String.class, intento),
                "ningún POST rechazado escribió");
        // Cabecera de identidad manipulada: no cuenta.
        var manipulada = http.send(HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/" + intento + "/narrativa"))
                .header("Cookie", cookieOtro).header("X-Student-Id", intento.toString()).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, manipulada.statusCode());
        assertFalse(manipulada.body().contains("prisaly-guion-1"));
        assertEquals(404, http.send(HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/" + UUID.randomUUID() + "/fase"))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(400, http.send(HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/no-uuid/fase"))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test @DisplayName("P3 concurrente por HTTP: 8 peticiones simultáneas, una sola entrada y la misma fecha persistida")
    void cuestionarioConcurrente() throws Exception {
        cadena(Modo.CON_FASES);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> respuestas = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                respuestas.add(hilos.submit(() -> { salida.await(); return post("cuestionario", cookie, AccesoEstudianteQa.ORIGIN); }));
            }
            salida.countDown();
            List<JsonNode> cuerpos = new ArrayList<>();
            for (var r : respuestas) {
                var respuesta = r.get(60, TimeUnit.SECONDS);
                cuerpos.add(ok(respuesta));
            }
            assertEquals(1, cuerpos.stream().filter(b -> !b.get("yaIniciado").asBoolean()).count());
            assertEquals(1, cuerpos.stream().map(b -> b.get("fechaInicioCuestionario").asText()).distinct().count());
            assertEquals(1, cantidad("SELECT count(*) FROM \"FaseIntento\" WHERE \"idIntento\"=? AND \"fase\"='Cuestionario'", intento));
        } finally {
            hilos.shutdownNow();
        }
    }

    // --- Utilidades -----------------------------------------------------------------------------------------

    JsonNode ok(HttpResponse<String> r) throws Exception {
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("no-store", r.headers().firstValue("Cache-Control").orElseThrow().split(",")[0].trim(), r.headers().toString());
        return json.readTree(r.body());
    }

    String error(HttpResponse<String> r, int estado, String codigo) throws Exception {
        assertEquals(estado, r.statusCode(), r.body());
        JsonNode cuerpo = json.readTree(r.body());
        assertEquals(codigo, cuerpo.get("code").asText(), r.body());
        assertEquals(2, cuerpo.size(), r.body());
        assertTrue(r.headers().firstValue("Cache-Control").orElse("").contains("no-store"), r.headers().toString());
        return r.body();
    }

    void sinPreguntas(String cuerpo) {
        for (String prohibido : List.of(pregunta1.toString(), pregunta2.toString(), "ENUNCIADO-UNO", "ENUNCIADO-DOS", "Historia fases")) {
            assertFalse(cuerpo.contains(prohibido), prohibido);
        }
    }

    private UUID estudianteConCuenta(String alias) {
        String rol = "EstFases-" + alias;
        catalog.createRole(rol, "Controlled student fixture");
        UUID usuario = catalog.registerUser("Estudiante " + alias, alias + "@fases.prisaly.test", CLAVE, rol).getIdUsuario();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", UUID.randomUUID(), usuario);
        return usuario;
    }
    private UUID idEstudiante(UUID usuario) {
        return jdbc.queryForObject("SELECT \"idEstudiante\" FROM \"Estudiante\" WHERE \"idUsuario\"=?", UUID.class, usuario);
    }
    private int cantidad(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private String bearer(String correo) throws Exception {
        var r = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asText();
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    private HttpResponse<String> get(String ruta) throws Exception { return send("GET", ruta, cookie, null, null); }
    private HttpResponse<String> post(String ruta, String cookie, String origin) throws Exception {
        return send("POST", ruta, cookie, origin, null);
    }
    private HttpResponse<String> send(String metodo, String ruta, String cookie, String origin, String token) throws Exception {
        var b = HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/" + intento + "/" + ruta))
                .method(metodo, HttpRequest.BodyPublishers.noBody());
        if (cookie != null) b.header("Cookie", cookie);
        if (origin != null) b.header("Origin", origin);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> put(UUID pregunta, UUID opcion, int revision, String cookie) throws Exception {
        var b = HttpRequest.newBuilder(uri("/v1/estudiantes/intentos/" + intento + "/respuestas/" + pregunta))
                .header("Origin", AccesoEstudianteQa.ORIGIN).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"idOpcionSeleccionada\":\"" + opcion + "\",\"revisionEsperada\":" + revision + "}"));
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
