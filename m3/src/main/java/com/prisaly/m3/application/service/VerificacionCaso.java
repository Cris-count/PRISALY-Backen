package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Identidad y permiso previos a escribir un caso: un fallo técnico nunca concede acceso. */
final class VerificacionCaso {
    private static final String NO_DISPONIBLE = "CASE_AUTHORIZATION_UNAVAILABLE";

    private VerificacionCaso() {
    }

    static UUID usuarioActual(CasoCurrentUserPort identity) {
        UUID user;
        try {
            user = identity == null ? null : identity.currentUserId();
        } catch (CasoAuthenticationException | CasoDomainException classified) {
            throw classified;
        } catch (RuntimeException technicalFailure) {
            throw noDisponible();
        }
        if (user == null) {
            throw new CasoAuthenticationException();
        }
        return user;
    }

    static void exigir(BooleanSupplier permitido, String mensajeDenegado) {
        boolean allowed;
        try {
            allowed = permitido.getAsBoolean();
        } catch (CasoDomainException classified) {
            if (CasoApplicationService.CODE_FORBIDDEN.equals(classified.code())) {
                throw denegado(mensajeDenegado);
            }
            throw noDisponible();
        } catch (RuntimeException technicalFailure) {
            throw noDisponible();
        }
        if (!allowed) {
            throw denegado(mensajeDenegado);
        }
    }

    static CasoDomainException denegado(String mensaje) {
        return new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, mensaje);
    }

    static CasoDomainException noDisponible() {
        return new CasoDomainException(NO_DISPONIBLE, "Institutional verification unavailable");
    }
}
