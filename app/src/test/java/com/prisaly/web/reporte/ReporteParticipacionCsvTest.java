package com.prisaly.web.reporte;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m4.domain.model.EstadoSesion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DC-72 escaping and formula neutralization (unchanged) + DC-92 DD-11 fixed columns. */
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
    void cabeceraFijaDd11BomYFinDeRegistroCrlf() {
        var csv = new String(ReporteParticipacionCsv.generar(java.util.List.of()), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("﻿" + String.join(",", ReporteParticipacionCsv.CABECERA) + "\r\n", csv);
        assertEquals(java.util.List.of("id_estudiante", "nombre_estudiante", "caso", "sesion", "fecha_sesion",
                "estado_convocatoria", "estado_participacion", "numero_intentos", "estado_evaluacion",
                "calificacion_publicada"), ReporteParticipacionCsv.CABECERA);
        assertFalse(ReporteParticipacionCsv.CABECERA.stream().anyMatch(c -> c.contains("correo")), "DD-11: no email");
    }

    @Test
    void soloLaCalificacionPublicadaApareceYLosDatosHostilesSeNeutralizan() {
        var publicada = fila("=cmd|' /C calc'!A0", "PARTICIPACION_FINALIZADA", "PUBLICADA", new java.math.BigDecimal("4.50"));
        var csv = new String(ReporteParticipacionCsv.generar(java.util.List.of(publicada)), java.nio.charset.StandardCharsets.UTF_8);
        String[] lineas = csv.split("\r\n");
        assertEquals(EST + ",'=cmd|' /C calc'!A0,\"Caso, \"\"A\"\"\",'@Espacio,2031-03-12T15:00:00Z,Autorizado,"
                + "PARTICIPACION_FINALIZADA,1,PUBLICADA,4.50", lineas[1]);
        var oculta = ReporteParticipacionCsv.celdas(fila("Ana", "PARTICIPACION_FINALIZADA", "EVALUADA_NO_PUBLICADA", null));
        assertNull(oculta.get(9), "an unreleased grade is never written");
    }

    private static com.prisaly.config.dc92.VistaParticipacionDc92.Fila fila(String nombre, String participacion,
            String evaluacion, java.math.BigDecimal nota) {
        return new com.prisaly.config.dc92.VistaParticipacionDc92.Fila(UUID.randomUUID(), EST, nombre, "Caso, \"A\"",
                "@Espacio", java.time.Instant.parse("2031-03-12T15:00:00Z"), "Autorizado", null, UUID.randomUUID(), null,
                1, participacion, UUID.randomUUID(), null, null, evaluacion, nota, null);
    }
}
