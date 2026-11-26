package com.prisaly.m3.domain.rule;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.Pregunta;

/**
 * Transitions a validated draft case to published state.
 *
 * <p>DC-111: questions stay {@code Borrador} while the case is a draft. Publishing validates every question first
 * ({@link CasoValidation#assertPublishable}); only when all of them are valid does each {@code Borrador} question
 * become {@code Lista}. An invalid question rejects the whole publication, so there is never a partial transition.
 * Options, rubric and associations are carried unchanged.
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
                escenariosListos(caso),
                caso.rubrica(),
                caso.contenidoEvaluableBloqueado(),
                caso.historia(),
                caso.desarrolloSituacional(),
                caso.activo(),
                caso.fechaCreacion(),
                caso.fechaActualizacion(),
                caso.fechaBloqueoAcademico());
    }

    /** DC-111: every question of the (already validated) case becomes {@code Lista}; nothing else changes. */
    static java.util.List<Escenario> escenariosListos(Caso caso) {
        return caso.escenarios().stream().map(e -> new Escenario(e.id(), e.descripcion(),
                e.preguntas().stream().map(CasoPublication::lista).toList(),
                e.titulo(), e.contexto(), e.instrucciones(), e.orden(), e.activo())).toList();
    }

    private static Pregunta lista(Pregunta p) {
        return "Lista".equals(p.estadoEdicion()) ? p : new Pregunta(p.id(), p.enunciado(), p.opciones(), p.textoAyuda(),
                "Lista", p.visible(), p.orden(), p.fechaCreacion(), p.fechaActualizacion());
    }
}
