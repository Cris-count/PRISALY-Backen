package com.prisaly.config.m6;

import com.prisaly.m5.infrastructure.evaluacion.LecturaIntentoDocenteDesdeM5;
import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import java.util.Objects;

/**
 * Evaluation authorization of the switch-guarded assembly (M6-R6): AUTORIZADO only when the attempt exists and its
 * case belongs to the current active teacher. A missing attempt is DENEGADO (existence is not revealed); a technical
 * failure is NO_DISPONIBLE, which M6 also refuses. Not a Spring bean: it never replaces the deny-only
 * {@code M6EvaluacionAuthorizationAdapter}, which stays registered and unchanged.
 */
final class AutorizacionDocentePropietarioM6 implements AutorizacionEvaluacionPort {
    private final LecturaIntentoDocenteDesdeM5 lector;

    AutorizacionDocentePropietarioM6(LecturaIntentoDocenteDesdeM5 lector) {
        this.lector = Objects.requireNonNull(lector, "lector");
    }

    @Override
    public DecisionAcceso verificarAcceso(SolicitudEvaluacion solicitud) {
        if (solicitud == null || solicitud.idIntento() == null || solicitud.idRubrica() == null) {
            return DecisionAcceso.DENEGADO;
        }
        try {
            return lector.perteneceAlDocenteActual(solicitud.idIntento())
                    ? DecisionAcceso.AUTORIZADO : DecisionAcceso.DENEGADO;
        } catch (AccesoEvaluacionDenegadoException denegado) {
            return DecisionAcceso.DENEGADO;
        } catch (RuntimeException fallo) {
            return DecisionAcceso.NO_DISPONIBLE;
        }
    }
}
