package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.infrastructure.security.*;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Configuration/MVC regression. Student persistence is a test double, not production evidence. */
class StudentHttpSecurityTest {
    private static final String ORIGIN = "http://localhost:4200";
    private static final String URL = "/v1/estudiantes/intentos/prueba";

    @Test void cookieVerifiedByM1ReachesStudentRouteWithoutPersonnelBearer() throws Exception {
        try (var context = context(ORIGIN)) {
            var mvc = mvc(context);
            mvc.perform(get(URL).cookie(cookie())).andExpect(status().isOk()).andExpect(content().string("student"));
            mvc.perform(get("/v1/personal/me").cookie(cookie())).andExpect(status().isUnauthorized());
            mvc.perform(get(URL)).andExpect(status().isUnauthorized());
            mvc.perform(get(URL).cookie(new Cookie(EstudianteSessionFilter.COOKIE, "invalid")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void unsafeCookieOperationsRequireTrustedOrigin() throws Exception {
        try (var context = context(ORIGIN)) {
            var mvc = mvc(context);
            mvc.perform(put(URL).cookie(cookie()).header("Origin", ORIGIN)).andExpect(status().isOk());
            mvc.perform(put(URL).cookie(cookie())).andExpect(status().isForbidden());
            mvc.perform(put(URL).cookie(cookie()).header("Origin", "https://attacker.invalid"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/v1/acceso-estudiante/cerrar-sesion").header("Origin", "null"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void credentialsCorsIsLimitedToStudentRoutesAndExplicitOrigins() throws Exception {
        try (var context = context(ORIGIN)) {
            var mvc = mvc(context);
            mvc.perform(options(URL).header("Origin", ORIGIN).header("Access-Control-Request-Method", "PUT"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
            mvc.perform(options("/v1/casos/test").header("Origin", ORIGIN).header("Access-Control-Request-Method", "PATCH"))
                    .andExpect(status().isOk()).andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
            mvc.perform(options(URL).header("Origin", "https://attacker.invalid").header("Access-Control-Request-Method", "PUT"))
                    .andExpect(status().isForbidden());
        }
        assertThrows(RuntimeException.class, () -> { try (var ignored = context("*")) {} });
    }

    private static Cookie cookie() { return new Cookie(EstudianteSessionFilter.COOKIE, "verified-fixture"); }
    private static MockMvc mvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }
    private static AnnotationConfigWebApplicationContext context(String origins) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "prisaly.cors.allowed-origins=" + origins, "prisaly.m1.student-access.enabled=true");
        context.register(TestConfig.class);
        try { context.refresh(); return context; }
        catch (RuntimeException error) { context.close(); throw error; }
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({HttpApiConfig.class, FixtureController.class})
    static class TestConfig {
        @Bean ProvisionalBearerAuthenticationFilter bearer() {
            return new ProvisionalBearerAuthenticationFilter(new ProvisionalAccessTokenStore(), mock(IdentityAccessService.class));
        }
        @Bean EstudianteSessionFilter studentFilter() { return new EstudianteSessionFilter(); }
        @Bean StudentAccessPort studentAccess() {
            return () -> {
                String token = new RequestTokenSesionEstudianteActual().actual().orElse("");
                if (!"verified-fixture".equals(token)) throw new IdentityDomainException("UNAUTHENTICATED", "Denied");
                return new AuthenticatedStudent(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.fromString("22222222-2222-2222-2222-222222222222"));
            };
        }
    }
    @RestController static class FixtureController {
        @GetMapping("/v1/estudiantes/intentos/prueba") String read() { return "student"; }
        @PutMapping("/v1/estudiantes/intentos/prueba") String write() { return "student"; }
    }
}
