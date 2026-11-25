package com.prisaly.integracion.reporte;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m1.application.service.IdentityCatalogService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 — shared fictitious fixture ({@code @prisaly.test}) for the RF-16 candidate report ITs.
 * Data is inserted with the official schema V001–V009/V013 and its triggers; no migration is added.
 */
abstract class ReporteParticipacionHttpFixture {
    static final String CLAVE = "Fixture#Reporte-72";

    @LocalServerPort int port;
    @Autowired IdentityCatalogService catalogo;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient http = HttpClient.newHttpClient();

    record Docente(UUID id, String token) { }

    record Sesion(UUID id, UUID caso, UUID rubrica) { }

    // ─── Personnel ────────────────────────────────────────────────────────────────────────────────────────────

    UUID administrador() {
        if (!catalogo.existeRolAdministrador()) {
            catalogo.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture");
        }
        return catalogo.registerUser("Admin 72", UUID.randomUUID() + "@prisaly.test", CLAVE,
                IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }

    Docente docente(UUID admin, boolean gestionarSesion) throws Exception {
        String rol = "R72-" + sufijo();
        catalogo.createRole(rol, "Docente fixture 72");
        var u = catalogo.registerUser("Docente 72", "doc72." + sufijo() + "@prisaly.test", CLAVE, rol);
        if (gestionarSesion) {
            catalogo.assignPermission(u.getIdUsuario(), permiso("GESTIONAR_SESION"), admin);
        }
        var login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"correo\":\"" + u.getCorreo() + "\",\"password\":\"" + CLAVE + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return new Docente(u.getIdUsuario(), json.readTree(login.body()).get("accessToken").asString());
    }

    Integer permiso(String codigo) {
        var e = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        return e.isEmpty() ? catalogo.createPermission(codigo, codigo, null).getIdPermiso() : e.get(0);
    }

    // ─── Academic data ────────────────────────────────────────────────────────────────────────────────────────

    /** Published case with a rubric, then the session (programming locks the content, V007). */
    Sesion sesion(Docente d, String espacio, String estado) {
        UUID caso = UUID.randomUUID(), rubrica = UUID.randomUUID(), sesion = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Caso\" (\"idCaso\",\"idDocente\",\"nombre\",\"tiempoEstimadoSegundos\",\"estado\")"
                + " VALUES (?,?,?,600,'Publicado')", caso, d.id(), "Caso 72 " + sufijo());
        jdbc.update("INSERT INTO \"Rubrica\" (\"idRubrica\",\"idCaso\",\"nombre\",\"definicionEvaluacion\")"
                + " VALUES (?,?,?,'{\"fixture\":\"72\"}')", rubrica, caso, "Rubrica 72 " + sufijo());
        jdbc.update("INSERT INTO \"Sesion\" (\"idSesion\",\"idCaso\",\"idDocenteProgramador\",\"nombreEspacioAcademico\","
                + "\"inicioPrevisto\",\"finPrevisto\",\"tiempoMaximoParticipacionSegundos\",\"estado\") VALUES (?,?,?,?,"
                + "now()-interval '3 hours',now()+interval '3 hours',3600,?)", sesion, caso, d.id(), espacio, estado);
        return new Sesion(sesion, caso, rubrica);
    }

