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
 * DC-92 DD-11 (supersedes the DC-72 "last evaluated attempt" column): the CSV shows ONLY a released grade (N2) of the
 * considered attempt — the teacher's selection (DD-04) or the single attempt — and the DD-13 / RF-21 states. It never
 * evaluates nor writes. Fictitious data; DC-92 candidate schema in a disposable container.
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
        esquemaDc92(r);
    }

    @Test
    void soloLaNotaPublicadaDelIntentoConsideradoYLosEstadosRatificados() throws Exception {
        UUID admin = administrador();
        Docente a = docente(admin, true);
        Sesion s = sesion(a, "Espacio DD-11", "Finalizado");

        UUID publicada = participacion(convocar(s, "Publicada"));
        UUID i1 = intento(publicada, 1, true, a.id());
        calificar(s, i1, "4.50");
        liberar(i1, a.id());

        UUID oculta = participacion(convocar(s, "No Publicada"));
        calificar(s, intento(oculta, 1, true, a.id()), "3.00");

        UUID dos = participacion(convocar(s, "Dos Sin Seleccion"));
        UUID d1 = intento(dos, 1, true, a.id());
        UUID d2 = intento(dos, 2, true, a.id());
        calificar(s, d1, "2.00");
        calificar(s, d2, "5.00");
        liberar(d1, a.id());
        liberar(d2, a.id());

        UUID seleccion = participacion(convocar(s, "Dos Con Seleccion"));
        UUID s1 = intento(seleccion, 1, true, a.id());
        UUID s2 = intento(seleccion, 2, true, a.id());
        calificar(s, s1, "3.25");
        calificar(s, s2, "1.00");
        liberar(s1, a.id());
        liberar(s2, a.id());
        jdbc.update("INSERT INTO \"SeleccionIntento\" (\"idSeleccion\",\"idParticipacion\",\"idIntento\",\"idDocente\",\"motivo\")"
                + " VALUES (gen_random_uuid(),?,?,?,'Intento completo')", seleccion, s1, a.id());

        convocar(s, "Ausente Sin Clasificar");
        UUID justificada = convocar(s, "Ausente Justificada");
        jdbc.update("INSERT INTO \"ClasificacionAusencia\" (\"idClasificacion\",\"idConvocatoria\",\"estado\",\"idDocente\",\"motivo\")"
                + " VALUES (gen_random_uuid(),?,'AUSENCIA_JUSTIFICADA',?,'Incapacidad medica')", justificada, a.id());
        UUID retirada = convocar(s, "Retirada");
        jdbc.update("UPDATE \"Convocatoria\" SET \"fechaRetiro\"=now(), \"estadoAutorizacion\"='Retirado' WHERE \"idConvocatoria\"=?", retirada);

        String antes = jdbc.queryForObject("SELECT (SELECT count(*) FROM \"Calificacion\") || '/' || (SELECT count(*) FROM"
                + " \"LiberacionCalificacion\")", String.class);
        var r = descargar(a.token(), s.id());
        assertEquals(200, r.statusCode(), texto(r));
        var regs = registros(r);
        assertEquals(8, regs.size(), regs.toString());

        assertEquals(List.of("PARTICIPACION_FINALIZADA", "1", "PUBLICADA", "4.50"), fila(regs, "Publicada").subList(6, 10));
        assertEquals(List.of("PARTICIPACION_FINALIZADA", "1", "EVALUADA_NO_PUBLICADA", ""), fila(regs, "No Publicada").subList(6, 10));
        assertEquals(List.of("PARTICIPACION_FINALIZADA", "2", "SELECCION_PENDIENTE", ""), fila(regs, "Dos Sin Seleccion").subList(6, 10),
                "DD-04: with two attempts only the teacher's selection counts");
        assertEquals(List.of("PARTICIPACION_FINALIZADA", "2", "PUBLICADA", "3.25"), fila(regs, "Dos Con Seleccion").subList(6, 10));
        assertEquals(List.of("AUSENTE_PENDIENTE_REVISION", "0", "SIN_INTENTOS", ""), fila(regs, "Ausente Sin Clasificar").subList(6, 10));
        assertEquals(List.of("AUSENCIA_JUSTIFICADA", "0", "SIN_INTENTOS", ""), fila(regs, "Ausente Justificada").subList(6, 10),
                "DD-13: an absence never becomes a zero");
        assertEquals(List.of("Retirado", "RETIRADO"), fila(regs, "Retirada").subList(5, 7));
        String t = texto(r);
        assertFalse(t.contains("3.00") || t.contains("5.00") || t.contains("2.00") || t.contains("1.00"), t);
        assertEquals(antes, jdbc.queryForObject("SELECT (SELECT count(*) FROM \"Calificacion\") || '/' || (SELECT count(*) FROM"
                + " \"LiberacionCalificacion\")", String.class), "the report never writes");
    }

    @Test
    void sigueExigiendoPropiedadDeLaSesion() throws Exception {
        UUID admin = administrador();
        Docente a = docente(admin, true), b = docente(admin, true);
        Sesion s = sesion(a, "Ajena publicada", "Finalizado");
        UUID i = intento(participacion(convocar(s, "Nota Privada")), 1, true, a.id());
        calificar(s, i, "5.00");
        liberar(i, a.id());
        var r = descargar(b.token(), s.id());
        assertEquals(403, r.statusCode(), texto(r));
        assertFalse(texto(r).contains("5.00") || texto(r).contains("Nota Privada"));
    }
}
