package com.prisaly.config.m6;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.domain.model.Calificacion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * N2 (M6-R10, DC-43) CANDIDATE — DC-BE-CIERRE-DEFINITIVO-87. Explicit, single, irreversible release of an existing grade
 * by the owning teacher, and the student's own query only after the release. Assembled only by
 * {@link M6LiberacionConfig} when {@code prisaly.m6.liberacion.enabled=true}, which needs the M6 evaluation and the
 * candidate table {@code "LiberacionCalificacion"} (V019, {@code db/m6-propuesta}).
 *
 * <p>No policy of its own: ownership is M6's ({@link EvaluacionDocenteM6#esDelDocenteActual}), student identity and
 * titularity are M5's ({@link ControlAccesoIntentos}: DD-02 identity + M4 titularity), the grade is M6's immutable
 * {@code Calificacion}. Idempotency and concurrency rest on the primary key ({@code ON CONFLICT DO NOTHING}); the V019
 * triggers forbid UPDATE/DELETE and a releaser other than the case owner.
 */
public final class LiberacionCalificacionM6 {

    /** Result of a release; {@code nueva} is false when the grade had already been released (idempotent call). */
    public record Liberacion(Calificacion calificacion, UUID idDocente, Instant fechaLiberacion, boolean nueva) { }

    /** What the student may see: the released grade only. */
    public record CalificacionLiberada(Calificacion calificacion, Instant fechaLiberacion) { }

