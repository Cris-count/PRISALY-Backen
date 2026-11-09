package com.prisaly.m3.presentation.dto;

import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.OpcionRespuesta;
import com.prisaly.m3.domain.model.Pregunta;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Teacher view of the complete evaluable content, including correctness and explanations.
 * Authorized callers only. Owner, rubric and audit data are deliberately not part of it.
 */
public record CasoContenidoResponse(UUID idCaso, String titulo, int tiempoEstimadoSegundos, String estado,
        String historia, String desarrolloSituacional, List<EscenarioContenido> escenarios) {

    public record EscenarioContenido(String idEscenario, String titulo, String contexto, String instrucciones,
            Integer orden, boolean activo, List<PreguntaContenido> preguntas) {
        public static EscenarioContenido from(Escenario e) {
            return new EscenarioContenido(e.id(), e.titulo(), e.contexto(), e.instrucciones(), e.orden(), e.activo(),
                    e.preguntas().stream().sorted(Comparator.comparing(Pregunta::orden, ORDEN))
                            .map(PreguntaContenido::from).toList());
        }
    }

    public record PreguntaContenido(String idPregunta, String enunciado, String textoAyuda, String estadoEdicion,
            boolean visible, Integer orden, List<OpcionContenido> opciones) {
        public static PreguntaContenido from(Pregunta p) {
            return new PreguntaContenido(p.id(), p.enunciado(), p.textoAyuda(), p.estadoEdicion(), p.visible(), p.orden(),
                    opcionesDe(p));
        }
    }

    public record OpcionContenido(String idOpcion, String texto, boolean esCorrecta, String explicacion, Integer orden) {
        public static OpcionContenido from(OpcionRespuesta o) {
            return new OpcionContenido(o.id(), o.texto(), o.esCorrecta(), o.explicacion(), o.orden());
        }
    }

    private static final Comparator<Integer> ORDEN = Comparator.nullsLast(Comparator.naturalOrder());

    public static CasoContenidoResponse from(Caso caso) {
        return new CasoContenidoResponse(UUID.fromString(caso.id()), caso.titulo(), caso.tiempoEstimadoSegundos(),
                caso.estado().name(), caso.historia(), caso.desarrolloSituacional(), escenariosDe(caso));
    }

    public static List<EscenarioContenido> escenariosDe(Caso caso) {
        return caso.escenarios().stream().sorted(Comparator.comparing(Escenario::orden, ORDEN))
                .map(EscenarioContenido::from).toList();
    }

    public static List<OpcionContenido> opcionesDe(Pregunta pregunta) {
        return pregunta.opciones().stream().sorted(Comparator.comparing(OpcionRespuesta::orden, ORDEN))
                .map(OpcionContenido::from).toList();
    }
}
