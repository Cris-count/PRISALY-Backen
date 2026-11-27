package com.prisaly.m7.infrastructure;

import com.prisaly.m1.application.port.EntregaActivacionDocentePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * D-05 (DC-77): mail channel of teacher activation, same switch as M1 ({@code prisaly.m1.activacion-docente.enabled}).
 * Requires {@code prisaly.m1.activacion-docente.remitente} and a mail transport ({@link FabricaProveedorCorreo}:
 * {@code spring.mail.host} for SMTP, or the HTTPS API key); {@code ...enlace} is optional. The SMTP adapter is private (not a
 * {@code ProveedorCorreoPort} bean), as in {@code ConfiguracionCodigoAccesoM7}, so the convocation worker is untouched.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "prisaly.m1.activacion-docente.enabled", havingValue = "true")
public class ConfiguracionActivacionDocenteM7 {

    @Bean
    EntregaActivacionDocentePort entregaActivacionDocenteM7(ObjectProvider<JavaMailSender> mailSender, Environment env) {
        String remitente = env.getProperty("prisaly.m1.activacion-docente.remitente", "").trim();
        if (remitente.isEmpty()) {
            throw new IllegalStateException("M7-ACTIVACION-REMITENTE: prisaly.m1.activacion-docente.remitente is mandatory");
        }
        return new CorreoActivacionDocenteAdapter(FabricaProveedorCorreo.crear(env, mailSender), remitente,
                env.getProperty("prisaly.m1.activacion-docente.enlace", ""));
    }
}
