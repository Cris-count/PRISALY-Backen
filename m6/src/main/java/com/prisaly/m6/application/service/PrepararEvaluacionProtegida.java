package com.prisaly.m6.application.service;

import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.integration.ContextoEvaluacionPreparado;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.Objects;

/** Explicit internal composition only; never calculates, writes or publishes a grade. */
public final class PrepararEvaluacionProtegida {
    private final AutorizacionEvaluacionPort autorizacion;
    private final ConsultaCalificacionPort calificaciones;
    private final PrepararContextoEvaluacionService preparacion;

    public PrepararEvaluacionProtegida(AutorizacionEvaluacionPort autorizacion,
            ConsultaCalificacionPort calificaciones, PrepararContextoEvaluacionService preparacion) {
        this.autorizacion = autorizacion;
        this.calificaciones = Objects.requireNonNull(calificaciones, "calificaciones");
        this.preparacion = Objects.requireNonNull(preparacion, "preparacion");
    }

    public ContextoEvaluacionPreparado preparar(SolicitudEvaluacion solicitud) {
        Objects.requireNonNull(solicitud, "solicitud");
        if (autorizacion == null) { throw new AccesoEvaluacionDenegadoException(); }
        DecisionAcceso decision;
        try {
            decision = autorizacion.verificarAcceso(solicitud);
        } catch (RuntimeException failure) {
            // Institutional providers may include credentials in their causes.
            throw new AccesoEvaluacionDenegadoException();
        }
        if (decision != DecisionAcceso.AUTORIZADO) { throw new AccesoEvaluacionDenegadoException(); }
        if (Objects.requireNonNull(calificaciones.findByIntentoId(solicitud.idIntento())).isPresent()) {
            throw new IllegalStateException("El intento ya tiene calificacion; no se prepara una reevaluacion");
        }
        var preparado = preparacion.preparar(solicitud.idIntento());
        var rubrica = preparado.contextoAcademico().rubrica();
        if (rubrica == null || !solicitud.idRubrica().equals(rubrica.idRubrica())
                || !preparado.intento().idCaso().equals(rubrica.idCaso())) {
            throw new IllegalStateException("La rubrica solicitada no corresponde al contexto recuperado");
        }
        return preparado;
    }
}
