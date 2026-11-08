package com.prisaly.m1.infrastructure.security;

import com.prisaly.m1.application.port.TokenSesionEstudianteActual;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@ConditionalOnProperty(name = "prisaly.m1.student-access.enabled", havingValue = "true")
public class RequestTokenSesionEstudianteActual implements TokenSesionEstudianteActual {

    @Override
    public Optional<String> actual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return Optional.empty();
        }
        HttpServletRequest request = atributos.getRequest();
        Object valor = request.getAttribute(ATRIBUTO);
        if (valor instanceof String token && !token.isBlank()) {
            return Optional.of(token);
        }
        return Optional.empty();
    }
}
