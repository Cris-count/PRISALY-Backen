package com.prisaly.m5.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.servicio.ActualizarRespuestaServicio;
import com.prisaly.m5.application.servicio.ConsultarIntentoServicio;
import com.prisaly.m5.application.servicio.FinalizarIntentoServicio;
import com.prisaly.m5.application.servicio.RegistrarRespuestaServicio;

/**
 * Ensambla los casos de uso de M5 con su adaptador de persistencia. Las
 * clases de aplicación no dependen de Spring; aquí se cablean.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionParticipacion {

    @Bean
    ParticipacionFacade participacionFacade(RepositorioIntentos repositorio) {
        return new ParticipacionFacade(
                new ConsultarIntentoServicio(repositorio),
                new RegistrarRespuestaServicio(repositorio),
                new ActualizarRespuestaServicio(repositorio),
                new FinalizarIntentoServicio(repositorio));
    }

    @Bean
    FachadaParticipacionTransaccional fachadaParticipacionTransaccional(
            ParticipacionFacade participacionFacade, PlatformTransactionManager transacciones) {
        return new FachadaParticipacionTransaccional(participacionFacade, transacciones);
    }
}
