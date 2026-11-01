package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ContextoConvocatoriaPort;
import com.prisaly.m7.application.NotificarConvocatoriaService;
import com.prisaly.m7.application.RegistroAvisoPort;
import com.prisaly.m7.application.RegistroEnvioCorreoPort;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans de persistencia e idempotencia M7 (Fase 1).
 * No expone {@code NotificacionPort}: el adaptador productivo de M4 es Fase 2.
 */
@Configuration(proxyBeanMethods = false)
public class M7Configuration {

    @Bean
    ContextoConvocatoriaPort contextoConvocatoriaM7(EntityManager em) {
        return new ContextoConvocatoriaJpaAdapter(em);
    }

    @Bean
    RegistroAvisoPort registroAvisoM7(EntityManager em) {
        return new RegistroAvisoJpaAdapter(em);
    }

    @Bean
    RegistroEnvioCorreoPort registroEnvioCorreoM7(EntityManager em) {
        return new RegistroEnvioCorreoJpaAdapter(em);
    }

    @Bean
    NotificarConvocatoriaService notificarConvocatoriaServiceM7(
            ContextoConvocatoriaPort contexto,
            RegistroAvisoPort registroAviso,
            RegistroEnvioCorreoPort registroEnvio,
            Environment environment) {
        String contenido = environment.getProperty(
                "prisaly.m7.texto-convocatoria",
                "Se ha registrado tu convocatoria a la sesión {sesionId}.");
        String asunto = environment.getProperty(
                "prisaly.m7.asunto-convocatoria", "Convocatoria a sesión {sesionId}");
        return new NotificarConvocatoriaService(contexto, registroAviso, registroEnvio, contenido, asunto);
    }
}
