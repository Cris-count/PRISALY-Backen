package com.prisaly.m1.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class EstudianteSessionFilterTest {

    @AfterEach
    void limpia() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void laCookieNoSeConvierteEnPrincipalDePersonal() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID(), "bearer-personal"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(EstudianteSessionFilter.COOKIE, "token-opaco"));
        new EstudianteSessionFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertEquals("token-opaco", request.getAttribute(TokenSesionEstudianteActual.ATRIBUTO));
        assertEquals("bearer-personal", SecurityContextHolder.getContext().getAuthentication().getCredentials());
    }

    @Test
    void sinCookieNoHayToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        new EstudianteSessionFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(request.getAttribute(TokenSesionEstudianteActual.ATRIBUTO));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
