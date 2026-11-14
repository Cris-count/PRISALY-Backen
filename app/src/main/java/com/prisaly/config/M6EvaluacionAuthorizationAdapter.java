package com.prisaly.config;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.api.SolicitudEvaluacion;
import java.util.Objects;

/**
 * Patrón Adapter, cerrado en negativo. Traduce el puerto de autorización de M6
 * a la identidad de M1. No existe todavía un permiso de evaluación ratificado,
 * así que verificarAcceso() responde DENEGADO aunque el personal esté activo.
 * Un fallo inesperado de identidad responde NO_DISPONIBLE, nunca AUTORIZADO.
 */
public final class M6EvaluacionAuthorizationAdapter implements AutorizacionEvaluacionPort {
    private final IdentityAccessPort identity;

    public M6EvaluacionAuthorizationAdapter(IdentityAccessPort identity) {
        this.identity = Objects.requireNonNull(identity);
    }

    @Override
    public DecisionAcceso verificarAcceso(SolicitudEvaluacion solicitud) {
        if (solicitud == null || solicitud.idIntento() == null || solicitud.idRubrica() == null) {
            return DecisionAcceso.DENEGADO;
        }
        try {
            AuthenticatedUser user = identity.currentUser();
            if (user == null || user.userId() == null || !isActive(user)) {
                return DecisionAcceso.DENEGADO;
            }
            return DecisionAcceso.DENEGADO;
        } catch (IdentityDomainException ex) {
            return DecisionAcceso.DENEGADO;
        } catch (RuntimeException ex) {
            return DecisionAcceso.NO_DISPONIBLE;
        }
    }

    private static boolean isActive(AuthenticatedUser user) {
        return "Activo".equalsIgnoreCase(user.accountStatus());
    }
}
