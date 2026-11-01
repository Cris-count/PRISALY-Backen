package com.prisaly.m7.infrastructure;

import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m7.application.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class M7Configuration {
    @Bean
    ContextoConvocatoriaPort contextoConvocatoriaM7(EntityManager em) {
        return new ContextoConvocatoriaJpaAdapter(em);
    }

    @Bean
    RegistroAvisoPort registroAvisoM7(EntityManager em) { return new RegistroAvisoJpaAdapter(em); }

    @Bean
    ObservadorRegistroAviso observadorRegistroM7(RegistroAvisoPort registro, Environment environment) {
        return new ObservadorRegistroAviso(registro, environment.getProperty(
                "prisaly.m7.texto-convocatoria", "Se ha registrado tu convocatoria a la sesión {sesionId}."));
    }

    @Bean
    PublicadorConvocatoria publicadorM7(ObservadorRegistroAviso requerido,
            List<ObservadorConvocatoria> observadores) {
        return new PublicadorConvocatoria(requerido,
                observadores.stream().filter(o -> o != requerido).toList());
    }

    @Bean
    NotificacionPort notificacionM7(ContextoConvocatoriaPort contexto, PublicadorConvocatoria publicador) {
        return new NotificacionM4Adapter(contexto, publicador);
    }
}
