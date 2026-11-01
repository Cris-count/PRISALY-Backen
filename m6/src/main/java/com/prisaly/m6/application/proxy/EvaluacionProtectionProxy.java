package com.prisaly.m6.application.proxy;

import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.EvaluacionCasoUso;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.Objects;

/** No academic effects: checks access once, then delegates the same request once. */
public final class EvaluacionProtectionProxy implements EvaluacionCasoUso {
    private final AutorizacionEvaluacionPort autorizacion;
    private final EvaluacionCasoUso delegado;

    /** A missing authorization provider is retained as an unavailable, deny-only configuration. */
    public EvaluacionProtectionProxy(AutorizacionEvaluacionPort autorizacion, EvaluacionCasoUso delegado) {
        this.autorizacion = autorizacion;
        this.delegado = Objects.requireNonNull(delegado, "delegado");
    }

    @Override
    public ResultadoEvaluacion evaluar(SolicitudEvaluacion solicitud) {
        Objects.requireNonNull(solicitud, "solicitud");
        if (autorizacion == null) { throw new AccesoEvaluacionDenegadoException(); }
        DecisionAcceso decision;
        try {
            decision = autorizacion.verificarAcceso(solicitud);
        } catch (RuntimeException failure) {
            throw new AccesoEvaluacionDenegadoException(failure);
        }
        if (decision != DecisionAcceso.AUTORIZADO) { throw new AccesoEvaluacionDenegadoException(); }
        // Outside the authorization catch: delegate failures keep their original meaning.
        return delegado.evaluar(solicitud);
    }
}
