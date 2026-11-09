package com.prisaly.config;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.domain.model.CasoEstado;
import java.util.Objects;
import java.util.UUID;

/**
 * Institutional M3 read policy over M1 identity (DC-M3-RES-02). Ownership stays in M3
 * ({@code CasoDocenteConsultaService}); this adapter never reads M3 repositories.
 * Explicit denials return DENEGADO; technical failures return NO_DISPONIBLE, never AUTORIZADO.
 * CREAR_CASO is not a read permission.
 */
public final class M3CaseReadAuthorizationAdapter implements AutorizacionConsultaCasoPort {
    private final IdentityAccessPort identity;
    private final PoliticaLecturaCaso politica;

    public M3CaseReadAuthorizationAdapter(IdentityAccessPort identity, PoliticaLecturaCaso politica) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    @Override
    public Decision verificarAcceso(UUID usuario, UUID caso) {
        if (usuario == null || caso == null || !politica.ratificada()) {
            return Decision.DENEGADO;
        }
        return conIdentidad(usuario, user -> identity.hasPermission(user.userId(), politica.permiso().orElseThrow()));
    }

    @Override
    public Decision verificarVisibilidad(UUID usuario, UUID caso, CasoEstado estado, boolean activo) {
        if (usuario == null || caso == null || !politica.visible(estado, activo)) {
            return Decision.DENEGADO;
        }
        return conIdentidad(usuario, user -> true);
    }

    // The identity always comes from the authenticated M1 request; a caller-supplied id that differs
    // from it is a spoofing attempt and is denied.
    private Decision conIdentidad(UUID usuario, java.util.function.Predicate<AuthenticatedUser> regla) {
        try {
            AuthenticatedUser current = identity.currentUser();
            if (current == null || current.userId() == null || !current.userId().equals(usuario)
                    || !"Activo".equalsIgnoreCase(current.accountStatus())) {
                return Decision.DENEGADO;
            }
            return regla.test(current) ? Decision.AUTORIZADO : Decision.DENEGADO;
        } catch (IdentityDomainException denied) {
            return Decision.DENEGADO;
        } catch (RuntimeException technicalFailure) {
            return Decision.NO_DISPONIBLE;
        }
    }
}
