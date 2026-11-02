package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.infrastructure.adapter.SesionRepositoryAdapter;
import com.prisaly.m5.infrastructure.programacion.ParticipacionesM4DesdeM5;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Regression for P1: full monolith starts with exactly one {@link ParticipacionesPort}
 * ({@code participacionesM4DesdeM5}) backing {@link SesionRepository}.
 */
@SpringBootTest
@Testcontainers
class ParticipacionesPortWiringIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SesionRepository sesionRepository;

    @Test
    void onlyM5ProviderIsRegisteredAndContextUsesSesionRepositoryAdapter() {
        Map<String, ParticipacionesPort> providers = context.getBeansOfType(ParticipacionesPort.class);
        assertEquals(1, providers.size());
        assertTrue(providers.containsKey("participacionesM4DesdeM5"));
        assertFalse(context.containsBean("historialM4M5"));
        assertFalse(context.containsBean("participacionesNoDisponibles"));
        assertEquals(
                ParticipacionesM4DesdeM5.class,
                AopUtils.getTargetClass(providers.get("participacionesM4DesdeM5")));
        assertEquals(SesionRepositoryAdapter.class, AopUtils.getTargetClass(sesionRepository));
    }
}
