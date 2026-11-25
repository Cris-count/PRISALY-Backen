package com.prisaly.m7.infrastructure;

import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m7.application.ContextoConvocatoriaPort;
import com.prisaly.m7.application.NotificarConvocatoriaService;
import com.prisaly.m7.application.RegistroAvisoPort;
import com.prisaly.m7.application.RegistroEnvioCorreoPort;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans M7: persistencia e idempotencia de Aviso/EnvioCorreo y proveedor productivo de
 * {@link NotificacionPort}. Al existir este bean, el fallback de M4
 * ({@code M4ProviderAutoConfiguration}, {@code @ConditionalOnMissingBean}) no se registra.
 * DD-02 student-access delivery uses a separate port/outbox — never {@link NotificacionPort}.
 * Issuance assembly lives in app (not here) so convocatoria IT contexts do not require M1 services.
 */
@Configuration(proxyBeanMethods = false)
public class M7Configuration {

    // DD-02 B (DC-DD02-B4-B5-01): with the student-access flag ON the encrypted Outbox of
    // ConfiguracionCodigoAccesoM7 is the delivery port; the in-memory adapter remains only while it is OFF.
    @Bean
    @ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "false", matchIfMissing = true)
    InMemoryStudentAccessDeliveryAdapter studentAccessDeliveryOutboxM7() {
        return new InMemoryStudentAccessDeliveryAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "false", matchIfMissing = true)
    StudentAccessDeliveryPort studentAccessDeliveryM7(
            InMemoryStudentAccessDeliveryAdapter outbox) {
        return outbox;
    }

    /** OBS-83-M7-02 (DC-86): presentation zone of notice/email dates. Default UTC keeps the approved text unchanged. */
    static final String PROPIEDAD_ZONA = "prisaly.m7.zona-horaria";

    @Bean
    ContextoConvocatoriaPort contextoConvocatoriaM7(EntityManager em, Environment environment) {
        return new ContextoConvocatoriaJpaAdapter(em, zonaPresentacion(environment));
    }

    /** Fails at startup on an unknown zone id instead of silently formatting in another zone. */
    static java.time.ZoneId zonaPresentacion(Environment environment) {
        String valor = environment.getProperty(PROPIEDAD_ZONA, "UTC").trim();
        try {
            java.time.ZoneId zona = java.time.ZoneId.of(valor);
            return "UTC".equals(valor) || "Z".equals(valor) ? java.time.ZoneOffset.UTC : zona;
        } catch (java.time.DateTimeException ex) {
            throw new IllegalStateException(PROPIEDAD_ZONA + " no es una zona horaria válida: " + valor, ex);
        }
    }

    /** "UTC" for UTC (the approved text, byte for byte); otherwise e.g. "hora America/Bogota (UTC-05:00)". */
    static String etiquetaZona(java.time.ZoneId zona) {
        if (java.time.ZoneOffset.UTC.equals(zona)) {
            return "UTC";
        }
        var desfase = zona.getRules().getOffset(java.time.Instant.now());
        return "hora " + zona.getId() + " (UTC" + desfase.getId() + ")";
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
                "Has sido convocado a la sesión {nombreSesion} del caso {nombreCaso}. "
                        + "Inicio: {inicioPrevisto} {zonaHoraria}. Fin: {finPrevisto} {zonaHoraria}. {mensajePersonalizado}")
                .replace("{zonaHoraria}", etiquetaZona(zonaPresentacion(environment)));
        String asunto = environment.getProperty(
                "prisaly.m7.asunto-convocatoria", "Convocatoria a sesión {nombreSesion}");
        return new NotificarConvocatoriaService(contexto, registroAviso, registroEnvio, contenido, asunto);
    }

    @Bean
    NotificacionPort notificacionM7(NotificarConvocatoriaService servicio) {
        return new NotificacionM4Adapter(servicio);
    }
}
