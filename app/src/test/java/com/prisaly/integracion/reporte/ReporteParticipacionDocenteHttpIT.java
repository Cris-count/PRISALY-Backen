package com.prisaly.integracion.reporte;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 — CANDIDATE RF-16 report over the real APP and PostgreSQL 16.15. Only
 * {@code prisaly.m7.reportes.participacion.enabled} is turned on (QA only); M6 keeps its shipped value (off), so the
 * grade is never read. Fictitious data.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReporteParticipacionDocenteHttpIT extends ReporteParticipacionHttpFixture {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("prisaly.m7.reportes.participacion.enabled", () -> "true"); // QA only
        ReporteParticipacionHttpFixture.esquemaDc92(r); // DC-92: DD-11 reads the DC-92 candidate schema
    }

    UUID admin;

    @BeforeEach
    void preparar() {
        admin = administrador();
    }

    @Test
    void elPropietarioDescargaCsvConCabeceraFijaEscapeYProteccionDeFormulas() throws Exception {
        Docente a = docente(admin, true);
        Sesion s = sesion(a, "=Espacio, \"G1\"", "EnCurso");
        UUID finalizo = participacion(convocar(s, "=HYPERLINK(\"http://x\",\"y\")"));
        calificar(s, intento(finalizo, 1, true, a.id()), "4.50");
        intento(participacion(convocar(s, "Ana, \"en curso\"")), 1, false, a.id());
        convocar(s, "Zoe Sin Intento");

        var r = descargar(a.token(), s.id());
        assertEquals(200, r.statusCode(), texto(r));
        assertEquals("text/csv;charset=UTF-8", r.headers().firstValue("Content-Type").orElse("").replace(" ", ""));
        assertEquals("attachment; filename=\"participacion-sesion-" + s.id() + ".csv\"",
                r.headers().firstValue("Content-Disposition").orElse(""));
        assertTrue(r.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(""));

        List<String> regs = registros(r);
        assertEquals(4, regs.size(), regs.toString());
        // DC-92 DD-11: the ten ratified fields, no email.
        assertEquals("id_estudiante,nombre_estudiante,caso,sesion,fecha_sesion,estado_convocatoria,"
                + "estado_participacion,numero_intentos,estado_evaluacion,calificacion_publicada", regs.get(0));
        // Raw text: the formula is neutralized with ' and the cell is RFC 4180 quoted.
        assertTrue(texto(r).contains(",\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\","), texto(r));
        assertTrue(texto(r).contains(",\"'=Espacio, \"\"G1\"\"\","), texto(r));
        assertFalse(texto(r).contains("@prisaly.test"), "DD-11: no email in the report");

        var hecha = fila(regs, "'=HYPERLINK(\"http://x\",\"y\")");
        assertEquals(10, hecha.size());
        assertEquals(List.of("Autorizado", "PARTICIPACION_FINALIZADA", "1", "EVALUADA_NO_PUBLICADA", ""), hecha.subList(5, 10),
                "graded but not released: never shown");
        assertTrue(hecha.get(4).endsWith("Z"), "ISO-8601 session date");
        assertFalse(texto(r).contains("4.50"));

        var curso = fila(regs, "Ana, \"en curso\"");
        assertEquals(List.of("PARTICIPACION_INICIADA", "1", "SIN_EVALUAR", ""), curso.subList(6, 10));

        var sin = fila(regs, "Zoe Sin Intento");
        assertEquals(List.of("CONVOCADO", "0", "SIN_INTENTOS", ""), sin.subList(6, 10));
    }

    @Test
    void sesionFinalizadaMarcaAusentesYSoloIncluyeLaSesionPedida() throws Exception {
        Docente a = docente(admin, true);
        Sesion fin = sesion(a, "Espacio cerrado", "Finalizado");
        Sesion otra = sesion(a, "Otra sesion", "EnCurso");
        convocar(fin, "Ausente Uno");
        convocar(otra, "De Otra Sesion");
        var regs = registros(descargar(a.token(), fin.id()));
        assertEquals(2, regs.size(), regs.toString());
        assertEquals("AUSENTE_PENDIENTE_REVISION", fila(regs, "Ausente Uno").get(6), "DD-13: no automatic zero");
        assertEquals("SIN_INTENTOS", fila(regs, "Ausente Uno").get(8));
        assertFalse(String.join("\n", regs).contains("De Otra Sesion"));

        Sesion vacia = sesion(a, "Sin convocados", "SinIniciar");
        var soloCabecera = registros(descargar(a.token(), vacia.id()));
        assertEquals(1, soloCabecera.size(), "header only");
    }

    @Test
    void ajenoEs403SinPermisoEs403InexistenteEs404SinTokenEs401YIdInvalidoEs400() throws Exception {
        Docente a = docente(admin, true), b = docente(admin, true), sinPermiso = docente(admin, false);
        Sesion s = sesion(a, "Privada", "EnCurso");
        convocar(s, "Dato Privado");

        var ajeno = descargar(b.token(), s.id());
        assertEquals(403, ajeno.statusCode(), texto(ajeno));
        assertTrue(texto(ajeno).contains("\"code\":\"M4-SESION-AJENA\""), texto(ajeno));
        assertTrue(ajeno.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));

        var sp = descargar(sinPermiso.token(), s.id());
        assertEquals(403, sp.statusCode(), texto(sp));
        assertTrue(texto(sp).contains("\"code\":\"M4-SIN-PERMISO\""), texto(sp));

        var inexistente = descargar(a.token(), UUID.randomUUID());
        assertEquals(404, inexistente.statusCode(), texto(inexistente));
        assertTrue(texto(inexistente).contains("\"code\":\"M4-SESION-NO-ENCONTRADA\""), texto(inexistente));

        var anonimo = descargar(null, s.id());
        assertEquals(401, anonimo.statusCode(), texto(anonimo));
        assertTrue(texto(anonimo).contains("UNAUTHENTICATED"));

        var invalido = descargar(a.token(), "no-es-uuid");
        assertEquals(400, invalido.statusCode(), texto(invalido));
        assertTrue(texto(invalido).contains("M4-SOLICITUD-INVALIDA"), texto(invalido));

        for (var r : List.of(ajeno, sp, inexistente, anonimo, invalido)) {
            assertFalse(texto(r).contains("Dato Privado"), "no data leaks in errors");
            assertTrue(r.headers().firstValue("Content-Disposition").isEmpty());
        }
    }

    @Test
    void laRevocacionDeGestionarSesionSurteEfectoEnLaSiguientePeticion() throws Exception {
        Docente a = docente(admin, true);
        Sesion s = sesion(a, "Revocable", "EnCurso");
        assertEquals(200, descargar(a.token(), s.id()).statusCode());
        catalogo.disablePermissionAssignment(a.id(), permiso("GESTIONAR_SESION"), admin);
        assertEquals(403, descargar(a.token(), s.id()).statusCode());
    }

    @Test
    void elReporteNoEscribeNada() throws Exception {
        Docente a = docente(admin, true);
        Sesion s = sesion(a, "Solo lectura", "EnCurso");
        intento(participacion(convocar(s, "Lector")), 1, true, a.id());
        String sql = "SELECT (SELECT count(*) FROM \"Participacion\") || '/' || (SELECT count(*) FROM \"Intento\")"
                + " || '/' || (SELECT count(*) FROM \"Calificacion\") || '/' || (SELECT count(*) FROM \"Aviso\")"
                + " || '/' || (SELECT count(*) FROM \"EnvioCorreo\")";
        String antes = jdbc.queryForObject(sql, String.class);
        assertEquals(200, descargar(a.token(), s.id()).statusCode());
        assertEquals(antes, jdbc.queryForObject(sql, String.class));
    }
}
