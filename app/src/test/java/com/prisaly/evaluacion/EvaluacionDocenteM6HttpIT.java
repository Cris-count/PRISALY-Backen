package com.prisaly.evaluacion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-M6-DESBLOQUEO-IMPLEMENTACION-55 over the <b>real APP</b> and PostgreSQL 16.15 (V001–V009 official chain):
 * CONTRATOS-HTTP-M6-55 with ACTA-M6-POLITICAS-55. The switch {@code prisaly.m6.evaluacion.enabled} is turned on
 * ONLY in this test context; {@code application.yml} ships it off. Real personnel Bearer authentication, real M1,
 * M3 (authorship and protected reader), M4 (responsible port), M5 (B1 reader) and M6 (proxy, preparation, S1,
 * insert-only writer). All data is fictitious ({@code @prisaly.test}).
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class EvaluacionDocenteM6HttpIT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("prisaly.m6.evaluacion.enabled", () -> "true"); // QA only
    }

    static final String CLAVE = "Fixture#M6eval55";

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalogo;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.prisaly.m3.api.ContenidoCasoEstudiantilPort contenidoEstudiantil;
    @Autowired org.springframework.transaction.PlatformTransactionManager transacciones;
    final HttpClient http = HttpClient.newHttpClient();

    // ─── Fixture: case with p1 (weight 6.00) and p2 (weight 4.00), each with one correct option ─────────────────

    record Caso(UUID id, UUID docente, String token, UUID rubrica, UUID sesion, UUID p1, UUID p1ok, UUID p1mal,
            UUID p2, UUID p2ok) { }

    /** DC-57 adds PREGUNTA_BORRADOR (p2 visible, active scenario, weighted, Borrador) and OCULTA_SIN_PESO
     * (p2 hidden and not associated with the rubric). */
    enum Variante { NORMAL, PREGUNTA_OCULTA, ESCENARIO_INACTIVO, RUBRICA_INCOMPLETA, PREGUNTA_BORRADOR, OCULTA_SIN_PESO }

    private Caso caso(Variante variante) throws Exception {
        return caso(variante, null);
    }

    /** {@code programador}: teacher who programs the session (null = the creator). */
    private Caso caso(Variante variante, UUID programador) throws Exception {
        String s = sufijo();
        var docente = docente("creador");
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), rubrica = UUID.randomUUID(),
                sesion = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID(),
                p1ok = UUID.randomUUID(), p1mal = UUID.randomUUID(), p2ok = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\")"
                + " VALUES (?,?,?,600,'Publicado')", caso, docente.id(), "Caso M6-55 " + s);
        jdbc.update("INSERT INTO \"Escenario\" (\"idEscenario\",\"idCaso\",\"orden\",\"activo\") VALUES (?,?,1,?)",
                escenario, caso, variante != Variante.ESCENARIO_INACTIVO);
        boolean p2Visible = variante != Variante.PREGUNTA_OCULTA && variante != Variante.OCULTA_SIN_PESO;
        String p2Estado = variante == Variante.PREGUNTA_BORRADOR ? "Borrador" : "Lista";
        jdbc.update("INSERT INTO \"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"estadoEdicion\",\"orden\","
                + "\"visible\") VALUES (?,?,'P1','Lista',1,true),(?,?,'P2',?,2,?)",
                p1, escenario, p2, escenario, p2Estado, p2Visible);
        jdbc.update("INSERT INTO \"OpcionRespuesta\" (\"idOpcion\",\"idPregunta\",\"texto\",\"orden\",\"esCorrecta\")"
                + " VALUES (?,?,'1A',1,true),(?,?,'1B',2,false),(?,?,'2A',1,true)", p1ok, p1, p1mal, p1, p2ok, p2);
        jdbc.update("INSERT INTO \"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\",\"definicionEvaluacion\")"
                + " VALUES (?,?,?,'{\"fixture\":\"M6-55\"}')", rubrica, caso, "Rubrica M6-55 " + s);
        jdbc.update("INSERT INTO \"RubricaPregunta\" (\"idRubricaPregunta\",\"idRubrica\",\"idPregunta\",\"valorPregunta\")"
                + " VALUES (gen_random_uuid(),?,?,6.00)", rubrica, p1);
        if (variante != Variante.RUBRICA_INCOMPLETA && variante != Variante.OCULTA_SIN_PESO) {
            jdbc.update("INSERT INTO \"RubricaPregunta\" (\"idRubricaPregunta\",\"idRubrica\",\"idPregunta\","
                    + "\"valorPregunta\") VALUES (gen_random_uuid(),?,?,4.00)", rubrica, p2);
        }
        // Programming the session locks the academic content (V007): everything above is inserted first.
        jdbc.update("INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\","
                + "\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\") VALUES (?,?,?,'Espacio M6-55',"
                + "now()-interval '3 hours',now()+interval '3 hours',3600)",
                sesion, caso, programador == null ? docente.id() : programador);
        return new Caso(caso, docente.id(), docente.token(), rubrica, sesion, p1, p1ok, p1mal, p2, p2ok);
    }

    record Docente(UUID id, String correo, String token) { }

    private Docente docente(String alias) throws Exception {
        String s = sufijo();
        catalogo.createRole("M6-55-" + alias + "-" + s, "Fixture M6-55");
        String correo = "m6-55." + alias + "." + s + "@prisaly.test";
        UUID id = catalogo.registerUser("Docente M6-55 " + alias, correo, CLAVE, "M6-55-" + alias + "-" + s).getIdUsuario();
        return new Docente(id, correo, login(correo));
    }

    private String login(String correo) throws Exception {
        var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/personal/autenticar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asString();
    }

    /** Finalized (or open) attempt of a new student with the given answers {question, option}. */
    private UUID intento(Caso c, String estado, String motivo, UUID[]... respuestas) {
        String s = sufijo();
        int rol = Math.abs(UUID.randomUUID().hashCode() % 900_000_000) + 100_000;
        UUID usuario = UUID.randomUUID(), estudiante = UUID.randomUUID(), convocatoria = UUID.randomUUID(),
                participacion = UUID.randomUUID(), intento = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (?,?, 'Fixture M6-55')", rol, "M6-55-est-" + s);
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,'Estudiante M6-55',?)",
                usuario, rol, "m6-55.est." + s + "@prisaly.test");
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)",
                convocatoria, c.sesion(), estudiante);
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participacion, convocatoria);
        boolean porTiempo = "TIEMPO_VENCIDO".equals(motivo);
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\","
                + "\"estado\") VALUES (?,?,1," + (porTiempo ? "now()-interval '2 hours',now()-interval '1 hour'"
                : "now(),now()+interval '1 hour'") + ",'EnCurso')", intento, participacion);
        for (UUID[] r : respuestas) {
            jdbc.update("INSERT INTO \"Respuesta\" (\"idRespuesta\",\"idIntento\",\"idPregunta\",\"idOpcionSeleccionada\")"
                    + " VALUES (gen_random_uuid(),?,?,?)", intento, r[0], r[1]);
        }
        if ("Finalizado".equals(estado)) {
            jdbc.update("UPDATE \"Intento\" SET \"estado\"='Finalizado', \"fin\"=clock_timestamp(), \"motivoFinalizacion\"=?"
                    + " WHERE \"idIntento\"=?", motivo, intento);
        }
        return intento;
    }

    private UUID finalizado(Caso c, UUID[]... respuestas) {
        return intento(c, "Finalizado", "ENTREGA_ESTUDIANTE", respuestas);
    }

    private static UUID[] r(UUID pregunta, UUID opcion) {
        return new UUID[] {pregunta, opcion};
    }

    // ─── HTTP helpers ───────────────────────────────────────────────────────────────────────────────────────────

    private HttpResponse<String> evaluar(String token, Object intento, Object rubrica) throws Exception {
        String cuerpo = rubrica == null ? "{}" : "{\"idRubrica\":\"" + rubrica + "\"}";
        return enviar(token, "POST", "/api/v1/evaluacion/intentos/" + intento, cuerpo);
    }

    private HttpResponse<String> consultar(String token, Object intento) throws Exception {
        return enviar(token, "GET", "/api/v1/evaluacion/intentos/" + intento, null);
    }

    private HttpResponse<String> enviar(String token, String metodo, String ruta, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).header("Accept", "application/json");
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        if (cuerpo != null) { b.header("Content-Type", "application/json"); }
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode cuerpo(HttpResponse<String> r) {
        return json.readTree(r.body());
    }

    private void error(HttpResponse<String> r, int estado, String codigo, String motivo) {
        assertEquals(estado, r.statusCode(), r.body());
        assertEquals(codigo, cuerpo(r).get("codigo").asString(), r.body());
        if (motivo != null) { assertEquals(motivo, cuerpo(r).get("motivo").asString(), r.body()); }
    }

    /** Raw JSON text of {@code valor} (a JSON number), as Frontend receives it; asserts 201 first. */
    private static String valor(HttpResponse<String> r) {
        assertEquals(201, r.statusCode(), r.body());
        var m = java.util.regex.Pattern.compile("\"valor\":(-?[0-9.]+)").matcher(r.body());
        assertTrue(m.find(), r.body());
        return m.group(1);
    }

    private int calificaciones(UUID intento) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\" WHERE \"idIntento\"=?", Integer.class, intento);
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ─── S1 calculation, rounding, unanswered, single immutable grade, teacher query ────────────────────────────

    @Test
    @DisplayName("S1: el propietario evalúa (6/10 → 3.00, no contestada = 0), una sola nota inmutable y consulta docente")
    void propietarioEvaluaYConsulta() throws Exception {
        Caso c = caso(Variante.NORMAL);
        UUID intento = finalizado(c, r(c.p1(), c.p1ok())); // p2 unanswered
        assertEquals(404, consultar(c.token(), intento).statusCode(), "not evaluated yet");
        error(consultar(c.token(), intento), 404, "CALIFICACION_NO_ENCONTRADA", null);

        var creada = evaluar(c.token(), intento, c.rubrica());
        assertEquals(201, creada.statusCode(), creada.body());
        assertEquals("/api/v1/evaluacion/intentos/" + intento, creada.headers().firstValue("Location").orElseThrow());
        JsonNode b = cuerpo(creada);
        assertEquals("3.00", valor(creada), "two exact decimals on the wire");
        assertEquals("0-5", b.get("escala").asString());
        assertEquals("formativa", b.get("caracter").asString());
        assertEquals("ponderada-s1-v1", b.get("versionPolitica").asString());
        assertFalse(b.get("liberada").asBoolean());
        assertEquals(intento.toString(), b.get("idIntento").asString());
        assertEquals(c.rubrica().toString(), b.get("idRubrica").asString());
        assertTrue(b.get("detalle").get("ratificada").asBoolean());
        assertEquals(2, b.get("detalle").get("preguntas").size());
        // DC-57: the persisted decimal scale reaches the client (it arrived as 10.0 / 6.0 before).
        assertTrue(creada.body().contains("\"pesoTotal\":10.00"), creada.body());
        assertTrue(creada.body().contains("\"peso\":6.00"), creada.body());

        var fila = jdbc.queryForMap("SELECT \"valor\",\"versionPolitica\",\"idRubrica\" FROM \"Calificacion\" WHERE \"idIntento\"=?", intento);
        assertEquals(new BigDecimal("3.00"), fila.get("valor"));
        assertEquals("ponderada-s1-v1", fila.get("versionPolitica"));

        var consulta = consultar(c.token(), intento);
        assertEquals(200, consulta.statusCode(), consulta.body());
        assertEquals(b, cuerpo(consulta), "the teacher query returns the recorded grade");

        // One immutable grade: a second evaluation is 409 and V003 forbids UPDATE.
        error(evaluar(c.token(), intento, c.rubrica()), 409, "EVALUACION_EXISTENTE", null);
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE \"Calificacion\" SET \"valor\"=5.00 WHERE \"idIntento\"=?", intento));
        assertEquals(1, calificaciones(intento));
        // Formative and no N2: Participacion is untouched.
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\" p JOIN \"Intento\" i ON"
                + " i.\"idParticipacion\"=p.\"idParticipacion\" WHERE i.\"idIntento\"=? AND (p.\"calificacionFinal\" IS NOT NULL"
                + " OR p.\"detalleResultadoFinal\" IS NOT NULL)", Integer.class, intento));
    }

    @Test
    @DisplayName("S1: ponderación y HALF_UP sobre datos persistidos; todo correcto 5.00; nada contestado 0.00")
    void calculoYRedondeo() throws Exception {
        Caso c = caso(Variante.NORMAL);
        UUID soloP2 = finalizado(c, r(c.p1(), c.p1mal()), r(c.p2(), c.p2ok())); // 5 × 4/10 = 2.00
        UUID todo = finalizado(c, r(c.p1(), c.p1ok()), r(c.p2(), c.p2ok()));
        UUID nada = finalizado(c);
        assertEquals("2.00", valor(evaluar(c.token(), soloP2, c.rubrica())));
        assertEquals("5.00", valor(evaluar(c.token(), todo, c.rubrica())));
        assertEquals("0.00", valor(evaluar(c.token(), nada, c.rubrica())));
    }

    @Test
    @DisplayName("Intento cerrado por tiempo (TIEMPO_VENCIDO) es evaluable; intento abierto → 409 sin escribir")
    void abiertoYCerradoPorTiempo() throws Exception {
        Caso c = caso(Variante.NORMAL);
        UUID abierto = intento(c, "EnCurso", null, r(c.p1(), c.p1ok()));
        error(evaluar(c.token(), abierto, c.rubrica()), 409, "INTENTO_NO_FINALIZADO", null);
        assertEquals(0, calificaciones(abierto));
        // Open attempt whose limit has passed: still not finalized until M5 closes it.
        UUID vencidoAbierto = intento(c, "EnCurso", "TIEMPO_VENCIDO", r(c.p1(), c.p1ok()));
        error(evaluar(c.token(), vencidoAbierto, c.rubrica()), 409, "INTENTO_NO_FINALIZADO", null);
        UUID porTiempo = intento(c, "Finalizado", "TIEMPO_VENCIDO", r(c.p2(), c.p2ok()));
        var r = evaluar(c.token(), porTiempo, c.rubrica());
        assertEquals(201, r.statusCode(), r.body());
        assertEquals("2.00", valor(r));
    }

    // ─── C1 and invalid rubric ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C1: pregunta no visible o escenario inactivo → 422 PREGUNTAS_NO_ACCESIBLES; rúbrica incompleta o ajena → 422")
    void rechazosSinCalificacion() throws Exception {
        Caso oculta = caso(Variante.PREGUNTA_OCULTA);
        UUID i1 = finalizado(oculta, r(oculta.p1(), oculta.p1ok()));
        error(evaluar(oculta.token(), i1, oculta.rubrica()), 422, "EVALUACION_RECHAZADA", "PREGUNTAS_NO_ACCESIBLES");

        Caso inactivo = caso(Variante.ESCENARIO_INACTIVO);
        UUID i2 = finalizado(inactivo, r(inactivo.p1(), inactivo.p1ok()));
        error(evaluar(inactivo.token(), i2, inactivo.rubrica()), 422, "EVALUACION_RECHAZADA", "PREGUNTAS_NO_ACCESIBLES");

        Caso incompleta = caso(Variante.RUBRICA_INCOMPLETA);
        UUID i3 = finalizado(incompleta, r(incompleta.p1(), incompleta.p1ok()));
        error(evaluar(incompleta.token(), i3, incompleta.rubrica()), 422, "EVALUACION_RECHAZADA", "PESOS_INCOMPLETOS");

        Caso normal = caso(Variante.NORMAL);
        UUID i4 = finalizado(normal, r(normal.p1(), normal.p1ok()));
        error(evaluar(normal.token(), i4, incompleta.rubrica()), 422, "EVALUACION_RECHAZADA", "RUBRICA_NO_CORRESPONDE");
        error(evaluar(normal.token(), i4, UUID.randomUUID()), 422, "EVALUACION_RECHAZADA", "RUBRICA_NO_CORRESPONDE");

        for (UUID i : List.of(i1, i2, i3, i4)) { assertEquals(0, calificaciones(i)); }
        assertEquals("Pendiente", jdbc.queryForObject("SELECT \"estadoEvaluacion\" FROM \"Intento\" WHERE \"idIntento\"=?",
                String.class, i1));
    }

    // ─── DC-BE-M6-CORRECCION-QA-57: estadoEdicion and the evaluable set ─────────────────────────────────────────

    /** What M3 actually delivers to a student for this case (real adapter, stable read transaction). */
    private boolean m3EntregaElCaso(Caso c) {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transacciones);
        tx.setReadOnly(true);
        return Boolean.TRUE.equals(tx.execute(st -> contenidoEstudiantil.consultar(c.id()).isPresent()));
    }

    @Test
    @DisplayName("QA-57: pregunta en Borrador (visible, escenario activo, con peso y respondida) → 422, sin nota")
    void preguntaEnBorradorSeRechaza() throws Exception {
        Caso c = caso(Variante.PREGUNTA_BORRADOR);
        assertFalse(m3EntregaElCaso(c), "M3 never delivered this case to a student");
        UUID intento = finalizado(c, r(c.p1(), c.p1ok()), r(c.p2(), c.p2ok()));
        error(evaluar(c.token(), intento, c.rubrica()), 422, "EVALUACION_RECHAZADA", "PREGUNTAS_NO_ACCESIBLES");
        assertEquals(0, calificaciones(intento));
        error(consultar(c.token(), intento), 404, "CALIFICACION_NO_ENCONTRADA", null);
    }

    @Test
    @DisplayName("QA-57: caso publicado y accesible → M3 lo entrega y la evaluación procede (201)")
    void preguntaAccesibleSeEvalua() throws Exception {
        Caso c = caso(Variante.NORMAL);
        assertTrue(m3EntregaElCaso(c));
        UUID intento = finalizado(c, r(c.p1(), c.p1ok()), r(c.p2(), c.p2ok()));
        assertEquals("5.00", valor(evaluar(c.token(), intento, c.rubrica())));
    }

    @Test
    @DisplayName("QA-57: oculta dentro del conjunto evaluable (con peso) y oculta sin peso → 422; M3 no entregó el caso")
    void ocultaDentroYFueraDeLaRubrica() throws Exception {
        Caso dentro = caso(Variante.PREGUNTA_OCULTA);
        assertFalse(m3EntregaElCaso(dentro));
        UUID i1 = finalizado(dentro, r(dentro.p1(), dentro.p1ok()));
        error(evaluar(dentro.token(), i1, dentro.rubrica()), 422, "EVALUACION_RECHAZADA", "PREGUNTAS_NO_ACCESIBLES");
        // Hidden and NOT in the rubric: M3 delivers a case all-or-nothing, so the student received no question at
        // all (the attempt's evaluable set is empty) and p1, which IS in the rubric, was inaccessible too.
        Caso fuera = caso(Variante.OCULTA_SIN_PESO);
        assertFalse(m3EntregaElCaso(fuera), "the contract cannot deliver p1 without p2: not distinguishable");
        UUID i2 = finalizado(fuera, r(fuera.p1(), fuera.p1ok()));
        error(evaluar(fuera.token(), i2, fuera.rubrica()), 422, "EVALUACION_RECHAZADA", "PREGUNTAS_NO_ACCESIBLES");
        assertEquals(0, calificaciones(i1));
        assertEquals(0, calificaciones(i2));
    }

    @Test
    @DisplayName("QA-57 contrato: accesibleAlEstudiante() de M6 coincide con la entrega real de M3 en las 8 combinaciones")
    void contratoAccesibilidadM3M6() throws Exception {
        UUID docente = docente("contrato").id();
        for (boolean activo : new boolean[] {true, false}) {
            for (boolean visible : new boolean[] {true, false}) {
                for (String estado : List.of("Lista", "Borrador")) {
                    UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), pregunta = UUID.randomUUID();
                    jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\","
                            + "\"estado\") VALUES (?,?,?,600,'Publicado')", caso, docente, "Contrato QA-57 " + sufijo());
                    jdbc.update("INSERT INTO \"Escenario\" (\"idEscenario\",\"idCaso\",\"orden\",\"activo\") VALUES (?,?,1,?)",
                            escenario, caso, activo);
                    jdbc.update("INSERT INTO \"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"estadoEdicion\","
                            + "\"orden\",\"visible\") VALUES (?,?,'P',?,1,?)", pregunta, escenario, estado, visible);
                    var fila = jdbc.queryForMap("SELECT e.\"activo\", p.\"visible\", p.\"estadoEdicion\" FROM \"Pregunta\" p"
                            + " JOIN \"Escenario\" e ON e.\"idEscenario\"=p.\"idEscenario\" WHERE p.\"idPregunta\"=?", pregunta);
                    var m6 = new com.prisaly.m6.domain.strategy.EntradaEvaluacion.PreguntaEvaluable(pregunta, escenario,
                            (Boolean) fila.get("activo"), (Boolean) fila.get("visible"), (String) fila.get("estadoEdicion"),
                            java.util.Optional.empty(), List.of(), java.util.Optional.empty());
                    var tx = new org.springframework.transaction.support.TransactionTemplate(transacciones);
                    tx.setReadOnly(true);
                    boolean entregado = Boolean.TRUE.equals(tx.execute(st -> contenidoEstudiantil.consultar(caso).isPresent()));
                    assertEquals(entregado, m6.accesibleAlEstudiante(), activo + "/" + visible + "/" + estado);
                }
            }
        }
    }

    // ─── Authorization: owner, foreign, programmer-not-creator, inactive ────────────────────────────────────────

    @Test
    @DisplayName("Docente ajeno, programador no creador e intento inexistente → 403 sin escribir y sin revelar datos")
    void docenteAjenoYProgramador() throws Exception {
        Docente ajeno = docente("ajeno");
        Caso c = caso(Variante.NORMAL);
        UUID intento = finalizado(c, r(c.p1(), c.p1ok()));
        error(evaluar(ajeno.token(), intento, c.rubrica()), 403, "ACCESO_DENEGADO", null);
        error(consultar(ajeno.token(), intento), 403, "ACCESO_DENEGADO", null);

        Docente programador = docente("programador");
        Caso programado = caso(Variante.NORMAL, programador.id());
        UUID otro = finalizado(programado, r(programado.p1(), programado.p1ok()));
        error(evaluar(programador.token(), otro, programado.rubrica()), 403, "ACCESO_DENEGADO", null);
        assertEquals(0, calificaciones(intento));
        assertEquals(0, calificaciones(otro));

        // Nonexistent attempt: same 403, existence is not revealed.
        error(evaluar(c.token(), UUID.randomUUID(), c.rubrica()), 403, "ACCESO_DENEGADO", null);
        error(consultar(c.token(), UUID.randomUUID()), 403, "ACCESO_DENEGADO", null);

        // The foreign teacher's later attempt did not prevent the owner from evaluating.
        assertEquals(201, evaluar(c.token(), intento, c.rubrica()).statusCode());
        error(consultar(ajeno.token(), intento), 403, "ACCESO_DENEGADO", null);
    }

    @Test
    @DisplayName("Propietario con cuenta inactivada tras autenticarse → 401 sin escribir")
    void propietarioInactivo() throws Exception {
        Caso c = caso(Variante.NORMAL);
        UUID intento = finalizado(c, r(c.p1(), c.p1ok()));
        jdbc.update("UPDATE \"Usuario\" SET \"estado\"='Inactivo' WHERE \"idUsuario\"=?", c.docente());
        var r = evaluar(c.token(), intento, c.rubrica());
        // M1's Bearer filter reloads the account on every request: inactive → unauthenticated (401) before M6.
        // PropietarioActivoCasoM6 re-checks the live status as a second line (unit test).
        assertEquals(401, r.statusCode(), r.body());
        assertEquals(0, calificaciones(intento));
        var q = consultar(c.token(), intento);
        assertEquals(401, q.statusCode(), q.body());
    }

    @Test
    @DisplayName("Sin autenticación 401; UUID inválido o sin idRubrica 400")
    void seguridadYValidacion() throws Exception {
        Caso c = caso(Variante.NORMAL);
        UUID intento = finalizado(c, r(c.p1(), c.p1ok()));
        assertEquals(401, evaluar(null, intento, c.rubrica()).statusCode());
        assertEquals(401, consultar(null, intento).statusCode());
        error(evaluar(c.token(), "no-es-uuid", c.rubrica()), 400, "SOLICITUD_INVALIDA", null);
        error(evaluar(c.token(), intento, null), 400, "SOLICITUD_INVALIDA", null);
        error(enviar(c.token(), "POST", "/api/v1/evaluacion/intentos/" + intento, "{no json"), 400, "SOLICITUD_INVALIDA", null);
        assertEquals(0, calificaciones(intento));
    }

    // ─── Concurrency ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("6 evaluaciones simultáneas del mismo intento → una 201, el resto 409; una sola calificación")
    void evaluacionConcurrente(CapturedOutput salida) throws Exception {
        Caso c = caso(Variante.NORMAL);
        UUID intento = finalizado(c, r(c.p1(), c.p1ok()), r(c.p2(), c.p2ok()));
        var pool = Executors.newFixedThreadPool(6);
        var salidaLatch = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> tareas = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                tareas.add(pool.submit(() -> { salidaLatch.await(); return evaluar(c.token(), intento, c.rubrica()); }));
            }
            salidaLatch.countDown();
            int creadas = 0;
            for (var t : tareas) {
                var r = t.get(60, TimeUnit.SECONDS);
                if (r.statusCode() == 201) {
                    creadas++;
                } else {
                    error(r, 409, "EVALUACION_EXISTENTE", null);
                }
            }
            assertEquals(1, creadas);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, calificaciones(intento));
        assertEquals(new BigDecimal("5.00"), jdbc.queryForObject(
                "SELECT \"valor\" FROM \"Calificacion\" WHERE \"idIntento\"=?", BigDecimal.class, intento));
        assertFalse(salida.getAll().contains(c.token()), "the Bearer token never reaches the logs");
    }
}
