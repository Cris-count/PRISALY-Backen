package com.prisaly.web.reporte;

import com.prisaly.config.dc92.VistaParticipacionDc92;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * RF-16 / DD-11 (DC-90, ratificada en DC-92): CSV de participación de UNA sesión, una fila por convocatoria. Columnas
 * FIJAS = los campos de DD-11: identificación y nombre del estudiante, caso, sesión, fecha, estado de convocatoria,
 * estado de participación (estados DD-13 y RETIRADO de RF-21), número de intentos, estado de evaluación y
 * calificación PUBLICADA (vacía salvo que el intento considerado tenga su nota liberada, N2). Sin correo ni otros datos
 * personales fuera de DD-11. Fechas ISO-8601 UTC. Escape RFC 4180 y neutralización de fórmulas en {@link CsvRfc4180}.
 */
final class ReporteParticipacionCsv {

    static final List<String> CABECERA = List.of(
            "id_estudiante", "nombre_estudiante", "caso", "sesion", "fecha_sesion", "estado_convocatoria",
            "estado_participacion", "numero_intentos", "estado_evaluacion", "calificacion_publicada");

    /** UTF-8 byte order mark so common spreadsheet software decodes accented names correctly. */
    static final String BOM = "﻿";

    private ReporteParticipacionCsv() {
    }

    static byte[] generar(List<VistaParticipacionDc92.Fila> filas) {
        var csv = new StringBuilder(BOM);
        CsvRfc4180.registro(csv, CABECERA);
        for (var f : filas) {
            CsvRfc4180.registro(csv, celdas(f));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static List<String> celdas(VistaParticipacionDc92.Fila f) {
        return Arrays.asList(texto(f.idEstudiante()), f.nombreEstudiante(), f.caso(), f.sesion(), texto(f.fechaSesion()),
                f.estadoConvocatoria(), f.estadoParticipacion(), Integer.toString(f.numeroIntentos()),
                f.estadoEvaluacion(), f.calificacionPublicada() == null ? null : f.calificacionPublicada().toPlainString());
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
