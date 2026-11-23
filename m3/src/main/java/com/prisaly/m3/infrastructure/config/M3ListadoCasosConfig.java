package com.prisaly.m3.infrastructure.config;

import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.service.CasoDocenteListadoService;
import com.prisaly.m3.infrastructure.persistence.jdbc.CasoListadoDocenteJdbcAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** DC-67 candidate: GET /v1/casos exists only with {@code prisaly.m3.listado.enabled=true} (shipped false). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = M3ListadoCasosConfig.PROPIEDAD, havingValue = "true")
public class M3ListadoCasosConfig {
    public static final String PROPIEDAD = "prisaly.m3.listado.enabled";

    @Bean
    CasoDocenteListadoService casoDocenteListadoService(ObjectProvider<CasoCurrentUserPort> identidad,
            ObjectProvider<AutorizacionConsultaCasoPort> autorizacion, JdbcTemplate jdbc) {
        return new CasoDocenteListadoService(identidad.getIfAvailable(), autorizacion.getIfAvailable(),
                new CasoListadoDocenteJdbcAdapter(jdbc));
    }
}
