package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.infrastructure.security.EstudianteSessionFilter;
import com.prisaly.m1.infrastructure.security.ProvisionalAccessTokenStore;
import com.prisaly.m1.infrastructure.security.ProvisionalBearerAuthenticationFilter;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Audit-only probes. Assertions describe reproduced defects, not desired acceptance behavior. */
class AuditoriaIntegralProbesTest {
    @Test void expiredUnusedPersonnelTokensAreRetained() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-08T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var store = new ProvisionalAccessTokenStore(clock, 1);
        String token = null;
        for (int i = 0; i < 1000; i++) token = store.issue(UUID.randomUUID());
        now.set(now.get().plusSeconds(2));
        assertTrue(store.resolveActiveUserId("not-issued").isEmpty());
        var field = ProvisionalAccessTokenStore.class.getDeclaredField("tokensByValue");
        field.setAccessible(true);
        Map<?, ?> retained = (Map<?, ?>) field.get(store);
        assertEquals(1000, retained.size());
        assertTrue(store.resolveActiveUserId(token).isEmpty());
        assertEquals(999, retained.size());
        System.out.println("AUDIT-A01 retainedExpired=1000; afterAccessingOne=999; no credential emitted");
    }

    @Test void studentFilterCannotBeOrderedBeforeAnUnregisteredCustomFilter() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "prisaly.cors.allowed-origins=http://localhost:4200",
                    "prisaly.m1.student-access.enabled=true");
            context.register(StudentSecurityConfiguration.class);
            RuntimeException error = assertThrows(RuntimeException.class, context::refresh);
            Throwable root = error;
            while (root.getCause() != null) root = root.getCause();
            assertInstanceOf(IllegalArgumentException.class, root);
            assertTrue(root.getMessage().contains("does not have a registered order"), root.getMessage());
            assertTrue(root.getMessage().contains("ProvisionalBearerAuthenticationFilter"), root.getMessage());
            System.out.println("AUDIT-A02 " + root.getMessage());
        }
    }

    @Test void crossOriginStudentCookieResponsesDoNotAllowCredentials() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EstudianteSessionFilter> provider = mock(ObjectProvider.class);
        var config = new HttpApiConfig("http://localhost:4200", mock(ProvisionalBearerAuthenticationFilter.class), provider);
        var request = new MockHttpServletRequest("POST", "/v1/acceso-estudiante/autenticar");
        request.addHeader("Origin", "http://localhost:4200");
        var cors = config.corsConfigurationSource().getCorsConfiguration(request);
        assertNotNull(cors);
        assertEquals("http://localhost:4200", cors.checkOrigin("http://localhost:4200"));
        assertNull(cors.getAllowCredentials());
        System.out.println("AUDIT-A03 configuredOriginAccepted=true; allowCredentials=null");
    }

    @Configuration
    @EnableWebSecurity
    @Import(HttpApiConfig.class)
    static class StudentSecurityConfiguration {
        @Bean ProvisionalAccessTokenStore tokens() { return new ProvisionalAccessTokenStore(); }
        @Bean ProvisionalBearerAuthenticationFilter bearer(ProvisionalAccessTokenStore tokens) {
            return new ProvisionalBearerAuthenticationFilter(tokens, mock(IdentityAccessService.class));
        }
        @Bean EstudianteSessionFilter student() { return new EstudianteSessionFilter(); }
    }
}
