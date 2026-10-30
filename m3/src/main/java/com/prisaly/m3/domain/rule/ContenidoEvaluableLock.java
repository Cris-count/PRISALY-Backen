package com.prisaly.m3.domain.rule;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import java.util.List;

/**
 * F3-03: after the first session is scheduled, evaluable content is locked.
 * Later edits require an independent draft copy.
 */
public final class ContenidoEvaluableLock {

    public static final String CODE_EVALUABLE_CONTENT_LOCKED = "EVALUABLE_CONTENT_LOCKED";

    private ContenidoEvaluableLock() {
    }

    public static Caso lockAfterFirstSessionScheduled(Caso caso) {
        if (caso.contenidoEvaluableBloqueado()) {
            return caso;
        }
        return new Caso(
                caso.id(),
                caso.titulo(),
                caso.docenteId(),
                caso.estado(),
                caso.escenarios(),
                caso.rubrica(),
                true);
    }

    public static void assertEditable(Caso caso) {
        if (caso.contenidoEvaluableBloqueado()) {
            throw new CasoDomainException(
                    CODE_EVALUABLE_CONTENT_LOCKED,
                    "Evaluable content of case " + caso.id()
                            + " is locked after the first scheduled session");
        }
    }

    public static Caso createIndependentCopy(Caso source, String newId) {
        return new Caso(
                newId,
                source.titulo(),
                source.docenteId(),
                CasoEstado.BORRADOR,
                List.copyOf(source.escenarios()),
                source.rubrica(),
                false);
    }
}
