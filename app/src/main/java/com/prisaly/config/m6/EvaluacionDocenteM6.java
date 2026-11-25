package com.prisaly.config.m6;

import com.prisaly.m5.infrastructure.evaluacion.LecturaIntentoDocenteDesdeM5;
import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.application.port.EvaluacionCasoUso;
import com.prisaly.m6.domain.model.Calificacion;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Teacher-facing M6 evaluation (DC-55), assembled only by {@link M6EvaluacionConfig} when
 * {@code prisaly.m6.evaluacion.enabled=true}. It holds the whole M6 chain (protection proxy → protected preparation →
 * S1 → insert-only writer) and exposes only the two operations of CONTRATOS-HTTP-M6-55; no port of the chain is a
 * Spring bean. It contains no policy of its own.
 */
public final class EvaluacionDocenteM6 {
    private final EvaluacionCasoUso evaluacion;
    private final LecturaIntentoDocenteDesdeM5 lector;
    private final ConsultaCalificacionPort calificaciones;

    EvaluacionDocenteM6(EvaluacionCasoUso evaluacion, LecturaIntentoDocenteDesdeM5 lector,
            ConsultaCalificacionPort calificaciones) {
        this.evaluacion = Objects.requireNonNull(evaluacion, "evaluacion");
        this.lector = Objects.requireNonNull(lector, "lector");
        this.calificaciones = Objects.requireNonNull(calificaciones, "calificaciones");
    }

    /** Explicit evaluation by the owning teacher (M6-R7); returns the single recorded grade. */
    public Calificacion evaluar(UUID idIntento, UUID idRubrica) {
        evaluacion.evaluar(new SolicitudEvaluacion(idIntento, idRubrica));
        return calificaciones.findByIntentoId(idIntento)
                .orElseThrow(() -> new IllegalStateException("La calificacion registrada no puede leerse"));
    }

    /** N2 (DC-87): the same ownership rule as the teacher query, reused by the release; no policy of its own. */
    boolean esDelDocenteActual(UUID idIntento) {
        return lector.perteneceAlDocenteActual(Objects.requireNonNull(idIntento, "idIntento"));
    }

    /** Teacher query: denied unless the attempt is of a case of the current active teacher. */
    public Optional<Calificacion> consultar(UUID idIntento) {
        Objects.requireNonNull(idIntento, "idIntento");
        if (!lector.perteneceAlDocenteActual(idIntento)) {
            throw new AccesoEvaluacionDenegadoException();
        }
        return calificaciones.findByIntentoId(idIntento);
    }
}
