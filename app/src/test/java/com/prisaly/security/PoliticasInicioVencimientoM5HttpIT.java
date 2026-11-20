package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.infrastructure.persistence.PermisoRepository;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-M5-CIERRE-IMPLEMENTACION-45 — políticas ratificadas E1, L1, RF-22, V1-c y DD-14-a sobre la <b>APP real con el
 * ensamblado productivo</b> (sin sustituir políticas) y PostgreSQL 16.15. DD-02 B (Mailpit) y la publicación de los
 * POST se activan SOLO en este contexto QA; en {@code application.yml} siguen desactivados. Los intentos de este
 * recorrido son {@code SIN_FASES} (fases desactivadas): las fases se cubren en {@link FasesIntentoEstudianteHttpIT}.
 * Todos los datos son ficticios ({@code @prisaly.test}). El reloj de referencia es siempre el de PostgreSQL.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PoliticasInicioVencimientoM5HttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) { QA.propiedades(p, true); }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired PermisoRepository permisos;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    // ─── E1, L1 y RF-22 ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1+9: inicio autorizado; inicio y límite persistidos son los de PostgreSQL y L1 = inicio + máximo")
    void inicioAutorizadoConL1CalculadoConPostgreSql() throws Exception {
        Alumno a = alumno("e1ok");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, true, 1800, "now()+interval '2 hours'");
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        String cookie = login(a);

        OffsetDateTime antes = relojPg();
        var inicio = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookie);
        OffsetDateTime despues = relojPg();
        assertEquals(201, inicio.statusCode(), inicio.body());
        JsonNode cuerpo = json.readTree(inicio.body()).get("intento");
        String intento = cuerpo.get("idIntento").asText();
        Map<String, Object> fila = fila(intento);
        Instant ini = instante(fila.get("inicio")), lim = instante(fila.get("limiteTemporal"));
        // The persisted start is a PostgreSQL instant taken inside the request, never a client/JVM value.
        assertFalse(ini.isBefore(antes.toInstant()) || ini.isAfter(despues.toInstant()), ini + " " + antes + " " + despues);
        assertEquals(ini, Instant.parse(cuerpo.get("inicio").asText()));
        assertEquals(lim, Instant.parse(cuerpo.get("limiteTemporal").asText()));
        // L1 recomputed by PostgreSQL itself from the persisted rows: min(inicio + maximo, finPrevisto).
        assertEquals(Boolean.TRUE, jdbc.queryForObject("""
                SELECT i."limiteTemporal" = LEAST(i."inicio" + make_interval(secs => s."tiempoMaximoParticipacionSegundos"),
                                                  s."finPrevisto")
                FROM "Intento" i JOIN "Participacion" p ON p."idParticipacion"=i."idParticipacion"
                JOIN "Convocatoria" cv ON cv."idConvocatoria"=p."idConvocatoria" JOIN "Sesion" s ON s."idSesion"=cv."idSesion"
                WHERE i."idIntento"=?::uuid""", Boolean.class, intento));
        assertEquals(1800, java.time.Duration.between(ini, lim).getSeconds());
        assertEquals("EnCurso", fila.get("estado"));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT to_regclass('public.\"FaseIntento\"') IS NULL", Boolean.class),
                "SIN_FASES: this context has no phase table (V018 not applied) and the attempt is created without phases");
        assertEquals(1, intentos(conv));
    }

    @Test
    @DisplayName("2+4: sesión sin iniciar, caso no publicado o convocatoria retirada: 403 INICIO_NO_AUTORIZADO sin escribir")
    void inicioNoAutorizadoYCasoNoPublicado() throws Exception {
        Alumno a = alumno("e1no");
        String cookie = login(a);
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sinIniciar = sesion(c, false, 1800, "now()+interval '2 hours'");
        UUID convSinIniciar = RecorridoParticipacionQa.convocar(jdbc, sinIniciar, a.estudiante);
        noAutorizado(cookie, sinIniciar, convSinIniciar);

        UUID[] archivado = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesionArchivado = sesion(archivado, true, 1800, "now()+interval '2 hours'");
        UUID convArchivado = RecorridoParticipacionQa.convocar(jdbc, sesionArchivado, a.estudiante);
        jdbc.update("UPDATE \"Caso\" SET \"estado\"='Archivado' WHERE \"idCaso\"=?", archivado[0]);
        noAutorizado(cookie, sesionArchivado, convArchivado);

        UUID[] inactivo = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesionInactivo = sesion(inactivo, true, 1800, "now()+interval '2 hours'");
        UUID convInactivo = RecorridoParticipacionQa.convocar(jdbc, sesionInactivo, a.estudiante);
        jdbc.update("UPDATE \"Caso\" SET \"activo\"=false WHERE \"idCaso\"=?", inactivo[0]);
        noAutorizado(cookie, sesionInactivo, convInactivo);
    }

    @Test
    @DisplayName("3+10 (RF-22): sesión EnCurso pasado su fin previsto → 403 sin escribir; justo antes, límite = finPrevisto")
    void sesionFueraDePlazoYFronteraDelFinPrevisto() throws Exception {
        Alumno a = alumno("rf22"), b = alumno("rf22b");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID vencida = sesion(c, true, 1800, "clock_timestamp()-interval '1 second'", "clock_timestamp()-interval '1 hour'");
        UUID convVencida = RecorridoParticipacionQa.convocar(jdbc, vencida, a.estudiante);
        String cookieA = login(a);
        noAutorizado(cookieA, vencida, convVencida);

        // Planned end 4 s ahead and a 1-hour maximum: the end bounds the limit exactly (L1 boundary).
        UUID[] c2 = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID porTerminar = sesion(c2, true, 3600, "clock_timestamp()+interval '4 seconds'");
        UUID convB = RecorridoParticipacionQa.convocar(jdbc, porTerminar, b.estudiante);
        UUID convTarde = RecorridoParticipacionQa.convocar(jdbc, porTerminar, a.estudiante);
        String cookieB = login(b);
        var inicio = post("/v1/estudiantes/sesiones/" + porTerminar + "/intentos", cookieB);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asText();
        Instant fin = instante(jdbc.queryForObject("SELECT \"finPrevisto\" FROM \"Sesion\" WHERE \"idSesion\"=?",
                OffsetDateTime.class, porTerminar));
        assertEquals(fin, instante(fila(intento).get("limiteTemporal")), "limite = finPrevisto exactamente");
        assertEquals(1, intentos(convB));

        // Once PostgreSQL passes finPrevisto, a late student is rejected (no tolerance) and nothing is written.
        esperarPg("SELECT clock_timestamp() > \"finPrevisto\" FROM \"Sesion\" WHERE \"idSesion\"=?", porTerminar);
        noAutorizado(cookieA, porTerminar, convTarde);
    }

    @Test
    @DisplayName("5+18: sesión sin convocatoria propia o inexistente → 404 sin datos ajenos; sin sesión → 401")
    void convocatoriaInexistenteOAjenaYSeguridad() throws Exception {
        Alumno a = alumno("ajena"), b = alumno("duena");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, true, 1800, "now()+interval '2 hours'");
        UUID convB = RecorridoParticipacionQa.convocar(jdbc, sesion, b.estudiante);
        String cookieA = login(a);

        var ajena = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookieA);
        assertEquals(404, ajena.statusCode(), ajena.body());
        assertTrue(ajena.body().contains("CONVOCATORIA_NO_ENCONTRADA"), ajena.body());
        assertFalse(ajena.body().contains(convB.toString()) || ajena.body().contains(b.estudiante.toString()));
        var inexistente = post("/v1/estudiantes/sesiones/" + UUID.randomUUID() + "/intentos", cookieA);
        assertEquals(404, inexistente.statusCode(), inexistente.body());
        assertEquals(0, intentos(convB));

        String cookieB = login(b);
        var propio = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookieB);
        assertEquals(201, propio.statusCode(), propio.body());
        String intento = json.readTree(propio.body()).get("intento").get("idIntento").asText();
        // The other student cannot finalize, read or answer it; without a session nothing answers.
        assertEquals(403, post("/v1/estudiantes/intentos/" + intento + "/finalizar", cookieA).statusCode());
        assertEquals(403, RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento, null, cookieA).statusCode());
        assertEquals(401, post("/v1/estudiantes/sesiones/" + sesion + "/intentos", null).statusCode());
        assertEquals(401, post("/v1/estudiantes/intentos/" + intento + "/finalizar", null).statusCode());
        assertEquals("EnCurso", fila(intento).get("estado"));
    }

    @Test
    @DisplayName("6: la reanudación devuelve el mismo intento y no amplía el límite aunque la sesión cambie")
    void reanudacionSinAmpliarLimite() throws Exception {
        Alumno a = alumno("reanuda");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, true, 600, "now()+interval '2 hours'");
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        String cookie = login(a);
        var inicio = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookie);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asText();
        Object limiteOriginal = fila(intento).get("limiteTemporal");

        // The session is given more time afterwards: a resume must never recompute or extend the persisted limit.
        jdbc.update("UPDATE \"Sesion\" SET \"tiempoMaximoParticipacionSegundos\"=7200, \"finPrevisto\"=now()+interval '5 hours'"
                + " WHERE \"idSesion\"=?", sesion);
        var reanudacion = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookie);
        assertEquals(200, reanudacion.statusCode(), reanudacion.body());
        JsonNode r = json.readTree(reanudacion.body());
        assertTrue(r.get("reanudado").asBoolean());
        assertEquals(intento, r.get("intento").get("idIntento").asText());
        assertEquals(instante(limiteOriginal), Instant.parse(r.get("intento").get("limiteTemporal").asText()));
        assertEquals(limiteOriginal, fila(intento).get("limiteTemporal"));
        assertEquals(1, intentos(conv));
    }

    @Test
    @DisplayName("7 (DD-14-a): tras finalizar no hay otro intento, ni en la misma convocatoria ni en otra sesión del mismo caso")
    void duplicidadDelIntento() throws Exception {
        Alumno a = alumno("dd14");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID primera = sesion(c, true, 1800, "now()+interval '2 hours'");
        UUID segunda = sesion(c, true, 1800, "now()+interval '2 hours'");
        UUID conv1 = RecorridoParticipacionQa.convocar(jdbc, primera, a.estudiante);
        UUID conv2 = RecorridoParticipacionQa.convocar(jdbc, segunda, a.estudiante);
        String cookie = login(a);
        var inicio = post("/v1/estudiantes/sesiones/" + primera + "/intentos", cookie);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asText();

        // While the first attempt is running, a new convocation of the same case gives no second attempt (V004).
        var paralelo = post("/v1/estudiantes/sesiones/" + segunda + "/intentos", cookie);
        assertEquals(409, paralelo.statusCode(), paralelo.body());
        assertTrue(paralelo.body().contains("REPETICION_NO_HABILITADA"), paralelo.body());

        assertEquals(200, post("/v1/estudiantes/intentos/" + intento + "/finalizar", cookie).statusCode());
        var mismaConvocatoria = post("/v1/estudiantes/sesiones/" + primera + "/intentos", cookie);
        assertEquals(409, mismaConvocatoria.statusCode(), mismaConvocatoria.body());
        assertTrue(mismaConvocatoria.body().contains("REPETICION_NO_HABILITADA"), mismaConvocatoria.body());
        var otraSesion = post("/v1/estudiantes/sesiones/" + segunda + "/intentos", cookie);
        assertEquals(409, otraSesion.statusCode(), otraSesion.body());
        assertTrue(otraSesion.body().contains("REPETICION_NO_HABILITADA"), otraSesion.body());
        assertEquals(1, intentos(conv1));
        assertEquals(0, intentos(conv2));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\" WHERE \"idConvocatoria\"=?", Integer.class, conv2),
                "a rejected repetition leaves no participation either");
    }

    @Test
    @DisplayName("8: ocho inicios simultáneos del mismo estudiante crean un solo intento (un 201 y siete 200)")
    void inicioConcurrente() throws Exception {
        Alumno a = alumno("conc45");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, true, 1800, "now()+interval '2 hours'");
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        String cookie = login(a);
        int n = 8;
        var pool = Executors.newFixedThreadPool(n);
        var salida = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> tareas = new ArrayList<>();
        List<Integer> estados = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                tareas.add(pool.submit(() -> {
                    salida.await();
                    return post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookie);
                }));
            }
            salida.countDown();
            for (var t : tareas) {
                var r = t.get(60, TimeUnit.SECONDS);
                estados.add(r.statusCode());
                ids.add(json.readTree(r.body()).get("intento").get("idIntento").asText());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, estados.stream().filter(s -> s == 201).count(), estados.toString());
        assertEquals(n - 1, estados.stream().filter(s -> s == 200).count(), estados.toString());
        assertEquals(1, ids.stream().distinct().count(), ids.toString());
        assertEquals(1, intentos(conv));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\" WHERE \"idConvocatoria\"=?", Integer.class, conv));
    }

    // ─── V1-c ────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("11: finalizar antes del vencimiento = ENTREGA_ESTUDIANTE con fin anterior al límite")
    void finalizacionAntesDelVencimiento() throws Exception {
        Alumno a = alumno("antes");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, true, 1800, "now()+interval '2 hours'");
        RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        String cookie = login(a);
        String intento = iniciar(sesion, cookie);
        var fin = post("/v1/estudiantes/intentos/" + intento + "/finalizar", cookie);
        assertEquals(200, fin.statusCode(), fin.body());
        assertEquals("ENTREGA_ESTUDIANTE", json.readTree(fin.body()).get("motivoFinalizacion").asText());
        Map<String, Object> f = fila(intento);
        assertEquals("ENTREGA_ESTUDIANTE", f.get("motivoFinalizacion"));
        assertFalse(instante(f.get("fin")).isAfter(instante(f.get("limiteTemporal"))));
    }

    @Test
    @DisplayName("12+15: tras el límite el PUT sigue en 409 sin escribir y finalizar registra TIEMPO_VENCIDO; respuestas intactas")
    void finalizacionDespuesDelVencimiento() throws Exception {
        Alumno a = alumno("despues");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID p1 = c[2], o1a = c[4], o1b = c[5];
        UUID sesion = sesion(c, true, 3, "now()+interval '2 hours'");
        RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        String cookie = login(a);
        String intento = iniciar(sesion, cookie);
        var respuesta = RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento + "/respuestas/" + p1,
                "{\"idOpcionSeleccionada\":\"" + o1a + "\",\"revisionEsperada\":0}", cookie);
        assertEquals(200, respuesta.statusCode(), respuesta.body());
        Map<String, Object> confirmada = respuesta(intento);

        esperarPg("SELECT clock_timestamp() > \"limiteTemporal\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid", intento);
        var tarde = RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento + "/respuestas/" + p1,
                "{\"idOpcionSeleccionada\":\"" + o1b + "\",\"revisionEsperada\":1}", cookie);
        assertEquals(409, tarde.statusCode(), tarde.body());
        assertTrue(tarde.body().contains("LIMITE_TEMPORAL_VENCIDO"), tarde.body());
        assertEquals("EnCurso", fila(intento).get("estado"), "a rejected PUT does not close the attempt (unchanged rule)");

        var fin = post("/v1/estudiantes/intentos/" + intento + "/finalizar", cookie);
        assertEquals(200, fin.statusCode(), fin.body());
        JsonNode cierre = json.readTree(fin.body());
        assertEquals("TIEMPO_VENCIDO", cierre.get("motivoFinalizacion").asText());
        Map<String, Object> f = fila(intento);
        assertEquals("Finalizado", f.get("estado"));
        assertEquals("TIEMPO_VENCIDO", f.get("motivoFinalizacion"));
        assertTrue(instante(f.get("fin")).isAfter(instante(f.get("limiteTemporal"))));
        // Retry = same closing, same fin.
        var repetido = post("/v1/estudiantes/intentos/" + intento + "/finalizar", cookie);
        assertEquals(200, repetido.statusCode(), repetido.body());
        assertEquals(cierre.get("fin").asText(), json.readTree(repetido.body()).get("fin").asText());
        assertEquals("TIEMPO_VENCIDO", json.readTree(repetido.body()).get("motivoFinalizacion").asText());
        // The confirmed answer is preserved exactly (option, revision, timestamp).
        assertEquals(confirmada, respuesta(intento));
        assertEquals(o1a, confirmada.get("idOpcionSeleccionada"));
    }

    @Test
    @DisplayName("13+14+15: el cierre docente cierra por tiempo solo los vencidos; los vigentes siguen con su límite y operan")
    void cierreDocenteConIntentosVencidosYVigentes() throws Exception {
        Alumno vencido = alumno("cdv"), vigente = alumno("cdg"), tarde = alumno("cdt");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID p1 = c[2], p2 = c[3], o1a = c[4], o2a = c[6];
        UUID sesion = sesion(c, true, 3, "now()+interval '2 hours'");
        RecorridoParticipacionQa.convocar(jdbc, sesion, vencido.estudiante);
        RecorridoParticipacionQa.convocar(jdbc, sesion, vigente.estudiante);
        UUID convTarde = RecorridoParticipacionQa.convocar(jdbc, sesion, tarde.estudiante);
        String cookieVencido = login(vencido), cookieVigente = login(vigente), cookieTarde = login(tarde);

        String intentoVencido = iniciar(sesion, cookieVencido);
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intentoVencido
                + "/respuestas/" + p1, "{\"idOpcionSeleccionada\":\"" + o1a + "\",\"revisionEsperada\":0}", cookieVencido).statusCode());
        Map<String, Object> respuestaVencido = respuesta(intentoVencido);
        esperarPg("SELECT clock_timestamp() > \"limiteTemporal\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid", intentoVencido);
        // The teacher gives later starters more time; the second student starts with a limit still far ahead.
        jdbc.update("UPDATE \"Sesion\" SET \"tiempoMaximoParticipacionSegundos\"=1800 WHERE \"idSesion\"=?", sesion);
        String intentoVigente = iniciar(sesion, cookieVigente);
        Object limiteVigente = fila(intentoVigente).get("limiteTemporal");

        // Early closing by the teacher (real M4 route, real personnel authentication, owner of the session).
        var cierre = cambiarEstadoSesion(c[1], sesion, "Finalizado");
        assertEquals(200, cierre.statusCode(), cierre.body());
        assertEquals("Finalizado", jdbc.queryForObject("SELECT \"estado\" FROM \"Sesion\" WHERE \"idSesion\"=?", String.class, sesion));

        Map<String, Object> fv = fila(intentoVencido);
        assertEquals("Finalizado", fv.get("estado"));
        assertEquals("TIEMPO_VENCIDO", fv.get("motivoFinalizacion"));
        assertTrue(instante(fv.get("fin")).isAfter(instante(fv.get("limiteTemporal"))));
        assertEquals(respuestaVencido, respuesta(intentoVencido), "confirmed answers preserved by the teacher closing");
        Map<String, Object> fg = fila(intentoVigente);
        assertEquals("EnCurso", fg.get("estado"), "not marked TIEMPO_VENCIDO before its own limit");
        assertNull(fg.get("motivoFinalizacion"));
        assertEquals(limiteVigente, fg.get("limiteTemporal"), "the persisted limit is never reduced");

        // The student who already finalized-by-time gets the persisted closing back (idempotent).
        var reintento = post("/v1/estudiantes/intentos/" + intentoVencido + "/finalizar", cookieVencido);
        assertEquals(200, reintento.statusCode(), reintento.body());
        assertEquals("TIEMPO_VENCIDO", json.readTree(reintento.body()).get("motivoFinalizacion").asText());
        // The in-time student keeps working under the same ownership rules: resume, answer and deliver.
        var reanuda = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookieVigente);
        assertEquals(200, reanuda.statusCode(), reanuda.body());
        assertEquals(intentoVigente, json.readTree(reanuda.body()).get("intento").get("idIntento").asText());
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intentoVigente
                + "/respuestas/" + p2, "{\"idOpcionSeleccionada\":\"" + o2a + "\",\"revisionEsperada\":0}", cookieVigente).statusCode());
        assertEquals(403, post("/v1/estudiantes/intentos/" + intentoVigente + "/finalizar", cookieVencido).statusCode());
        var entrega = post("/v1/estudiantes/intentos/" + intentoVigente + "/finalizar", cookieVigente);
        assertEquals(200, entrega.statusCode(), entrega.body());
        assertEquals("ENTREGA_ESTUDIANTE", json.readTree(entrega.body()).get("motivoFinalizacion").asText());
        // A closed session admits no new first start (E1: the session is no longer EnCurso).
        noAutorizado(cookieTarde, sesion, convTarde);
    }

    @Test
    @DisplayName("V1-c concurrencia: cierre docente y entrega simultánea del estudiante sobre un intento vencido = un solo cierre")
    void cierreDocenteYEntregaSimultaneos() throws Exception {
        Alumno a = alumno("v1conc");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, true, 3, "now()+interval '2 hours'");
        RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        String cookie = login(a);
        String intento = iniciar(sesion, cookie);
        esperarPg("SELECT clock_timestamp() > \"limiteTemporal\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid", intento);
        cambiarEstadoSesion(c[1], UUID.randomUUID(), "Finalizado"); // warm-up: permission + login paths (404 expected)

        var pool = Executors.newFixedThreadPool(2);
        var salida = new CountDownLatch(1);
        try {
            Future<HttpResponse<String>> docente = pool.submit(() -> { salida.await(); return cambiarEstadoSesion(c[1], sesion, "Finalizado"); });
            Future<HttpResponse<String>> alumno = pool.submit(() -> { salida.await(); return post("/v1/estudiantes/intentos/" + intento + "/finalizar", cookie); });
            salida.countDown();
            var rDocente = docente.get(60, TimeUnit.SECONDS);
            var rAlumno = alumno.get(60, TimeUnit.SECONDS);
            assertEquals(200, rDocente.statusCode(), rDocente.body());
            assertEquals(200, rAlumno.statusCode(), rAlumno.body());
            assertEquals("TIEMPO_VENCIDO", json.readTree(rAlumno.body()).get("motivoFinalizacion").asText());
            Map<String, Object> f = fila(intento);
            assertEquals("Finalizado", f.get("estado"));
            assertEquals("TIEMPO_VENCIDO", f.get("motivoFinalizacion"));
            // Whoever won, the student sees exactly the single persisted closing.
            assertEquals(instante(f.get("fin")), Instant.parse(json.readTree(rAlumno.body()).get("fin").asText()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("13: cerrar una sesión sin intentos vencidos no toca nada; otro docente no puede cerrarla")
    void cierreDocenteSinVencidosYDocenteAjeno() throws Exception {
        Alumno a = alumno("sinv");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID[] otro = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, true, 1800, "now()+interval '2 hours'");
        RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        String intento = iniciar(sesion, login(a));
        var ajeno = cambiarEstadoSesion(otro[1], sesion, "Finalizado");
        assertNotEquals(200, ajeno.statusCode(), ajeno.body());
        assertEquals("EnCurso", jdbc.queryForObject("SELECT \"estado\" FROM \"Sesion\" WHERE \"idSesion\"=?", String.class, sesion));
        var cierre = cambiarEstadoSesion(c[1], sesion, "Finalizado");
        assertEquals(200, cierre.statusCode(), cierre.body());
        assertEquals("EnCurso", fila(intento).get("estado"));
    }

    @Test
    @DisplayName("16: un intento histórico SIN_FASES (fixture) se reanuda sin consultar políticas y se finaliza")
    void intentoHistoricoSinFases() throws Exception {
        Alumno a = alumno("hist");
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = sesion(c, false, 1800, "now()+interval '2 hours'"); // not started: E1 would reject a first start
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, sesion, a.estudiante);
        UUID participacion = UUID.randomUUID(), intento = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", participacion, conv);
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\",\"limiteTemporal\",\"estado\")"
                + " VALUES (?,?,1,now(),now()+interval '30 minutes','EnCurso')", intento, participacion);
        String cookie = login(a);
        var reanudacion = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookie);
        assertEquals(200, reanudacion.statusCode(), reanudacion.body());
        assertEquals(intento.toString(), json.readTree(reanudacion.body()).get("intento").get("idIntento").asText());
        var fin = post("/v1/estudiantes/intentos/" + intento + "/finalizar", cookie);
        assertEquals(200, fin.statusCode(), fin.body());
        assertEquals("ENTREGA_ESTUDIANTE", json.readTree(fin.body()).get("motivoFinalizacion").asText());
    }

    // ─── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    record Alumno(String correo, UUID estudiante) {}

    private Alumno alumno(String alias) {
        String correo = alias + "." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test";
        return new Alumno(correo, RecorridoParticipacionQa.estudiante(catalog, jdbc, correo));
    }

    private String login(Alumno a) throws Exception {
        return QA.login(http, port, json, worker, jdbc, a.correo);
    }

    private UUID sesion(UUID[] caso, boolean enCurso, int maximo, String finPrevistoSql) {
        return sesion(caso, enCurso, maximo, finPrevistoSql, "clock_timestamp()-interval '1 hour'");
    }

    /** Session with explicit SQL times (PostgreSQL clock); {@code enCurso} marks it opened by the teacher. */
    private UUID sesion(UUID[] caso, boolean enCurso, int maximo, String finPrevistoSql, String inicioPrevistoSql) {
        UUID sesion = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\","
                + "\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\") VALUES (?,?,?,'Espacio QA 45',"
                + inicioPrevistoSql + "," + finPrevistoSql + ",?)", sesion, caso[0], caso[1], maximo);
        if (enCurso) {
            jdbc.update("UPDATE \"Sesion\" SET \"estado\"='EnCurso', \"fechaInicioReal\"=clock_timestamp() WHERE \"idSesion\"=?", sesion);
        }
        return sesion;
    }

    private String iniciar(UUID sesion, String cookie) throws Exception {
        var r = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookie);
        assertEquals(201, r.statusCode(), r.body());
        return json.readTree(r.body()).get("intento").get("idIntento").asText();
    }

    private void noAutorizado(String cookie, UUID sesion, UUID convocatoria) throws Exception {
        var r = post("/v1/estudiantes/sesiones/" + sesion + "/intentos", cookie);
        assertEquals(403, r.statusCode(), r.body());
        assertTrue(r.body().contains("INICIO_NO_AUTORIZADO"), r.body());
        assertEquals(0, intentos(convocatoria));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Participacion\" WHERE \"idConvocatoria\"=?", Integer.class, convocatoria));
    }

    private HttpResponse<String> post(String ruta, String cookie) throws Exception {
        return RecorridoParticipacionQa.enviar(http, port, "POST", ruta, null, cookie);
    }

    /** Real M4 route with real personnel authentication (Bearer) of the teacher {@code docente}. */
    private HttpResponse<String> cambiarEstadoSesion(UUID docente, UUID sesion, String estado) throws Exception {
        Integer permiso = permisos.findByCodigo("GESTIONAR_SESION").map(PermisoEntity::getIdPermiso)
                .orElseGet(() -> catalog.createPermission("GESTIONAR_SESION", "Gestionar sesión", null).getIdPermiso());
        if (jdbc.queryForObject("SELECT count(*) FROM \"UsuarioPermiso\" WHERE \"idUsuario\"=? AND \"idPermiso\"=?",
                Integer.class, docente, permiso) == 0) {
            catalog.assignPermission(docente, permiso, administrador());
        }
        String correo = jdbc.queryForObject("SELECT \"correo\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente);
        var login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/personal/autenticar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\""
                        + RecorridoParticipacionQa.FIXTURE + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        String token = json.readTree(login.body()).get("accessToken").asText();
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/m4/sesiones/" + sesion + "/estado"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"nuevoEstado\":\"" + estado + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private UUID administrador() {
        if (!catalog.existeRolAdministrador()) {
            catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture");
        }
        return catalog.registerUser("Administrador fixture 45", UUID.randomUUID() + "@admin.prisaly.test",
                UUID.randomUUID().toString(), IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    private Map<String, Object> fila(String intento) {
        return jdbc.queryForMap("SELECT \"estado\",\"motivoFinalizacion\",\"inicio\",\"limiteTemporal\",\"fin\" FROM \"Intento\""
                + " WHERE \"idIntento\"=?::uuid", intento);
    }

    private Map<String, Object> respuesta(String intento) {
        return jdbc.queryForMap("SELECT \"idOpcionSeleccionada\",\"revision\",\"fechaActualizacion\" FROM \"Respuesta\""
                + " WHERE \"idIntento\"=?::uuid", intento);
    }

    private OffsetDateTime relojPg() {
        return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
    }

    /** Waits (bounded) until a PostgreSQL clock condition holds: the reference clock is never the JVM's. */
    private void esperarPg(String condicion, Object id) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Boolean.TRUE.equals(jdbc.queryForObject(condicion, Boolean.class, id))) {
            assertTrue(System.nanoTime() < limite, "PostgreSQL clock condition not reached: " + condicion);
            Thread.sleep(100);
        }
    }

    private static Instant instante(Object valor) {
        if (valor instanceof OffsetDateTime o) return o.toInstant();
        if (valor instanceof java.sql.Timestamp t) return t.toInstant();
        if (valor instanceof Instant i) return i;
        throw new IllegalArgumentException(String.valueOf(valor));
    }

    private int intentos(UUID convocatoria) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Intento\" i JOIN \"Participacion\" p ON p.\"idParticipacion\"=i.\"idParticipacion\""
                + " WHERE p.\"idConvocatoria\"=?", Integer.class, convocatoria);
    }
}
