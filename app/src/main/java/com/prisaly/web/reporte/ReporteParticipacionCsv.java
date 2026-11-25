package com.prisaly.web.reporte;

import com.prisaly.m4.domain.model.EstadoSesion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 (RF-16 candidate): renders the per-session participation report, one record per
 * convocatoria. Columns are FIXED and PROVISIONAL (DD-11 "momento y columnas del reporte" is not communicated);
 * changing them is a contract change for Dirección Central.
 *
 * <p>{@code situacion} is derived only from persisted facts: {@code RETIRADO} (fechaRetiro set), {@code AUSENTE}
 * (no attempt and the session is Finalizado), {@code SIN_PARTICIPACION} (no attempt yet, session not finished),
 * {@code EN_CURSO} (last attempt EnCurso: partial progress) and {@code FINALIZADO}. The grade follows DD-04 (last
 * evaluated attempt) and is filled only when M6 evaluation is enabled; otherwise {@code estado_calificacion} is
 * {@code NO_HABILITADA} and the grade cell is empty. Timestamps are ISO-8601 UTC.
 */
final class ReporteParticipacionCsv {

    static final List<String> CABECERA = List.of(
            "id_sesion", "espacio_academico", "estado_sesion", "id_estudiante", "nombre_estudiante",
            "correo_estudiante", "estado_convocatoria", "situacion", "estado_participacion", "intentos",
            "intentos_finalizados", "ultimo_intento_numero", "ultimo_intento_estado", "ultimo_intento_inicio",
            "ultimo_intento_fin", "ultimo_intento_motivo", "calificacion", "intento_calificado", "estado_calificacion");

    /** UTF-8 byte order mark so common spreadsheet software decodes accented names correctly. */
    static final String BOM = "﻿";

    record Sesion(UUID id, String espacioAcademico, EstadoSesion estado) { }

    record Fila(UUID idEstudiante, String nombre, String correo, String estadoConvocatoria, Instant fechaRetiro,
            String estadoParticipacion, int intentos, int intentosFinalizados, Integer ultimoNumero,
            String ultimoEstado, Instant ultimoInicio, Instant ultimoFin, String ultimoMotivo,
            BigDecimal calificacion, Integer intentoCalificado) { }

    private ReporteParticipacionCsv() {
    }

    static byte[] generar(Sesion sesion, List<Fila> filas, boolean calificacionHabilitada) {
        var csv = new StringBuilder(BOM);
        CsvRfc4180.registro(csv, CABECERA);
        for (Fila f : filas) {
            CsvRfc4180.registro(csv, celdas(sesion, f, calificacionHabilitada));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static List<String> celdas(Sesion sesion, Fila f, boolean calificacionHabilitada) {
        var celdas = new ArrayList<String>(Arrays.asList(
                texto(sesion.id()), sesion.espacioAcademico(), sesion.estado().valorBd(), texto(f.idEstudiante()),
                f.nombre(), f.correo(), f.estadoConvocatoria(), situacion(sesion.estado(), f), f.estadoParticipacion(),
                Integer.toString(f.intentos()), Integer.toString(f.intentosFinalizados()), texto(f.ultimoNumero()),
                f.ultimoEstado(), texto(f.ultimoInicio()), texto(f.ultimoFin()), f.ultimoMotivo()));
        if (!calificacionHabilitada) {
            celdas.addAll(Arrays.asList(null, null, "NO_HABILITADA"));
        } else if (f.calificacion() == null) {
            celdas.addAll(Arrays.asList(null, null, "SIN_CALIFICACION"));
        } else {
            celdas.addAll(List.of(f.calificacion().toPlainString(), texto(f.intentoCalificado()), "CALIFICADA"));
        }
        return celdas;
    }

    static String situacion(EstadoSesion estadoSesion, Fila f) {
        if (f.fechaRetiro() != null) {
            return "RETIRADO";
        }
        if (f.intentos() == 0) {
            return estadoSesion == EstadoSesion.FINALIZADO ? "AUSENTE" : "SIN_PARTICIPACION";
        }
        return "EnCurso".equals(f.ultimoEstado()) ? "EN_CURSO" : "FINALIZADO";
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