    /** Convoked student; returns the convocatoria id. */
    UUID convocar(Sesion s, String nombre) {
        int rol = Math.abs(UUID.randomUUID().hashCode() % 900_000_000) + 100_000;
        UUID usuario = UUID.randomUUID(), estudiante = UUID.randomUUID(), convocatoria = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES (?,?,'Fixture 72')", rol,
                "R72-est-" + sufijo());
        jdbc.update("INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES (?,?,?,?)",
                usuario, rol, nombre, "est72." + sufijo() + "@prisaly.test");
        jdbc.update("INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)", estudiante, usuario);
        jdbc.update("INSERT INTO \"Convocatoria\" (\"idConvocatoria\",\"idSesion\",\"idEstudiante\") VALUES (?,?,?)",
                convocatoria, s.id(), estudiante);
        return convocatoria;
    }

    UUID participacion(UUID convocatoria) {
        UUID p = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Participacion\" (\"idParticipacion\",\"idConvocatoria\") VALUES (?,?)", p, convocatoria);
        return p;
    }

    /** Attempt {@code numero}; numbers above 1 consume a fresh repetition authorization (V005/V007 triggers). */
    UUID intento(UUID participacion, int numero, boolean finalizado, UUID docente) {
        UUID intento = UUID.randomUUID(), autorizacion = null;
        if (numero > 1) {
            autorizacion = UUID.randomUUID();
            jdbc.update("INSERT INTO \"AutorizacionRepeticion\" (\"idAutorizacion\",\"idParticipacion\","
                    + "\"idDocenteAutorizador\",\"fechaAutorizacion\") VALUES (?,?,?,now()-interval '1 minute')",
                    autorizacion, participacion, docente);
        }
        jdbc.update("INSERT INTO \"Intento\" (\"idIntento\",\"idParticipacion\",\"numeroIntento\",\"inicio\","
                + "\"limiteTemporal\",\"estado\",\"idAutorizacion\") VALUES (?,?,?,now(),now()+interval '1 hour',"
                + "'EnCurso',?)", intento, participacion, numero, autorizacion);
        if (finalizado) {
            jdbc.update("UPDATE \"Intento\" SET \"estado\"='Finalizado', \"fin\"=clock_timestamp(),"
                    + " \"motivoFinalizacion\"='ENTREGA_ESTUDIANTE' WHERE \"idIntento\"=?", intento);
        }
        return intento;
    }

    void calificar(Sesion s, UUID intento, String valor) {
        jdbc.update("INSERT INTO \"Calificacion\" (\"idCalificacion\",\"idIntento\",\"idRubrica\",\"valor\","
                + "\"detalleEvaluacion\",\"versionPolitica\") VALUES (gen_random_uuid(),?,?,?::numeric,"
                + "'{\"fixture\":\"72\"}'::jsonb,'S1')", intento, s.rubrica(), valor);
    }

    /** DC-92: N2 release of an existing grade by the case owner (fixture insert; the V019 triggers still apply). */
    void liberar(UUID intento, UUID docente) {
        jdbc.update("INSERT INTO \"LiberacionCalificacion\" (\"idCalificacion\",\"idDocente\") SELECT \"idCalificacion\", ?"
                + " FROM \"Calificacion\" WHERE \"idIntento\"=?", docente, intento);
    }

    /** DC-92 candidate schema (integral + V021-V023) and its M4 contract, for the calling test context only. */
    static void esquemaDc92(org.springframework.test.context.DynamicPropertyRegistry r) {
        java.nio.file.Path base = java.nio.file.Path.of("src/main/resources/db").toAbsolutePath();
        r.add("spring.flyway.locations", () -> "filesystem:" + base.resolve("migration") + ",filesystem:"
                + base.resolve("dd02b-propuesta") + ",filesystem:" + base.resolve("m6-propuesta") + ",filesystem:"
                + base.resolve("d05-propuesta") + ",filesystem:" + base.resolve("dc92-propuesta"));
        r.add("prisaly.m4.schema-contract", () -> "bd0052-v013-integral-dc92");
    }

    // ─── HTTP ─────────────────────────────────────────────────────────────────────────────────────────────────

    HttpResponse<byte[]> descargar(String token, Object sesion) throws Exception {
        var b = HttpRequest.newBuilder(uri("/v1/docente/sesiones/" + sesion + "/reporte-participacion")).GET();
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    static String texto(HttpResponse<byte[]> r) {
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    /** Records without BOM; RFC 4180 CRLF separators (no fixture value contains a line break). */
    static List<String> registros(HttpResponse<byte[]> r) {
        String t = texto(r);
        assertTrue(t.startsWith("﻿"), "UTF-8 BOM");
        assertTrue(t.endsWith("\r\n"));
        return List.of(t.substring(1).split("\r\n"));
    }

    /** Record whose student name (column 5) equals {@code nombreCsv}, split on commas outside quotes. */
    static List<String> fila(List<String> registros, String nombreCsv) {
        return registros.stream().skip(1).map(ReporteParticipacionHttpFixture::celdas)
                .filter(c -> c.get(1).equals(nombreCsv)).findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + nombreCsv + " in " + registros));
    }

    static List<String> celdas(String registro) {
        var celdas = new java.util.ArrayList<String>();
        var actual = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < registro.length(); i++) {
            char ch = registro.charAt(i);
            if (comillas && ch == '"' && i + 1 < registro.length() && registro.charAt(i + 1) == '"') {
                actual.append('"');
                i++;
            } else if (ch == '"') {
                comillas = !comillas;
            } else if (ch == ',' && !comillas) {
                celdas.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        celdas.add(actual.toString());
        return celdas;
    }

    URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api" + path);
    }

    static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
