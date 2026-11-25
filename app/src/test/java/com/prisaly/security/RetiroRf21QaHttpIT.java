package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;
import java.net.http.HttpClient;
import java.util.UUID;
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
 * DC-92 RF-21 over the real APP, PostgreSQL 16.15 (DC-92 candidate schema) and real student sessions (DD-02 B):
 * withdrawal by the session's teacher with explicit resolution of an attempt in progress, history and grades kept,
 * new participation blocked by M5's own eligibility, unique notices to student and teacher, audit record.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RetiroRf21QaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        Dc92Qa.propiedades(QA, p);
        p.add("prisaly.m4.retiro.enabled", () -> "true");   // QA only
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void retiroConResolucionExplicitaHistorialBloqueoYNotificaciones() throws Exception {
        UUID[] c = Dc92Qa.casoConRubrica(catalog, jdbc);
        UUID docente = c[1];
        // The session programmer must hold GESTIONAR_SESION (M4 rule, same as convoking).
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        UUID admin = catalog.registerUser("Admin 21", Dc92Qa.correo("admin21"), RecorridoParticipacionQa.FIXTURE,
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        Integer gestionar = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\"='GESTIONAR_SESION'", Integer.class)
                .stream().findFirst().orElseGet(() -> catalog.createPermission("GESTIONAR_SESION", "GESTIONAR_SESION", null).getIdPermiso());
        catalog.assignPermission(docente, gestionar, admin);
        String token = Dc92Qa.tokenDocente(http, port, json, jdbc, docente);
        String otro = Dc92Qa.otroDocente(catalog, http, port, json);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, c[0], docente, true, 1800);

        // Ana: attempt in progress with an answer.
        String correoAna = Dc92Qa.correo("rf21a");
        UUID ana = RecorridoParticipacionQa.estudiante(catalog, jdbc, correoAna);
        UUID convAna = RecorridoParticipacionQa.convocar(jdbc, sesion, ana);
        String cookieAna = QA.login(http, port, json, worker, jdbc, correoAna);
        var inicio = Dc92Qa.iniciar(http, port, sesion, cookieAna);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asString();
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento
                + "/respuestas/" + c[2], "{\"idOpcionSeleccionada\":\"" + c[5] + "\",\"revisionEsperada\":0}", cookieAna).statusCode());
        // Beto: never started.
        UUID beto = RecorridoParticipacionQa.estudiante(catalog, jdbc, Dc92Qa.correo("rf21b"));
        UUID convBeto = RecorridoParticipacionQa.convocar(jdbc, sesion, beto);

        String rutaAna = "/v1/docente/convocatorias/" + convAna + "/retiro";
        assertEquals(403, Dc92Qa.personal(http, port, "POST", rutaAna, otro, "{\"motivo\":\"x\"}").statusCode());
        assertEquals(401, Dc92Qa.personal(http, port, "POST", rutaAna, null, "{\"motivo\":\"x\"}").statusCode());
        assertEquals(404, Dc92Qa.personal(http, port, "POST", "/v1/docente/convocatorias/" + UUID.randomUUID() + "/retiro", token, "{}").statusCode());
        assertEquals(400, Dc92Qa.personal(http, port, "POST", rutaAna, token, "{\"resolucionIntentoEnCurso\":\"BORRAR\"}").statusCode());
        var sinResolucion = Dc92Qa.personal(http, port, "POST", rutaAna, token, "{\"motivo\":\"Cancelo la materia\"}");
        assertEquals(409, sinResolucion.statusCode(), sinResolucion.body());
        assertTrue(sinResolucion.body().contains("INTENTO_EN_CURSO_REQUIERE_RESOLUCION"));
        assertEquals("EnCurso", jdbc.queryForObject("SELECT \"estado\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid", String.class, intento),
                "a refused withdrawal changes nothing");

        var retiro = Dc92Qa.personal(http, port, "POST", rutaAna, token,
                "{\"motivo\":\"Cancelo la materia\",\"resolucionIntentoEnCurso\":\"FINALIZAR_INTENTO\"}");
        assertEquals(201, retiro.statusCode(), retiro.body());
        JsonNode r = json.readTree(retiro.body());
        assertEquals(docente.toString(), r.get("idDocente").asString());
        assertEquals("INTENTO_FINALIZADO_POR_RETIRO", r.get("resolucionIntento").asString());
        assertEquals(intento, r.get("idIntentoResuelto").asString());
        var fila = jdbc.queryForMap("SELECT \"estado\",\"motivoFinalizacion\",\"fin\" FROM \"Intento\" WHERE \"idIntento\"=?::uuid", intento);
        assertEquals("Finalizado", fila.get("estado"));
        assertEquals("RETIRO", fila.get("motivoFinalizacion"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Respuesta\" WHERE \"idIntento\"=?::uuid", Integer.class, intento),
                "history kept");
        assertEquals("Retirado", jdbc.queryForObject("SELECT \"estadoAutorizacion\" FROM \"Convocatoria\" WHERE \"idConvocatoria\"=?", String.class, convAna));

        // Blocked by M5's own eligibility; repeated withdrawal refused without duplicated notices.
        var bloqueado = Dc92Qa.iniciar(http, port, sesion, cookieAna);
        assertTrue(bloqueado.statusCode() == 403 || bloqueado.statusCode() == 409, bloqueado.statusCode() + " " + bloqueado.body());
        assertEquals(409, Dc92Qa.personal(http, port, "POST", rutaAna, token, "{\"resolucionIntentoEnCurso\":\"FINALIZAR_INTENTO\"}").statusCode());
        String clave = "retiro:" + convAna;
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Aviso\" WHERE \"claveEvento\"=?", Integer.class, clave));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"EnvioCorreo\" WHERE \"claveIdempotencia\"=?", Integer.class, clave));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"Aviso\" WHERE \"claveEvento\"=? AND \"idUsuarioDestino\"=?",
                Integer.class, "retiro-docente:" + convAna, docente), "the teacher is notified too");
        String correo = jdbc.queryForObject("SELECT \"contenido\" FROM \"EnvioCorreo\" WHERE \"claveIdempotencia\"=?", String.class, clave);
        assertFalse(correo.contains("Cancelo la materia"), "the reason never travels by email");
        assertEquals(200, Dc92Qa.personal(http, port, "GET", rutaAna, token, null).statusCode());
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM \"RetiroConvocatoria\" WHERE \"idConvocatoria\"=?", convAna));

        // Beto: no attempt in progress, no resolution needed; re-convoking the same student stays a duplicate (M4).
        var b = Dc92Qa.personal(http, port, "POST", "/v1/docente/convocatorias/" + convBeto + "/retiro", token, "{}");
        assertEquals(201, b.statusCode(), b.body());
        assertEquals("SIN_INTENTO_EN_CURSO", json.readTree(b.body()).get("resolucionIntento").asString());
        assertEquals(404, Dc92Qa.personal(http, port, "GET", "/v1/docente/convocatorias/" + RecorridoParticipacionQa.convocar(jdbc,
                RecorridoParticipacionQa.sesion(jdbc, c[0], docente, true, 1800), beto) + "/retiro", token, null).statusCode());
    }
}
