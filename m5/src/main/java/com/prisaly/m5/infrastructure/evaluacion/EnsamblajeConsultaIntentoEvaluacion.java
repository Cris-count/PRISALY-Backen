package com.prisaly.m5.infrastructure.evaluacion;

import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/** Opt-in @Import from APP after institutional policy approval; intentionally not component-scanned. */
public class EnsamblajeConsultaIntentoEvaluacion {
    @Bean
    FabricaConsultaIntentoEvaluacion fabricaConsultaIntentoEvaluacion(
            AutorizacionEvaluacionPort autorizacion, AccesoProtegidoParticipacion protegida,
            RepositorioIntentos repositorio, IdentidadEstudianteActual identidad,
            ProgramacionParticipacionPort programacion, PlatformTransactionManager transacciones) {
        return new FabricaConsultaIntentoEvaluacion(autorizacion, protegida, repositorio,
                identidad, programacion, transacciones);
    }
}
