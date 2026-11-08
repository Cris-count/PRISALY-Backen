package com.prisaly.config;

import com.prisaly.m1.application.port.IdentityAccessPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single provider of {@code AutorizacionConsultaCasoPort}. The policy is read from
 * {@code prisaly.m3.lectura.*} (declared in application.yml; no in-code defaults).
 */
@Configuration(proxyBeanMethods = false)
public class M3CaseReadAuthorizationConfig {
    @Bean
    public M3CaseReadAuthorizationAdapter m3CaseReadAuthorizationAdapter(
            IdentityAccessPort identity,
            @Value("${prisaly.m3.lectura.permiso}") String permiso,
            @Value("${prisaly.m3.lectura.estados-visibles}") String estadosVisibles,
            @Value("${prisaly.m3.lectura.requiere-activo}") boolean requiereActivo) {
        return new M3CaseReadAuthorizationAdapter(
                identity, PoliticaLecturaCaso.desdeConfiguracion(permiso, estadosVisibles, requiereActivo));
    }
}
