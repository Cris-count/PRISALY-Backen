package com.prisaly.config.m6;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m6.application.port.ConsultaRetroalimentacionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DC-92 DD-12 / RF-26: {@code prisaly.m6.retroalimentacion.enabled} (shipped false: no route). Requires the M6
 * evaluation ({@code M6-RETROALIMENTACION-REQUIERE-EVALUACION}); the student only sees it through N2.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = M6RetroalimentacionDd12Config.PROPIEDAD, havingValue = "true")
public class M6RetroalimentacionDd12Config {
    public static final String PROPIEDAD = "prisaly.m6.retroalimentacion.enabled";

    @Bean
    RetroalimentacionDocenteDd12 retroalimentacionDocenteDd12(ObjectProvider<EvaluacionDocenteM6> evaluacion,
            IdentityAccessPort identidad, ConsultaRetroalimentacionPort lectura, JdbcTemplate jdbc) {
        EvaluacionDocenteM6 m6 = evaluacion.getIfAvailable();
        if (m6 == null) {
            throw new IllegalStateException("M6-RETROALIMENTACION-REQUIERE-EVALUACION: " + PROPIEDAD
                    + "=true necesita prisaly.m6.evaluacion.enabled=true");
        }
        return new RetroalimentacionDocenteDd12(m6, identidad, lectura, jdbc);
    }
}
