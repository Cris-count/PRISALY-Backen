package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-JORNADA-NOCTURNA-86 — <b>CONTRACTUAL INTEGRATION OF THE CANDIDATE INCREMENTS, QA ONLY.</b> Over the real APP,
 * PostgreSQL 16.15 (official schema + the DD-02 B candidates V014-V016, disposable container) and a Mailpit capture
 * mailbox. Every protected feature is switched on ONLY in this context: DD-02 B, M5 start/finish publication, M6
 * teacher evaluation, P-78 student registration and the RF-16 report. Nothing here activates anything elsewhere.
 *
 * <p>Administrator (P-78, no SQL) → teacher (M4 HTTP: convoke, open the session) → two students (DD-02 B through M7 and
 * Mailpit) → M5 start, resume, answer, finish → isolation between students → D-80-1 for student identities → M6
 * evaluation by the owner teacher → RF-16 CSV reflecting the grade. Only the case content is seeded by SQL, as in the
 * existing journeys (case authoring is covered by the M3 suites).
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlataformaContractualQaHttpIT {
    static final RecorridoParticipacionQa QA = new RecorridoParticipacionQa();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        // DC-92: DC-92 candidate schema, DD-02 B, M5 POST, M6 evaluation and N2 (QA only).
        Dc92Qa.propiedades(QA, p);
        p.add("prisaly.m1.alta-estudiantes.enabled", () -> "true");        // P-78 (QA only)
        p.add("prisaly.m7.reportes.participacion.enabled", () -> "true");  // RF-16 (QA only)
    }

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracionCodigoAccesoM7.TrabajoEntregas worker;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void administradorDocenteYDosEstudiantesRecorrenLaPlataformaConAislamientoYResultado() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);

        // ── 1. Administrator: catalog role (PD-01 extension) and two students through P-78, never SQL ────────────
        if (!catalog.existeRolAdministrador()) { catalog.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture"); }
        jdbc.execute(Files.readString(Path.of("../docs/provision/pd01-p78-rol-estudiante.sql")));
        String correoAdmin = "admin86." + s + "@prisaly.test";
        UUID admin = catalog.registerUser("Admin 86", correoAdmin, RecorridoParticipacionQa.FIXTURE,
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
        String tokenAdmin = loginPersonal(correoAdmin);
        String correoAna = "ana86." + s + "@prisaly.test", correoBeto = "beto86." + s + "@prisaly.test";
        UUID[] ana = alta(tokenAdmin, "Ana 86", correoAna), beto = alta(tokenAdmin, "Beto 86", correoBeto);

        // ── 2. Teacher: published case (seeded content + rubric before programming), GESTIONAR_SESION, real M4 HTTP ──
        UUID[] c = RecorridoParticipacionQa.caso(catalog, jdbc);
        UUID caso = c[0], docente = c[1], p1 = c[2], p2 = c[3], o1b = c[5], o2a = c[6];
        UUID rubrica = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\",\"definicionEvaluacion\") VALUES (?,?,?,'{\"fixture\":\"86\"}')",
                rubrica, caso, "Rubrica 86 " + s);
        jdbc.update("INSERT INTO \"RubricaPregunta\" (\"idRubricaPregunta\",\"idRubrica\",\"idPregunta\",\"valorPregunta\")"
                + " VALUES (gen_random_uuid(),?,?,6.00),(gen_random_uuid(),?,?,4.00)", rubrica, p1, rubrica, p2);
        catalog.assignPermission(docente, permiso("GESTIONAR_SESION"), admin);
        String correoDocente = jdbc.queryForObject("SELECT \"correo\" FROM \"Usuario\" WHERE \"idUsuario\"=?", String.class, docente);
        String tokenDocente = loginPersonal(correoDocente);
        UUID sesion = RecorridoParticipacionQa.sesion(jdbc, caso, docente, false, 1800);
        assertEquals(201, personal("POST", "/m4/sesiones/" + sesion + "/convocatorias", tokenDocente,
                "{\"estudianteId\":\"" + ana[1] + "\"}").statusCode());
        assertEquals(201, personal("POST", "/m4/sesiones/" + sesion + "/convocatorias", tokenDocente,
                "{\"estudianteId\":\"" + beto[1] + "\"}").statusCode());
        var abrir = personal("PATCH", "/m4/sesiones/" + sesion + "/estado", tokenDocente, "{\"nuevoEstado\":\"EnCurso\"}");
        assertEquals(200, abrir.statusCode(), abrir.body());

        // ── 3. Students: DD-02 B login through the M7 code worker and Mailpit; each sees only their own call ────────
        String cookieAna = QA.login(http, port, json, worker, jdbc, correoAna);
        String cookieBeto = QA.login(http, port, json, worker, jdbc, correoBeto);
        var convAna = RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/convocatorias", null, cookieAna);
        assertEquals(200, convAna.statusCode(), convAna.body());
        assertTrue(convAna.body().contains(sesion.toString()), convAna.body());

        // ── 4. M5: start, resume (same attempt), content, answers with CAS, finish (idempotent) ─────────────────────
        var inicio = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookieAna);
        assertEquals(201, inicio.statusCode(), inicio.body());
        String intento = json.readTree(inicio.body()).get("intento").get("idIntento").asString();
        var reanudar = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/sesiones/" + sesion + "/intentos", null, cookieAna);
        assertEquals(200, reanudar.statusCode(), reanudar.body());
        assertEquals(intento, json.readTree(reanudar.body()).get("intento").get("idIntento").asString(), "resume, never a new attempt");
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento, null, cookieAna).statusCode());
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/contenido", null, cookieAna).statusCode());
        for (UUID[] r : new UUID[][] {{p1, o1b}, {p2, o2a}}) {
            var guardar = RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento + "/respuestas/" + r[0],
                    "{\"idOpcionSeleccionada\":\"" + r[1] + "\",\"revisionEsperada\":0}", cookieAna);
            assertEquals(200, guardar.statusCode(), guardar.body());
        }

        // ── 5. Isolation: Beto can neither read, answer nor finish Ana's attempt; nothing of Ana leaks ───────────────
        for (var r : List.of(
                RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento, null, cookieBeto),
                RecorridoParticipacionQa.enviar(http, port, "GET", "/v1/estudiantes/intentos/" + intento + "/contenido", null, cookieBeto),
                RecorridoParticipacionQa.enviar(http, port, "PUT", "/v1/estudiantes/intentos/" + intento + "/respuestas/" + p1,
                        "{\"idOpcionSeleccionada\":\"" + c[4] + "\",\"revisionEsperada\":1}", cookieBeto),
                RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookieBeto))) {
            assertTrue(r.statusCode() == 403 || r.statusCode() == 404, r.statusCode() + " " + r.body());
            assertFalse(r.body().contains(correoAna) || r.body().contains(o1b.toString()), r.body());
        }
        assertEquals(o1b, jdbc.queryForObject("SELECT \"idOpcionSeleccionada\" FROM \"Respuesta\" WHERE \"idIntento\"=?::uuid AND \"idPregunta\"=?",
                UUID.class, intento, p1), "Beto's rejected PUT changed nothing");

        var fin = RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookieAna);
        assertEquals(200, fin.statusCode(), fin.body());
        assertEquals(200, RecorridoParticipacionQa.enviar(http, port, "POST", "/v1/estudiantes/intentos/" + intento + "/finalizar", null, cookieAna).statusCode());

        // ── 6. D-80-1 for real student identities: no personnel permission, no personnel route ───────────────────────
        var permisoEst = personal("PUT", "/v1/admin/usuarios/" + ana[0] + "/permisos/" + permiso("GESTIONAR_SESION"), tokenAdmin, null);
        assertEquals(422, permisoEst.statusCode(), permisoEst.body());
        assertTrue(permisoEst.body().contains("PERMISSION_NOT_ALLOWED_FOR_STUDENT"), permisoEst.body());
        for (String ruta : new String[] {"/m4/sesiones", "/v1/docente/sesiones/" + sesion + "/reporte-participacion",
                "/v1/evaluacion/intentos/" + intento}) {
            var r = RecorridoParticipacionQa.enviar(http, port, "GET", ruta, null, cookieAna);
            assertTrue(r.statusCode() == 401 || r.statusCode() == 403, ruta + " -> " + r.statusCode() + " " + r.body());
        }

        // ── 7. M6: the owner teacher evaluates the finished attempt (S1: 6.00 + 4.00 of 10 → scale 0-5) ─────────────
        var evaluacion = personal("POST", "/v1/evaluacion/intentos/" + intento, tokenDocente, "{\"idRubrica\":\"" + rubrica + "\"}");
        assertEquals(201, evaluacion.statusCode(), evaluacion.body());
        String valor = jdbc.queryForObject("SELECT \"valor\"::text FROM \"Calificacion\" WHERE \"idIntento\"=?::uuid", String.class, intento);
        assertTrue(evaluacion.body().contains("\"valor\":" + valor), evaluacion.body() + " vs " + valor);
        assertFalse(json.readTree(evaluacion.body()).get("liberada").asBoolean(), "not released yet");

        // ── 8. DC-92: N2 release, then RF-16/DD-11 shows the PUBLISHED grade; Beto is still CONVOCADO ──────────────
        assertEquals(201, personal("POST", "/v1/evaluacion/intentos/" + intento + "/liberacion", tokenDocente, null).statusCode());
        var csv = http.send(HttpRequest.newBuilder(uri("/v1/docente/sesiones/" + sesion + "/reporte-participacion"))
                .header("Authorization", "Bearer " + tokenDocente).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, csv.statusCode());
        String texto = new String(csv.body(), StandardCharsets.UTF_8);
        String filaAna = linea(texto, ana[1]), filaBeto = linea(texto, beto[1]);
        List<String> a = Arrays.asList(filaAna.split(",", -1)), b = Arrays.asList(filaBeto.split(",", -1));
        assertEquals(List.of("PARTICIPACION_FINALIZADA", "1", "PUBLICADA", valor), a.subList(6, 10), filaAna);
        assertEquals(List.of("CONVOCADO", "0", "SIN_INTENTOS", ""), b.subList(6, 10), filaBeto);
    }

    private UUID[] alta(String tokenAdmin, String nombre, String correo) throws Exception {
        var r = personal("POST", "/v1/admin/estudiantes", tokenAdmin, "{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\"}");
        assertEquals(201, r.statusCode(), r.body());
        var n = json.readTree(r.body());
        return new UUID[] {UUID.fromString(n.get("idUsuario").asString()), UUID.fromString(n.get("idEstudiante").asString())};
    }

    private static String linea(String csv, UUID estudiante) {
        return csv.lines().filter(l -> l.contains(estudiante.toString())).findFirst()
                .orElseThrow(() -> new AssertionError("no CSV row for " + estudiante + ":\n" + csv));
    }

    private Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalog.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }

    private String loginPersonal(String correo) throws Exception {
        var r = personal("POST", "/v1/personal/autenticar", null,
                "{\"correo\":\"" + correo + "\",\"password\":\"" + RecorridoParticipacionQa.FIXTURE + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return json.readTree(r.body()).get("accessToken").asString();
    }

    private HttpResponse<String> personal(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(uri(ruta)).method(metodo,
                cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) { b.header("Content-Type", "application/json"); }
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String ruta) { return URI.create("http://localhost:" + port + "/api" + ruta); }
}
