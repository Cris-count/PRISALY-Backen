package com.prisaly.integracion.reporte;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 — grade column of the RF-16 candidate when M6's own switch is on (QA only). Criterion
 * DD-04: the grade of the last evaluated attempt. The report only reads; it never evaluates. Fictitious data.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReporteParticipacionCalificacionHttpIT extends ReporteParticipacionHttpFixture {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("prisaly.m7.reportes.participacion.enabled", () -> "true"); // QA only
        r.add("prisaly.m6.evaluacion.enabled", () -> "true"); // QA only
    }

    @Test
    void calificacionDelUltimoIntentoEvaluadoSegunDd04() throws Exception {
        UUID admin = administrador();
        Docente a = docente(admin, true);
        Sesion s = sesion(a, "Espacio calificado", "Finalizado");

        UUID dos = participacion(convocar(s, "Dos Evaluados"));
        calificar(s, intento(dos, 1, true, a.id()), "3.00");
        calificar(s, intento(dos, 2, true, a.id()), "4.50");

        UUID segundoSinNota = participacion(convocar(s, "Segundo Sin Nota"));
        calificar(s, intento(segundoSinNota, 1, true, a.id()), "2.75");
        intento(segundoSinNota, 2, true, a.id());

        intento(participacion(convocar(s, "Sin Evaluar")), 1, true, a.id());
        convocar(s, "Ausente");

        String calificacionesAntes = jdbc.queryForObject("SELECT count(*)::text FROM \"Calificacion\"", String.class);
        var r = descargar(a.token(), s.id());
        assertEquals(200, r.statusCode(), texto(r));
        var regs = registros(r);
        assertEquals(5, regs.size(), regs.toString());

        var d = fila(regs, "Dos Evaluados");
        assertEquals(List.of("2", "2", "2", "Finalizado"), d.subList(9, 13));
        assertEquals(List.of("4.50", "2", "CALIFICADA"), d.subList(16, 19));

        var sn = fila(regs, "Segundo Sin Nota");
        assertEquals("2", sn.get(11), "last attempt is #2");
        assertEquals(List.of("2.75", "1", "CALIFICADA"), sn.subList(16, 19), "last EVALUATED attempt is #1");

        assertEquals(List.of("", "", "SIN_CALIFICACION"), fila(regs, "Sin Evaluar").subList(16, 19));
        var au = fila(regs, "Ausente");
        assertEquals("AUSENTE", au.get(7));
        assertEquals(List.of("", "", "SIN_CALIFICACION"), au.subList(16, 19));
        assertEquals(calificacionesAntes, jdbc.queryForObject("SELECT count(*)::text FROM \"Calificacion\"", String.class),
                "the report never evaluates");
    }

    @Test
    void conM6EncendidoSigueExigiendoPropiedadDeLaSesion() throws Exception {
        UUID admin = administrador();
        Docente a = docente(admin, true), b = docente(admin, true);
        Sesion s = sesion(a, "Ajena calificada", "Finalizado");
        calificar(s, intento(participacion(convocar(s, "Nota Privada")), 1, true, a.id()), "5.00");
        var r = descargar(b.token(), s.id());
        assertEquals(403, r.statusCode(), texto(r));
        assertFalse(texto(r).contains("5.00") || texto(r).contains("Nota Privada"));
    }
}
