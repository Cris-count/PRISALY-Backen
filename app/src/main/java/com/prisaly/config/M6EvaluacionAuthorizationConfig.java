package com.prisaly.config;

import com.prisaly.m1.application.port.IdentityAccessPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires only {@code AutorizacionEvaluacionPort}. A decision here does not authorize
 * {@code ContextoAcademicoConsultaPort}. M1 exposes no capability operation, so this
 * configuration does not issue or verify M3 academic capabilities.
 */
@Configuration(proxyBeanMethods = false)
public class M6EvaluacionAuthorizationConfig {
    @Bean
    public M6EvaluacionAuthorizationAdapter m6EvaluacionAuthorizationAdapter(IdentityAccessPort identity) {
        return new M6EvaluacionAuthorizationAdapter(identity);
    }
}
