package com.prisaly.m5.infrastructure.evaluacion;

import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.api.SolicitudEvaluacion;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Fábrica de composición. Arma la consulta de un intento para M6 juntando autorización,
 * identidad del estudiante, programación de M4 y el repositorio de intentos.
 * No publica un lector directo de tablas ni inventa un proveedor institucional.
 */
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
