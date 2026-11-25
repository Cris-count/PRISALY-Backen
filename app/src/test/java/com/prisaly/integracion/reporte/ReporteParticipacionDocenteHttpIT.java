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
        assertEquals("id_sesion,espacio_academico,estado_sesion,id_estudiante,nombre_estudiante,correo_estudiante,"
                + "estado_convocatoria,situacion,estado_participacion,intentos,intentos_finalizados,"
                + "ultimo_intento_numero,ultimo_intento_estado,ultimo_intento_inicio,ultimo_intento_fin,"
                + "ultimo_intento_motivo,calificacion,intento_calificado,estado_calificacion", regs.get(0));
        // Raw text: the formula is neutralized with ' and the cell is RFC 4180 quoted.
        assertTrue(texto(r).contains(",\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\","), texto(r));
        assertTrue(texto(r).contains(s.id() + ",\"'=Espacio, \"\"G1\"\"\",EnCurso,"), texto(r));

        var hecha = fila(regs, "'=HYPERLINK(\"http://x\",\"y\")");
        assertEquals(19, hecha.size());
        assertEquals(List.of("Autorizado", "FINALIZADO", "Pendiente", "1", "1", "1", "Finalizado"), hecha.subList(6, 13));
        assertEquals("ENTREGA_ESTUDIANTE", hecha.get(15));
        assertFalse(hecha.get(13).isEmpty() || hecha.get(14).isEmpty(), "ISO-8601 start and end");
        assertTrue(hecha.get(13).endsWith("Z"));
        assertEquals(List.of("", "", "NO_HABILITADA"), hecha.subList(16, 19), "M6 off: the grade is not read");
        assertFalse(texto(r).contains("4.50"));

        var curso = fila(regs, "Ana, \"en curso\"");
        assertEquals(List.of("EN_CURSO", "Pendiente", "1", "0", "1", "EnCurso"), curso.subList(7, 13));
        assertEquals("", curso.get(14));

        var sin = fila(regs, "Zoe Sin Intento");
        assertEquals(List.of("SIN_PARTICIPACION", "", "0", "0", ""), sin.subList(7, 12));
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
        assertEquals("AUSENTE", fila(regs, "Ausente Uno").get(7));
        assertEquals("Finalizado", fila(regs, "Ausente Uno").get(2));
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
