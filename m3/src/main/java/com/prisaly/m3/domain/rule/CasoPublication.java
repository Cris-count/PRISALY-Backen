package com.prisaly.m3.domain.rule;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;

/**
 * Transitions a validated draft case to published state.
 */
public final class CasoPublication {

    public static final String CODE_ALREADY_PUBLISHED = "ALREADY_PUBLISHED";

    private CasoPublication() {
    }

    public static Caso publish(Caso caso) {
        if (caso.estado() == CasoEstado.PUBLICADO) {
            throw new CasoDomainException(
                    CODE_ALREADY_PUBLISHED,
                    "Case " + caso.id() + " is already published");
        }
        CasoValidation.assertPublishable(caso);
        return new Caso(
                caso.id(),
                caso.titulo(),
                caso.docenteId(),
                caso.tiempoEstimadoSegundos(),
                CasoEstado.PUBLICADO,
                caso.escenarios(),
                caso.rubrica(),
                caso.contenidoEvaluableBloqueado());
    }
}
