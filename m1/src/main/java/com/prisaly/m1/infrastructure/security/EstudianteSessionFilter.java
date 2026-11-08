package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copia la cookie estudiantil a la petición. No escribe el SecurityContext del personal.
 */
@Component
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class EstudianteSessionFilter extends OncePerRequestFilter {

    public static final String COOKIE = "prisaly_estudiante";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = leer(request);
        if (token != null) {
            request.setAttribute(TokenSesionEstudianteActual.ATRIBUTO, token);
        }
        filterChain.doFilter(request, response);
    }

    private static String leer(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
