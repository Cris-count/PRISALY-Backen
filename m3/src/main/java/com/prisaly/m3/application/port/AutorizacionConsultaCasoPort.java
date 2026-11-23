package com.prisaly.m3.application.port;

import com.prisaly.m3.domain.model.CasoEstado;
import java.util.UUID;

/** M3 read policy boundary. No permission code or state visibility is granted locally. */
public interface AutorizacionConsultaCasoPort {
    enum Decision { AUTORIZADO, DENEGADO, NO_DISPONIBLE }

    /** Institutional eligibility for this read, checked before repository access. */
    Decision verificarAcceso(UUID usuario, UUID caso);

    /** Explicit resource visibility, after ownership verification, without academic content. */
    Decision verificarVisibilidad(UUID usuario, UUID caso, CasoEstado estado, boolean activo);

    /**
     * DC-BE-CIERRE-FUNCIONAL-NOCTURNO-67 (candidate listing): institutional eligibility to list the user's own cases,
     * the same permission as {@link #verificarAcceso} but without a case. Fails closed unless an adapter overrides it.
     */
    default Decision verificarListado(UUID usuario) {
        return Decision.DENEGADO;
    }
}
