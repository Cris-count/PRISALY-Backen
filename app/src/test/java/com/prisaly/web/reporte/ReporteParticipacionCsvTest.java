package com.prisaly.web.reporte;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.domain.model.EstadoSesion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DC-BE-CIERRE-FUNCIONAL-72: RFC 4180 escaping, formula-injection neutralization and derived columns. */
class ReporteParticipacionCsvTest {
    static final UUID SESION = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID EST = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void escapaSegunRfc4180() {
        assertEquals("", CsvRfc4180.celda(null));
        assertEquals("", CsvRfc4180.celda(""));
        assertEquals("simple", CsvRfc4180.celda("simple"));
        assertEquals("\"a,b\"", CsvRfc4180.celda("a,b"));
        assertEquals("\"di \"\"hola\"\"\"", CsvRfc4180.celda("di \"hola\""));
        assertEquals("\"linea1\nlinea2\"", CsvRfc4180.celda("linea1\nlinea2"));
        assertEquals("\"a\r\nb\"", CsvRfc4180.celda("a\r\nb"));
        assertEquals("Ñandú José", CsvRfc4180.celda("Ñandú José"));
    }

    @Test
    void neutralizaFormulasEnTodasLasCeldasPeligrosas() {
        assertEquals("'=SUM(A1:A2)", CsvRfc4180.celda("=SUM(A1:A2)"));
        assertEquals("'+57 300", CsvRfc4180.celda("+57 300"));
        assertEquals("'-1", CsvRfc4180.celda("-1"));
        assertEquals("'@cmd", CsvRfc4180.celda("@cmd"));
        assertEquals("'\tx", CsvRfc4180.celda("\tx"));
        assertEquals("\"'\rx\"", CsvRfc4180.celda("\rx"), "neutralized and still quoted because of CR");
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"", CsvRfc4180.celda("=HYPERLINK(\"http://x\",\"y\")"));
        assertEquals("a=b", CsvRfc4180.celda("a=b"), "only the leading character matters");
    }

    @Test
    void cabeceraFijaBomYFinDeRegistroCrlf() {
        var csv = new String(ReporteParticipacionCsv.generar(sesion(EstadoSesion.EN_CURSO), List.of(), false),
                StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿"));
        assertEquals("﻿" + String.join(",", ReporteParticipacionCsv.CABECERA) + "\r\n", csv);
        assertEquals(19, ReporteParticipacionCsv.CABECERA.size());
        assertEquals("id_sesion", ReporteParticipacionCsv.CABECERA.get(0));
        assertEquals("estado_calificacion", ReporteParticipacionCsv.CABECERA.get(18));
    }

    @Test
    void situacionDerivadaDeHechosPersistidos() {
        assertEquals("SIN_PARTICIPACION", ReporteParticipacionCsv.situacion(EstadoSesion.EN_CURSO, fila(0, null, null)));
        assertEquals("SIN_PARTICIPACION", ReporteParticipacionCsv.situacion(EstadoSesion.SIN_INICIAR, fila(0, null, null)));
        assertEquals("AUSENTE", ReporteParticipacionCsv.situacion(EstadoSesion.FINALIZADO, fila(0, null, null)));
        assertEquals("EN_CURSO", ReporteParticipacionCsv.situacion(EstadoSesion.EN_CURSO, fila(1, "EnCurso", null)));
        assertEquals("FINALIZADO", ReporteParticipacionCsv.situacion(EstadoSesion.FINALIZADO, fila(2, "Finalizado", null)));
        var retirado = new ReporteParticipacionCsv.Fila(EST, "N", "c@x.test", "Autorizado", Instant.EPOCH, null, 0, 0,
                null, null, null, null, null, null, null);
        assertEquals("RETIRADO", ReporteParticipacionCsv.situacion(EstadoSesion.FINALIZADO, retirado));
    }

    @Test
    void calificacionSoloSiM6EstaHabilitado() {
        var f = fila(1, "Finalizado", new BigDecimal("4.50"));
        var apagado = ReporteParticipacionCsv.celdas(sesion(EstadoSesion.FINALIZADO), f, false);
        assertEquals(19, apagado.size());
        assertNull(apagado.get(16));
        assertNull(apagado.get(17));
        assertEquals("NO_HABILITADA", apagado.get(18));
        var encendido = ReporteParticipacionCsv.celdas(sesion(EstadoSesion.FINALIZADO), f, true);
        assertEquals(List.of("4.50", "1", "CALIFICADA"), encendido.subList(16, 19));
        var sinNota = ReporteParticipacionCsv.celdas(sesion(EstadoSesion.FINALIZADO), fila(1, "Finalizado", null), true);
        assertEquals("SIN_CALIFICACION", sinNota.get(18));
    }

    @Test
    void registroCompletoConDatosHostiles() {
        var f = new ReporteParticipacionCsv.Fila(EST, "=cmd|' /C calc'!A0", "a,\"b\"@x.test", "Autorizado", null,
                "Pendiente", 1, 1, 1, "Finalizado", Instant.parse("2026-11-24T10:00:00Z"),
                Instant.parse("2026-11-24T10:30:00Z"), "ENTREGA_ESTUDIANTE", null, null);
        var csv = new String(ReporteParticipacionCsv.generar(
                new ReporteParticipacionCsv.Sesion(SESION, "@Espacio, grupo \"A\"", EstadoSesion.FINALIZADO),
                List.of(f), false), StandardCharsets.UTF_8);
        String[] lineas = csv.split("\r\n");
        assertEquals(2, lineas.length);
        assertEquals(SESION + ",\"'@Espacio, grupo \"\"A\"\"\",Finalizado," + EST + ",'=cmd|' /C calc'!A0,"
                + "\"a,\"\"b\"\"@x.test\",Autorizado,FINALIZADO,Pendiente,1,1,1,Finalizado,2026-11-24T10:00:00Z,"
                + "2026-11-24T10:30:00Z,ENTREGA_ESTUDIANTE,,,NO_HABILITADA", lineas[1]);
    }

    private static ReporteParticipacionCsv.Sesion sesion(EstadoSesion estado) {
        return new ReporteParticipacionCsv.Sesion(SESION, "Espacio", estado);
    }

    private static ReporteParticipacionCsv.Fila fila(int intentos, String ultimoEstado, BigDecimal nota) {
        return new ReporteParticipacionCsv.Fila(EST, "Nombre", "n@x.test", "Autorizado", null,
                intentos == 0 ? null : "Pendiente", intentos, "Finalizado".equals(ultimoEstado) ? intentos : 0,
                intentos == 0 ? null : intentos, ultimoEstado, null, null, null, nota, nota == null ? null : 1);
    }
}