    /** No grade recorded for the attempt. */
    public static final class CalificacionNoEncontradaException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        CalificacionNoEncontradaException() {
            super("El intento no tiene calificacion");
        }
    }

    private final EvaluacionDocenteM6 evaluacion;
    private final ConsultaCalificacionPort calificaciones;
    private final IdentityAccessPort identidad;
    private final ControlAccesoIntentos accesoEstudiante;
    private final JdbcTemplate jdbc;
    /** RF-27 candidate (DC-88): null unless {@code prisaly.m7.notificacion-calificacion.enabled=true}. */
    private final NotificacionCalificacionM7 notificacion;
    /** Release (+ RF-27 notice) in one transaction; null keeps the DC-87 behaviour (auto-commit insert). */
    private final org.springframework.transaction.support.TransactionTemplate transaccion;

    LiberacionCalificacionM6(EvaluacionDocenteM6 evaluacion, ConsultaCalificacionPort calificaciones,
            IdentityAccessPort identidad, ControlAccesoIntentos accesoEstudiante, JdbcTemplate jdbc) {
        this(evaluacion, calificaciones, identidad, accesoEstudiante, jdbc, null, null);
    }

    LiberacionCalificacionM6(EvaluacionDocenteM6 evaluacion, ConsultaCalificacionPort calificaciones,
            IdentityAccessPort identidad, ControlAccesoIntentos accesoEstudiante, JdbcTemplate jdbc,
            NotificacionCalificacionM7 notificacion,
            org.springframework.transaction.support.TransactionTemplate transaccion) {
        this.evaluacion = Objects.requireNonNull(evaluacion, "evaluacion");
        this.calificaciones = Objects.requireNonNull(calificaciones, "calificaciones");
        this.identidad = Objects.requireNonNull(identidad, "identidad");
        this.accesoEstudiante = Objects.requireNonNull(accesoEstudiante, "accesoEstudiante");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        if ((notificacion == null) != (transaccion == null)) {
            throw new IllegalArgumentException("La notificacion RF-27 requiere su transaccion");
        }
        this.notificacion = notificacion;
        this.transaccion = transaccion;
    }

    /** Teacher release: owner only; 404 without a grade; a repeated call returns the first release unchanged. */
    public Liberacion liberar(UUID idIntento) {
        if (!evaluacion.esDelDocenteActual(idIntento)) {
            throw new AccesoEvaluacionDenegadoException();
        }
        Calificacion calificacion = calificaciones.findByIntentoId(idIntento)
                .orElseThrow(CalificacionNoEncontradaException::new);
        UUID docente = identidad.currentUser().userId();
        if (transaccion == null) {
            return registrar(calificacion, docente);
        }
        return transaccion.execute(estado -> {
            Liberacion l = registrar(calificacion, docente);
            if (l.nueva()) {
                notificacion.notificar(calificacion.idCalificacion(), calificacion.idIntento());
            }
            return l;
        });
    }

    private Liberacion registrar(Calificacion calificacion, UUID docente) {
        int insertadas = jdbc.update("INSERT INTO \"LiberacionCalificacion\" (\"idCalificacion\",\"idDocente\") VALUES (?,?)"
                + " ON CONFLICT (\"idCalificacion\") DO NOTHING", calificacion.idCalificacion(), docente);
        var fila = jdbc.queryForMap("SELECT \"idDocente\",\"fechaLiberacion\" FROM \"LiberacionCalificacion\""
                + " WHERE \"idCalificacion\"=?", calificacion.idCalificacion());
        return new Liberacion(calificacion, (UUID) fila.get("idDocente"), instante(fila.get("fechaLiberacion")),
                insertadas == 1);
    }

    /** Release instant of a grade, if released (teacher views). */
    public Optional<Instant> fechaLiberacion(UUID idCalificacion) {
        return jdbc.query("SELECT \"fechaLiberacion\" FROM \"LiberacionCalificacion\" WHERE \"idCalificacion\"=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant(), idCalificacion).stream().findFirst();
    }

    /**
     * Student query. M5 verifies identity and titularity first (its exceptions propagate: 401/403/404 as M5). A grade
     * that does not exist and one that is not released are indistinguishable (empty).
     */
    public Optional<CalificacionLiberada> consultarComoEstudiante(UUID idIntento) {
        accesoEstudiante.verificar(new ReferenciaIntento(Objects.requireNonNull(idIntento, "idIntento").toString()));
        return calificaciones.findByIntentoId(idIntento).flatMap(c ->
                fechaLiberacion(c.idCalificacion()).map(f -> new CalificacionLiberada(c, f)));
    }

    /** DC-92 DD-12: one graded question as the titular student may see it once published (explicit configuration). */
    public record DetallePregunta(UUID idPregunta, String enunciado, String opcionSeleccionada, Boolean correcta,
            String explicacionSeleccionada, String opcionCorrecta, String explicacionCorrecta) { }

    /**
     * DC-92 DD-12: general feedback of the attempt's participation (M6 read port). The caller already verified the
     * titular student and the release; it is never returned before publication.
     */
    public Optional<String> retroalimentacionGeneral(UUID idIntento, com.prisaly.m6.application.port.ConsultaRetroalimentacionPort retro) {
        UUID participacion = jdbc.queryForObject("SELECT \"idParticipacion\" FROM \"Intento\" WHERE \"idIntento\"=?",
                UUID.class, idIntento);
        return retro.findByParticipacionId(participacion).map(r -> r.contenido());
    }

    /**
     * DC-92 DD-12: per-question detail (selected option, correctness and explanations) of the questions graded by the
     * grade's rubric. Facts only (M3 option data and M5 answers); no grading logic. Exposed only when the explicit
     * configuration {@code prisaly.m6.resultado.detalle-por-pregunta=true} is set AND the grade is released.
     */
    public java.util.List<DetallePregunta> detallePorPregunta(UUID idIntento, UUID idRubrica) {
        return jdbc.query("""
                SELECT p."idPregunta", p."enunciado", sel."texto" AS seleccionada, sel."esCorrecta" AS correcta,
                       sel."explicacion" AS explicacion_seleccionada,
                       (SELECT string_agg(o."texto", ' | ' ORDER BY o."orden") FROM "OpcionRespuesta" o
                         WHERE o."idPregunta" = p."idPregunta" AND o."esCorrecta") AS correcta_texto,
                       (SELECT string_agg(o."explicacion", ' | ' ORDER BY o."orden") FROM "OpcionRespuesta" o
                         WHERE o."idPregunta" = p."idPregunta" AND o."esCorrecta") AS correcta_explicacion
                FROM "RubricaPregunta" rp
                JOIN "Pregunta" p ON p."idPregunta" = rp."idPregunta"
                JOIN "Escenario" e ON e."idEscenario" = p."idEscenario"
                LEFT JOIN "Respuesta" r ON r."idIntento" = ? AND r."idPregunta" = p."idPregunta"
                LEFT JOIN "OpcionRespuesta" sel ON sel."idOpcion" = r."idOpcionSeleccionada"
                WHERE rp."idRubrica" = ?
                ORDER BY e."orden", p."orden"
                """, (rs, n) -> new DetallePregunta(rs.getObject("idPregunta", UUID.class), rs.getString("enunciado"),
                rs.getString("seleccionada"), (Boolean) rs.getObject("correcta"), rs.getString("explicacion_seleccionada"),
                rs.getString("correcta_texto"), rs.getString("correcta_explicacion")), idIntento, idRubrica);
    }

    private static Instant instante(Object valor) {
        if (valor instanceof OffsetDateTime o) return o.toInstant();
        if (valor instanceof java.sql.Timestamp t) return t.toInstant();
        if (valor instanceof Instant i) return i;
        throw new IllegalStateException("fechaLiberacion ilegible");
    }
}
