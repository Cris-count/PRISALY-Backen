package com.prisaly.m7.infrastructure;

import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m7.application.ContextoConvocatoriaPort;
import com.prisaly.m7.application.NotificarConvocatoriaService;
import com.prisaly.m7.application.RegistroAvisoPort;
import com.prisaly.m7.application.RegistroEnvioCorreoPort;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans M7: persistencia e idempotencia de Aviso/EnvioCorreo y proveedor productivo de
 * {@link NotificacionPort}. Al existir este bean, el fallback de M4
 * ({@code M4ProviderAutoConfiguration}, {@code @ConditionalOnMissingBean}) no se registra.
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

    @Bean
    NotificacionPort notificacionM7(NotificarConvocatoriaService servicio) {
        return new NotificacionM4Adapter(servicio);
    }
}
