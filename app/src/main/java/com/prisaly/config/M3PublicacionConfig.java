package com.prisaly.config;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.infrastructure.persistence.jpa.CasoPublicationAdapter;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** PD-02: explicit wiring of the state transitions. Permissions are never granted by default. */
@Configuration(proxyBeanMethods = false)
public class M3PublicacionConfig {
    @Bean
    public CasoPublicationPort casoPublicationPort(EntityManagerFactory emf, PlatformTransactionManager manager,
            CaseAuthorizationPort authorization) {
        return new CasoPublicationAdapter(emf, manager, authorization);
    }
}
