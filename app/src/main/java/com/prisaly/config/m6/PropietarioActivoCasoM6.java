package com.prisaly.config.m6;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m3.api.AutoriaCasoPort;
import com.prisaly.m5.infrastructure.evaluacion.LecturaIntentoDocenteDesdeM5;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * M6-R6 (ACTA-M6-POLITICAS-55): the current teacher is <b>active</b> (M1, read live on every call) and is the
 * <b>creator of the case</b> (M3 {@link AutoriaCasoPort}, {@code Caso.idDocente}). Programming the session is not
 * enough, and no extra M1 permission is required (L-4). Explicit identity denials are {@code false}; technical
 * failures propagate and are never turned into {@code true}.
 */
final class PropietarioActivoCasoM6 implements LecturaIntentoDocenteDesdeM5.PropietarioDelCaso {
    private final IdentityAccessPort identidad;
    private final AutoriaCasoPort autoria;

    PropietarioActivoCasoM6(IdentityAccessPort identidad, AutoriaCasoPort autoria) {
        this.identidad = Objects.requireNonNull(identidad, "identidad");
        this.autoria = Objects.requireNonNull(autoria, "autoria");
    }

    /** The active authenticated user of the request, or empty when absent, unknown or inactive. */
    Optional<UUID> docenteActivo() {
        AuthenticatedUser usuario;
        try {
            usuario = identidad.currentUser(); // M1 reloads the account: an inactive one is rejected here
        } catch (IdentityDomainException denegado) {
            return Optional.empty();
        }
        if (usuario == null || usuario.userId() == null || !"Activo".equalsIgnoreCase(usuario.accountStatus())) {
            return Optional.empty();
        }
        return Optional.of(usuario.userId());
    }

    @Override
    public boolean esDocenteActivoPropietario(UUID idCaso) {
        if (idCaso == null) {
            return false;
        }
        Optional<UUID> docente = docenteActivo();
        if (docente.isEmpty()) {
            return false;
        }
        return autoria.docenteCreador(idCaso).map(docente.get()::equals).orElse(false);
    }
}
