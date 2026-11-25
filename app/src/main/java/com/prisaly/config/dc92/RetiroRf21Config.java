package com.prisaly.config.dc92;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DC-92 RF-21: {@code prisaly.m4.retiro.enabled} (shipped false: no route). Requires the DC-92 candidate schema. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RetiroRf21Config.PROPIEDAD, havingValue = "true")
public class RetiroRf21Config {
    public static final String PROPIEDAD = "prisaly.m4.retiro.enabled";

    @Bean
    RetiroConvocatoriaRf21 retiroConvocatoriaRf21(SesionAcademicaService sesiones, IdentityAccessPort identidad,
            JdbcTemplate jdbc, PlatformTransactionManager transacciones) {
        VistaParticipacionDc92.verificarEsquema(jdbc, "RF21-REQUIERE-ESQUEMA-DC92");
        return new RetiroConvocatoriaRf21(sesiones, identidad, jdbc, new TransactionTemplate(transacciones));
    }
}
