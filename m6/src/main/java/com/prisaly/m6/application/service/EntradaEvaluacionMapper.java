package com.prisaly.m6.application.service;

import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.ContextoEvaluacionPreparado;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion.OpcionEvaluable;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion.PreguntaEvaluable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the strategy input from the already prepared and authorized context (C2, DD-03 contract only).
 *
 * <p>It only copies and cross-checks references: scenarios and questions keep the order of the academic
 * context; answers are joined by {@code idPregunta}; the per-question association weight and the rubric's
 * opaque definition are passed as found. It computes nothing. Any structural incoherence (another case,
 * an answer or rubric association to a question outside the case, an option of another question, duplicates)
 * fails with {@link IllegalStateException} before the strategy runs, so nothing is evaluated or written.
 */
final class EntradaEvaluacionMapper {

    private EntradaEvaluacionMapper() {
    }

    static EntradaEvaluacion mapear(SolicitudEvaluacion solicitud, ContextoEvaluacionPreparado preparado) {
        Objects.requireNonNull(solicitud, "solicitud");
        Objects.requireNonNull(preparado, "preparado");
        IntentoEvaluacion intento = preparado.intento();
        ContextoAcademico contexto = preparado.contextoAcademico();
        ContextoAcademico.Rubrica rubrica = contexto.rubrica();
        UUID idCaso = contexto.idCaso();
        exigir(rubrica != null, "sin rubrica");
        exigir(solicitud.idIntento().equals(intento.idIntento()), "el intento preparado no es el solicitado");
        exigir(solicitud.idRubrica().equals(rubrica.idRubrica()), "la rubrica preparada no es la solicitada");
        exigir(idCaso.equals(intento.idCaso()) && idCaso.equals(rubrica.idCaso()), "caso distinto");
        exigir(intento.estado() == IntentoEvaluacion.EstadoIntento.FINALIZADO, "el intento no esta finalizado");

        Map<UUID, ContextoAcademico.Pregunta> preguntasDelCaso = new HashMap<>();
        for (ContextoAcademico.Escenario escenario : contexto.escenarios()) {
            exigir(idCaso.equals(escenario.idCaso()), "escenario de otro caso");
            for (ContextoAcademico.Pregunta pregunta : escenario.preguntas()) {
                exigir(escenario.idEscenario().equals(pregunta.idEscenario()), "pregunta de otro escenario");
                exigir(preguntasDelCaso.put(pregunta.idPregunta(), pregunta) == null, "pregunta duplicada");
                for (ContextoAcademico.Opcion opcion : pregunta.opciones()) {
                    exigir(pregunta.idPregunta().equals(opcion.idPregunta()), "opcion de otra pregunta");
                }
            }
        }

        Map<UUID, BigDecimal> valorPorPregunta = new HashMap<>();
        for (ContextoAcademico.RubricaPregunta asociacion : rubrica.preguntas()) {
            exigir(rubrica.idRubrica().equals(asociacion.idRubrica()), "asociacion de otra rubrica");
            exigir(preguntasDelCaso.containsKey(asociacion.idPregunta()), "asociacion a una pregunta ajena al caso");
            exigir(valorPorPregunta.put(asociacion.idPregunta(), asociacion.valorPregunta()) == null,
                    "pregunta asociada dos veces a la rubrica");
        }

        Map<UUID, UUID> seleccionPorPregunta = new HashMap<>();
        for (IntentoEvaluacion.RespuestaReferencia respuesta : intento.respuestas()) {
            ContextoAcademico.Pregunta pregunta = preguntasDelCaso.get(respuesta.idPregunta());
            exigir(pregunta != null, "respuesta a una pregunta ajena al caso");
            exigir(pregunta.opciones().stream().anyMatch(o -> o.idOpcion().equals(respuesta.idOpcionSeleccionada())),
                    "opcion respondida ajena a la pregunta");
            exigir(seleccionPorPregunta.put(respuesta.idPregunta(), respuesta.idOpcionSeleccionada()) == null,
                    "pregunta respondida dos veces");
        }

        List<PreguntaEvaluable> preguntas = new ArrayList<>();
        for (ContextoAcademico.Escenario escenario : contexto.escenarios()) {
            for (ContextoAcademico.Pregunta pregunta : escenario.preguntas()) {
                List<OpcionEvaluable> opciones = pregunta.opciones().stream()
                        .map(o -> new OpcionEvaluable(o.idOpcion(), o.esCorrecta())).toList();
                preguntas.add(new PreguntaEvaluable(pregunta.idPregunta(), escenario.idEscenario(),
                        escenario.activo(), pregunta.visible(),
                        Optional.ofNullable(valorPorPregunta.get(pregunta.idPregunta())), opciones,
                        Optional.ofNullable(seleccionPorPregunta.get(pregunta.idPregunta()))));
            }
        }
        return new EntradaEvaluacion(solicitud, idCaso, preguntas, Optional.ofNullable(rubrica.definicionEvaluacion()));
    }

    private static void exigir(boolean condicion, String motivo) {
        if (!condicion) {
            throw new IllegalStateException("Contexto de evaluacion incoherente: " + motivo);
        }
    }
}
