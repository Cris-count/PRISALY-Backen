package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.infrastructure.EnsamblajeCandidatoQa;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-CIERRE-FUNCIONAL-09, fase 4 — <b>RECORRIDO CON POLÍTICAS CANDIDATAS (NO RATIFICADAS), SOLO QA.</b>
 *
 * <p>Autenticación DD-02 B (Mailpit) → {@code GET /v1/estudiantes/convocatorias} → {@code POST …/sesiones/{id}/intentos}
 * → {@code GET …/intentos/{id}/contenido} → {@code PUT …/respuestas/{idPregunta}} → {@code POST …/finalizar}, sobre la
 * APP real y PostgreSQL 16.15. Lo único sustituido es la política de inicio: {@link EnsamblajeCandidatoQa} cambia
 * {@code ElegibilidadNoRatificada}/{@code LimiteTemporalNoRatificado} por las candidatas. Un resultado verde aquí NO
 * demuestra una operación productiva: con el ensamblado productivo el primer inicio sigue en 409
 * ({@link InicioProductivoM4M5QaHttpIT}).
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecorridoCandidatoM4M5QaHttpIT.PoliticasCandidatasSoloQa.class)
class RecorridoCandidatoM4M5QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) { QA.propiedades(p, true); }

    @TestConfiguration(proxyBeanMethods = false)
    static class PoliticasCandidatasSoloQa {
        @Bean @Primary
        AccesoProtegidoParticipacion accesoProtegidoParticipacionCandidatoQa(RepositorioIntentos repositorio,
                RepositorioParticipaciones participaciones, PlatformTransactionManager transacciones,
                IdentidadEstudianteActual identidad, TitularidadIntentos titularidad,
                ProgramacionInicioCoordinadoPort coordinacion, ProgramacionAutenticadaPort autenticada,
                ProgramacionParticipacionPort programacion, ContenidoCasoEstudiantilPort contenido) {
            return EnsamblajeCandidatoQa.conPoliticasCandidatas(repositorio, participaciones, transacciones, identidad,
                    titularidad, coordinacion, autenticada, programacion, contenido);
        }
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    private String correo(String alias) { return alias + "." + UUID.randomUUID().toString().substring(0, 8) + "@prisaly.test"; }

    @Test
    void recorridoCompletoAutenticacionConvocatoriaInicioContenidoRespuestaFinalizacion() throws Exception {
        String correoA = correo("ana09"), correoB = correo("beto09");
        UUID ana = RecorridoParticipacionQa.estudiante(catalog, jdbc, correoA);
        UUID beto = RecorridoParticipacionQa.estudiante(catalog, jdbc, correoB);
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID caso = c[0], docente = c[1], p1 = c[2], o1a = c[4];
        UUID enCurso = RecorridoParticipacionQa.sesion(jdbc, caso, docente, true, 1800);
        UUID sinIniciar = RecorridoParticipacionQa.sesion(jdbc, caso, docente, false, 1800);
        UUID retirada = RecorridoParticipacionQa.sesion(jdbc, caso, docente, true, 1800);
        UUID convAna = RecorridoParticipacionQa.convocar(jdbc, enCurso, ana);
        UUID convAnaSinIniciar = RecorridoParticipacionQa.convocar(jdbc, sinIniciar, ana);
        UUID convAnaRetirada = RecorridoParticipacionQa.convocar(jdbc, retirada, ana);
        jdbc.update("UPDATE \"Convocatoria\" SET \"estadoAutorizacion\"='Retirado', \"fechaRetiro\"=now() WHERE \"idConvocatoria\"=?", convAnaRetirada);
        UUID convBeto = RecorridoParticipacionQa.convocar(jdbc, enCurso, beto);

        // 1. Authentication (DD-02 B, code delivered to Mailpit).
        String cookieAna = QA.login(http, port, json, worker, correoA);
        String cookieBeto = QA.login(http, port, json, worker, correoB);

        // 2. Discovery: only Ana's own AUTHORIZED convocations (retired and others' are absent).
        var lista = RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/convocatorias", null, cookieAna);
        assertEquals(200, lista.statusCode(), lista.body());
        assertTrue(lista.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode convocatorias = json.readTree(lista.body());
        List<String> ids = new ArrayList<>();
        convocatorias.forEach(n -> ids.add(n.get("idConvocatoria").asText()));
        assertEquals(2, ids.size(), lista.body());
        assertTrue(ids.containsAll(List.of(convAna.toString(), convAnaSinIniciar.toString())));
        assertFalse(ids.contains(convAnaRetirada.toString()));
        assertFalse(ids.contains(convBeto.toString()));
        assertFalse(lista.body().contains(beto.toString()) || lista.body().contains("esCorrecta"));

        // 3a. Candidate eligibility: a session not started is not eligible (403) and writes nothing.
        var noElegible = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sinIniciar + "/intentos", null, cookieAna);
        assertEquals(403, noElegible.statusCode(), noElegible.body());
        assertTrue(noElegible.body().contains("INICIO_NO_AUTORIZADO"), noElegible.body());
        assertEquals(0, intentos(convAnaSinIniciar));

        // 3b. Start in the running session (candidate limit = min(inicio + 1800 s, finPrevisto)).
        var inicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + enCurso + "/intentos", null, cookieAna);
        assertEquals(201, inicio.statusCode(), inicio.body());
        JsonNode creado = json.readTree(inicio.body());
        assertFalse(creado.get("reanudado").asBoolean());
        String intento = creado.get("intento").get("idIntento").asText();
        Instant ini = Instant.parse(creado.get("intento").get("inicio").asText());
        Instant lim = Instant.parse(creado.get("intento").get("limiteTemporal").asText());
        assertEquals(Duration.ofSeconds(1800), Duration.between(ini, lim));
        assertEquals(1, intentos(convAna));
        // Resume returns the same attempt with its persisted times.
        var reanudacion = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + enCurso + "/intentos", null, cookieAna);
        assertEquals(200, reanudacion.statusCode(), reanudacion.body());
        assertEquals(intento, json.readTree(reanudacion.body()).get("intento").get("idIntento").asText());
        assertEquals(lim.toString(), Instant.parse(json.readTree(reanudacion.body()).get("intento").get("limiteTemporal").asText()).toString());

        // 4. Academic content of the own attempt, without correct answers.
        var contenido = RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/contenido", null, cookieAna);
        assertEquals(200, contenido.statusCode(), contenido.body());
        assertFalse(contenido.body().contains("esCorrecta") || contenido.body().contains("explicacion"));
        assertEquals(caso.toString(), json.readTree(contenido.body()).get("caso").get("idCaso").asText());
        assertEquals(403, RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/contenido", null, cookieBeto).statusCode());

        // 5. Answer with CAS (existing PUT unchanged).
        var respuesta = RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento + "/respuestas/" + p1,
                "{\"idOpcionSeleccionada\":\"" + o1a + "\",\"revisionEsperada\":0}", cookieAna);
        assertEquals(200, respuesta.statusCode(), respuesta.body());
        assertEquals(403, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento + "/respuestas/" + p1,
                "{\"idOpcionSeleccionada\":\"" + o1a + "\",\"revisionEsperada\":1}", cookieBeto).statusCode());

        // 6. Finalization: the other student cannot; the owner closes once; the retry returns the same closing (D13).
        assertEquals(403, RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookieBeto).statusCode());
        var fin = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookieAna);
        assertEquals(200, fin.statusCode(), fin.body());
        JsonNode cierre = json.readTree(fin.body());
        assertEquals("Finalizado", cierre.get("estado").asText());
        assertEquals("ENTREGA_ESTUDIANTE", cierre.get("motivoFinalizacion").asText());
        var repetido = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookieAna);
        assertEquals(200, repetido.statusCode(), repetido.body());
        assertEquals(cierre.get("fin").asText(), json.readTree(repetido.body()).get("fin").asText());
        var trasCierre = RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento + "/respuestas/" + p1,
                "{\"idOpcionSeleccionada\":\"" + c[5] + "\",\"revisionEsperada\":1}", cookieAna);
        assertEquals(409, trasCierre.statusCode(), trasCierre.body());
        // A new start after closing is not a repetition (PF-08 not enabled).
        var otroInicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + enCurso + "/intentos", null, cookieAna);
        assertEquals(409, otroInicio.statusCode(), otroInicio.body());

        // Persisted state.
        assertEquals("Finalizado", jdbc.queryForObject("SELECT \"estado\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid", String.class, intento));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?::uuid", Integer.class, intento));
        assertEquals(o1a, jdbc.queryForObject("SELECT \"idOpcionSeleccionada\" FROM \"Respuesta\" WHERE \"idIntento\"=?::uuid", UUID.class, intento));
        assertEquals(0, intentos(convBeto));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid", Integer.class, intento),
                "finalizing does not evaluate (M6 not active)");
    }

    @Test
    void dosIniciosSimultaneosDelMismoEstudianteCreanUnSoloIntento() throws Exception {
        String correo = correo("conc09");
        UUID estudiante = RecorridoParticipacionQa.estudiante(catalog, jdbc, correo);
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], c[1], true, 1800);
        UUID conv = RecorridoParticipacionQa.convocar(jdbc, sesion, estudiante);
        String cookie = QA.login(http, port, json, worker, correo);
        var pool = Executors.newFixedThreadPool(2);
        var salida = new CountDownLatch(1);
        List<Future<java.net.http.HttpResponse<String>>> tareas = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                tareas.add(pool.submit(() -> {
                    salida.await();
                    return RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookie);
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            List<String> idIntentos = new ArrayList<>();
            for (var t : tareas) {
                var r = t.get(60, TimeUnit.SECONDS);
                estados.add(r.statusCode());
                idIntentos.add(json.readTree(r.body()).get("intento").get("idIntento").asText());
            }
            assertTrue(estados.containsAll(List.of(201, 200)), estados.toString());
            assertEquals(idIntentos.get(0), idIntentos.get(1));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, intentos(conv));
    }

    @Test
    void sinSesionEstudiantilNadaResponde() throws Exception {
        UUID cualquiera = UUID.randomUUID();
        assertEquals(401, RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/convocatorias", null, null).statusCode());
        assertEquals(401, RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + cualquiera + "/intentos", null, null).statusCode());
        assertEquals(401, RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + cualquiera + "/finalizar", null, null).statusCode());
        assertEquals(401, RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/convocatorias", null, "prisaly_estudiante=REDACTADO-cookie-forjada").statusCode());
    }

    private int intentos(UUID convocatoria) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Intento\" i JOIN \"Participacion\" p ON p.\"idParticipacion\"=i.\"idParticipacion\""
                + " WHERE p.\"idConvocatoria\"=?", Integer.class, convocatoria);
    }
}
