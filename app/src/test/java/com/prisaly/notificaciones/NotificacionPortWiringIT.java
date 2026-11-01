package com.prisaly.notificaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m7.infrastructure.NotificacionM4Adapter;
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

/** Full monolith context: M7 provides {@link NotificacionPort} and M4's fallback is not registered. */
@SpringBootTest
@Testcontainers
class NotificacionPortWiringIT {

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

    @Test
    void onlyM7ProviderIsRegistered() {
        Map<String, NotificacionPort> providers = context.getBeansOfType(NotificacionPort.class);
        assertEquals(1, providers.size());
        assertTrue(providers.containsKey("notificacionM7"));
        assertEquals(
                NotificacionM4Adapter.class,
                AopUtils.getTargetClass(providers.get("notificacionM7")));
        assertFalse(context.containsBean("notificacionesNoDisponibles"));
    }
}
