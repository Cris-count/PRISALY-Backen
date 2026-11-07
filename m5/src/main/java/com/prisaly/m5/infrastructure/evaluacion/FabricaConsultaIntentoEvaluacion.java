package com.prisaly.m5.infrastructure.evaluacion;

import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit APP composition only. Does not invent an institutional provider or publish a raw reader. */
public record FabricaConsultaIntentoEvaluacion(AutorizacionEvaluacionPort autorizacion,
        AccesoProtegidoParticipacion protegida, RepositorioIntentos repositorio,
        IdentidadEstudianteActual identidad, ProgramacionParticipacionPort programacion,
        PlatformTransactionManager transacciones) {
    public FabricaConsultaIntentoEvaluacion {
        Objects.requireNonNull(autorizacion);
        Objects.requireNonNull(protegida);
        Objects.requireNonNull(repositorio);
        Objects.requireNonNull(identidad);
        Objects.requireNonNull(programacion);
        Objects.requireNonNull(transacciones);
    }

    public ConsultaIntentoEvaluacionPort para(SolicitudEvaluacion solicitud) {
        return new ConsultaIntentoEvaluacionDesdeM5(solicitud, autorizacion, protegida,
                repositorio, identidad, programacion, transacciones);
    }
}
