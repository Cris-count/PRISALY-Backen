package com.prisaly.config.dc92;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DC-92 DD-09 ({@code prisaly.m4.consulta-participaciones.enabled}) and DD-13 ({@code prisaly.m4.ausencias.enabled}).
 * Both shipped {@code false}: nothing exists. Either one on requires the DC-92 candidate schema; startup fails closed
 * without it ({@code DC92-REQUIERE-ESQUEMA}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${prisaly.m4.consulta-participaciones.enabled:false} or ${prisaly.m4.ausencias.enabled:false}")
public class ParticipacionesDc92Config {
    public static final String PROPIEDAD_DD09 = "prisaly.m4.consulta-participaciones.enabled";
    public static final String PROPIEDAD_DD13 = "prisaly.m4.ausencias.enabled";

    @Bean
    ParticipacionesDocenteDc92 participacionesDocenteDc92(SesionAcademicaService sesiones, IdentityAccessPort identidad,
            JdbcTemplate jdbc, PlatformTransactionManager transacciones) {
        VistaParticipacionDc92.verificarEsquema(jdbc, "DC92-REQUIERE-ESQUEMA");
        return new ParticipacionesDocenteDc92(sesiones, identidad, jdbc, new TransactionTemplate(transacciones));
    }
}
