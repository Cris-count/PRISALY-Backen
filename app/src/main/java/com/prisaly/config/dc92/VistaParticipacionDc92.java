package com.prisaly.config.dc92;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DC-92 — read model shared by DD-09 (teacher query), DD-11 (CSV report) and DD-13 (absences): one row per
 * convocation of ONE session. It only reads; authorization (owner teacher, M4 rule) is the caller's.
 *
 * <p>Derivations are the literal DC-90/DC-92 rules, nothing else:
 * <ul>
 *   <li><b>estadoParticipacion</b> (DD-13 + RF-21): RETIRADO if withdrawn; with attempts PARTICIPACION_INICIADA (one
 *       EnCurso) or PARTICIPACION_FINALIZADA; without attempts the teacher's latest classification
 *       (AUSENCIA_JUSTIFICADA / AUSENCIA_NO_JUSTIFICADA), else AUSENTE_PENDIENTE_REVISION once the session is
 *       Finalizado, else CONVOCADO. An absence never produces a grade (DD-13).</li>
 *   <li><b>considered attempt</b> (DD-04): the teacher's latest selection; with a single attempt, that one; with two
 *       attempts and no selection there is none (SELECCION_PENDIENTE).</li>
 *   <li><b>estadoEvaluacion / calificacionPublicada</b> (DD-09, DD-11, DD-12): SIN_INTENTOS, SELECCION_PENDIENTE,
 *       SIN_EVALUAR, EVALUADA_NO_PUBLICADA or PUBLICADA; the value is exposed ONLY when PUBLICADA (N2 release).</li>
 * </ul>
 * Needs the DC-92 candidate schema (V019 + V021-V023); {@link #verificarEsquema} fails closed otherwise.
 */
public final class VistaParticipacionDc92 {

    public record Fila(UUID idConvocatoria, UUID idEstudiante, String nombreEstudiante, String caso, String sesion,
            Instant fechaSesion, String estadoConvocatoria, Instant fechaRetiro, UUID idParticipacion, Instant fechaIngreso,
            int numeroIntentos, String estadoParticipacion, UUID idIntentoConsiderado, Instant inicioIntento,
            Instant finIntento, String estadoEvaluacion, BigDecimal calificacionPublicada, Instant fechaPublicacion) { }

    /** Raw facts of one convocation, before applying the DC-90 rules. */
    record Hechos(boolean retirado, int intentos, boolean intentoEnCurso, String ausenciaClasificada,
            boolean sesionFinalizada, UUID intentoSeleccionado, boolean intentoConsiderado, boolean calificada,
            boolean publicada) { }

    private static final String SQL = """
            SELECT c."idConvocatoria", c."idEstudiante", u."nombre" AS nombre, ca."nombre" AS caso,
                   s."nombreEspacioAcademico" AS sesion, s."inicioPrevisto", s."estado" AS estado_sesion,
                   c."estadoAutorizacion", c."fechaRetiro", p."idParticipacion",
                   COALESCE(p."fechaInicio", (SELECT min(x."inicio") FROM "Intento" x
                                              WHERE x."idParticipacion" = p."idParticipacion")) AS fecha_ingreso,
                   (SELECT count(*) FROM "Intento" x WHERE x."idParticipacion" = p."idParticipacion") AS intentos,
                   EXISTS (SELECT 1 FROM "Intento" x WHERE x."idParticipacion" = p."idParticipacion"
                           AND x."estado" = 'EnCurso') AS en_curso,
                   sel."idIntento" AS seleccionado, aus."estado" AS ausencia,
                   ic."idIntento" AS considerado, ic."inicio" AS inicio_intento, ic."fin" AS fin_intento,
                   k."valor", k."idCalificacion", l."fechaLiberacion"
            FROM "Convocatoria" c
            JOIN "Sesion" s ON s."idSesion" = c."idSesion"
            JOIN "Caso" ca ON ca."idCaso" = s."idCaso"
            LEFT JOIN "Estudiante" e ON e."idEstudiante" = c."idEstudiante"
            LEFT JOIN "Usuario" u ON u."idUsuario" = e."idUsuario"
            LEFT JOIN "Participacion" p ON p."idConvocatoria" = c."idConvocatoria"
            LEFT JOIN LATERAL (SELECT "idIntento" FROM "SeleccionIntento" WHERE "idParticipacion" = p."idParticipacion"
                               ORDER BY "fechaSeleccion" DESC LIMIT 1) sel ON true
            LEFT JOIN LATERAL (SELECT "estado" FROM "ClasificacionAusencia" WHERE "idConvocatoria" = c."idConvocatoria"
                               ORDER BY "fechaRegistro" DESC LIMIT 1) aus ON true
            LEFT JOIN LATERAL (SELECT i."idIntento", i."inicio", i."fin" FROM "Intento" i
                               WHERE i."idParticipacion" = p."idParticipacion"
                                 AND (i."idIntento" = sel."idIntento"
                                      OR (sel."idIntento" IS NULL AND (SELECT count(*) FROM "Intento" y
                                          WHERE y."idParticipacion" = p."idParticipacion") = 1))) ic ON true
            LEFT JOIN "Calificacion" k ON k."idIntento" = ic."idIntento"
            LEFT JOIN "LiberacionCalificacion" l ON l."idCalificacion" = k."idCalificacion"
            WHERE c."idSesion" = ?
            ORDER BY u."nombre" NULLS LAST, c."idEstudiante"
            """;

    private final JdbcTemplate jdbc;

    public VistaParticipacionDc92(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /** Fails startup when the DC-92 candidate tables are missing (no silent degradation). */
    public static void verificarEsquema(JdbcTemplate jdbc, String codigo) {
        for (String tabla : new String[] {"LiberacionCalificacion", "SeleccionIntento", "ClasificacionAusencia",
                "RetiroConvocatoria"}) {
            Boolean existe = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class,
                    "public.\"" + tabla + "\"");
            if (!Boolean.TRUE.equals(existe)) {
                throw new IllegalStateException(codigo + ": falta la tabla candidata " + tabla
                        + " (esquema DC-92, contrato bd0052-v013-integral-dc92)");
            }
        }
    }

    public List<Fila> filas(UUID idSesion) {
        return jdbc.query(SQL, (rs, n) -> fila(rs), idSesion);
    }

    private static Fila fila(ResultSet rs) throws SQLException {
        Instant fechaRetiro = instante(rs, "fechaRetiro");
        int intentos = rs.getInt("intentos");
        UUID considerado = rs.getObject("considerado", UUID.class);
        Instant liberacion = instante(rs, "fechaLiberacion");
        var hechos = new Hechos(fechaRetiro != null, intentos, rs.getBoolean("en_curso"), rs.getString("ausencia"),
                "Finalizado".equals(rs.getString("estado_sesion")), rs.getObject("seleccionado", UUID.class),
                considerado != null, rs.getObject("idCalificacion", UUID.class) != null, liberacion != null);
        String evaluacion = estadoEvaluacion(hechos);
        return new Fila(rs.getObject("idConvocatoria", UUID.class), rs.getObject("idEstudiante", UUID.class),
                rs.getString("nombre"), rs.getString("caso"), rs.getString("sesion"), instante(rs, "inicioPrevisto"),
                rs.getString("estadoAutorizacion"), fechaRetiro, rs.getObject("idParticipacion", UUID.class),
                instante(rs, "fecha_ingreso"), intentos, estadoParticipacion(hechos), considerado,
                instante(rs, "inicio_intento"), instante(rs, "fin_intento"), evaluacion,
                "PUBLICADA".equals(evaluacion) ? rs.getBigDecimal("valor") : null,
                "PUBLICADA".equals(evaluacion) ? liberacion : null);
    }

    /** DD-13 + RF-21. */
    static String estadoParticipacion(Hechos h) {
        if (h.retirado()) return "RETIRADO";
        if (h.intentos() > 0) return h.intentoEnCurso() ? "PARTICIPACION_INICIADA" : "PARTICIPACION_FINALIZADA";
        if (h.ausenciaClasificada() != null) return h.ausenciaClasificada();
        return h.sesionFinalizada() ? "AUSENTE_PENDIENTE_REVISION" : "CONVOCADO";
    }

    /** DD-04 + DD-09/DD-11/DD-12: only a released grade is ever "PUBLICADA". */
    static String estadoEvaluacion(Hechos h) {
        if (h.intentos() == 0) return "SIN_INTENTOS";
        if (!h.intentoConsiderado()) return "SELECCION_PENDIENTE";
        if (!h.calificada()) return "SIN_EVALUAR";
        return h.publicada() ? "PUBLICADA" : "EVALUADA_NO_PUBLICADA";
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime v = rs.getObject(columna, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }
}
