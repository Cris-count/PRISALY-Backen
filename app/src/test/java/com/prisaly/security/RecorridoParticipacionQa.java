package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m7.infrastructure.ConfiguracionCodigoAccesoM7;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * QA fixture shared by the M4-M5 journey ITs (DC-BE-CIERRE-FUNCIONAL-09). It reuses the existing DD-02 B QA mechanism
 * ({@link AccesoEstudianteQa}: candidate schema V014-V016 ONLY in this context, disposable random keys) and delivers the
 * access code through M7 over SMTP to a Mailpit capture mailbox, exactly as {@code AccesoEstudianteBHttpIT}.
 * Nothing here touches production configuration.
 */
final class RecorridoParticipacionQa {
    static final String FIXTURE = "Fixture#Recorrido09";
    final PostgreSQLContainer postgres;
    final GenericContainer<?> mailpit;

    @SuppressWarnings("resource")
    RecorridoParticipacionQa() {
        postgres = new PostgreSQLContainer("postgres:16.15");
        mailpit = new GenericContainer<>("axllent/mailpit:v1.31.2").withExposedPorts(1025, 8025)
                .waitingFor(Wait.forListeningPorts(1025, 8025));
        postgres.start();
        mailpit.start();
    }

    void propiedades(DynamicPropertyRegistry p, boolean publicarPost) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        AccesoEstudianteQa.propiedades(p);
        p.add("prisaly.m7.worker.enabled", () -> "false");
        p.add("prisaly.m7.codigo-acceso.worker.enabled", () -> "true");
        p.add("prisaly.m7.codigo-acceso.worker.lote", () -> "20");
        p.add("prisaly.m7.codigo-acceso.worker.intervalo-ms", () -> "3600000");
        p.add("prisaly.m7.correo.remitente", () -> "no-reply@prisaly.test");
        p.add("spring.mail.host", mailpit::getHost);
        p.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        // QA only: the shipped value is false (application.yml).
        p.add("prisaly.m5.inicio-finalizacion.publicacion.enabled", () -> String.valueOf(publicarPost));
    }

    /** Active student account (staff password irrelevant to DD-02 B). Returns idEstudiante. */
    static UUID estudiante(IdentityCatalogService catalog, JdbcTemplate jdbc, String correo) {
        String rol = "Est09-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Fixture QA recorrido 09");
        UUID usuario = catalog.registerUser("Estudiante QA 09", correo, FIXTURE, rol).getIdUsuario();
        UUID estudiante = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);
        return estudiante;
    }

    /** Published case with 1 scenario, 2 questions (visible, Lista) and 3 options. Returns {caso, docente, p1, p2, o1a, o1b, o2a}. */
    static UUID[] caso(IdentityCatalogService catalog, JdbcTemplate jdbc) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole("Doc09-" + s, "Fixture");
        UUID docente = catalog.registerUser("Docente QA 09", "doc09." + s + "@prisaly.test", FIXTURE, "Doc09-" + s).getIdUsuario();
        UUID caso = UUID.randomUUID(), escenario = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID(),
                o1a = UUID.randomUUID(), o1b = UUID.randomUUID(), o2a = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"historia\",\"tiempoEstimadoSegundos\",\"estado\") VALUES (?,?,?,?,600,'Publicado')",
                caso, docente, "Caso recorrido 09 " + s, "Historia de prueba");
        jdbc.update("INSERT INTO \"Escenario\" (\"idEscenario\",\"idCaso\",\"titulo\",\"orden\") VALUES (?,?,'Escenario QA',1)", escenario, caso);
        jdbc.update("INSERT INTO \"Pregunta\" (\"idPregunta\",\"idEscenario\",\"enunciado\",\"estadoEdicion\",\"orden\") VALUES (?,?,'P1','Lista',1),(?,?,'P2','Lista',2)",
                p1, escenario, p2, escenario);
        jdbc.update("INSERT INTO \"OpcionRespuesta\" (\"idOpcion\",\"idPregunta\",\"texto\",\"orden\",\"esCorrecta\") VALUES (?,?,'1A',1,false),(?,?,'1B',2,true),(?,?,'2A',1,true)",
                o1a, p1, o1b, p1, o2a, p2);
        return new UUID[] {caso, docente, p1, p2, o1a, o1b, o2a};
    }

    /** Session over the case; {@code enCurso} also sets fechaInicioReal from the PostgreSQL clock. */
    static UUID sesion(JdbcTemplate jdbc, UUID caso, UUID docente, boolean enCurso, int maximoSegundos) {
        UUID sesion = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "Sesion" ("idSesion","idCaso","idDocenteProgramador","nombreEspacioAcademico",
                  "inicioPrevisto","finPrevisto","tiempoMaximoParticipacionSegundos")
                VALUES (?,?,?,'Espacio QA 09',now()-interval '1 hour',now()+interval '1 hour',?)""",
                sesion, caso, docente, maximoSegundos);
        if (enCurso) {
            jdbc.update("UPDATE \"Sesion\" SET \"estado\"='EnCurso', \"fechaInicioReal\"=clock_timestamp() WHERE \"idSesion\"=?", sesion);
        }
        return sesion;
    }

    static UUID convocar(JdbcTemplate jdbc, UUID sesion, UUID estudiante) {
        UUID conv = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)", conv, sesion, estudiante);
        return conv;
    }

    /** DD-02 B login through Mailpit: solicitar → M7 worker → SMTP → capture mailbox → validar. Returns the cookie pair. */
    String login(HttpClient http, int port, ObjectMapper json, ConfiguracionCodigoAccesoM7.TrabajoEntregas worker,
            String correo) throws Exception {
        var solicitud = enviar(http, port, "POST", "/v1/acceso-estudiante/solicitar", "{\"correo\":\"" + correo + "\"}", null);
        assertEquals(202, solicitud.statusCode(), solicitud.body());
        String challenge = json.readTree(solicitud.body()).get("challengeId").asText();
        assertTrue(worker.ejecutarAhora() >= 1, "M7 delivered the code over SMTP");
        String codigo = codigoDelBuzon(http, json, correo);
        var validacion = enviar(http, port, "POST", "/v1/acceso-estudiante/validar",
                "{\"challengeId\":\"" + challenge + "\",\"codigo\":\"" + codigo + "\"}", null);
        assertEquals(204, validacion.statusCode(), validacion.body());
        String setCookie = validacion.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Strict") && setCookie.contains("Path=/api"), setCookie);
        return setCookie.split(";", 2)[0];
    }

    private String codigoDelBuzon(HttpClient http, ObjectMapper json, String correo) throws Exception {
        String base = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
        for (int i = 0; i < 50; i++) {
            JsonNode lista = json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/search?query=to:" + correo)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            if (lista.get("messages") != null && lista.get("messages").size() > 0) {
                String id = lista.get("messages").get(0).get("ID").asText();
                String texto = json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/message/" + id)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body()).get("Text").asText();
                Matcher codigo = Pattern.compile("\\b(\\d{8})\\b").matcher(texto);
                assertTrue(codigo.find(), "code in the body");
                return codigo.group(1);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No message in the QA capture mailbox for the test recipient");
    }

    /** Same-origin browser-like request; the student cookie is the only identity sent. */
    static HttpResponse<String> enviar(HttpClient http, int port, String metodo, String ruta, String cuerpo, String cookie)
            throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + ruta))
                .header("Origin", AccesoEstudianteQa.ORIGIN).header("Content-Type", "application/json")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cookie != null) { b.header("Cookie", cookie); }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
