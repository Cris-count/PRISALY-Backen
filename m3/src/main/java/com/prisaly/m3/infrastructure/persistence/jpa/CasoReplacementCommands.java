package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.application.update.CasoUpdateCommand;
import com.prisaly.m3.application.update.CasoUpdateCommand.*;
import com.prisaly.m3.domain.model.Caso;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Converts a replacement only into existing field commands, never structural mutations. */
final class CasoReplacementCommands {
    private CasoReplacementCommands() { }

    static List<CasoUpdateCommand> between(Caso before, Caso requested) {
        require(before.id().equals(requested.id()) && before.docenteId().equals(requested.docenteId()),
                "Case identity and owner must remain unchanged");
        require(before.estado() == requested.estado() && before.activo() == requested.activo()
                && before.contenidoEvaluableBloqueado() == requested.contenidoEvaluableBloqueado()
                && Objects.equals(before.fechaCreacion(), requested.fechaCreacion())
                && Objects.equals(before.fechaActualizacion(), requested.fechaActualizacion())
                && Objects.equals(before.fechaBloqueoAcademico(), requested.fechaBloqueoAcademico()),
                "Lifecycle, activity and audit metadata cannot be replaced");
        require(Objects.equals(before.rubrica(), requested.rubrica()), "Rubric replacement is not supported");
        require(before.escenarios().size() == requested.escenarios().size(), "Scenario membership cannot be replaced");
        List<CasoUpdateCommand> commands = new ArrayList<>();
        if (!before.titulo().equals(requested.titulo())) {
            commands.add(new ActualizarDatosCasoCommand(CampoCaso.NOMBRE, requested.titulo()));
        }
        if (!Objects.equals(before.historia(), requested.historia())) {
            commands.add(new ActualizarDatosCasoCommand(CampoCaso.HISTORIA, requested.historia()));
        }
        if (!Objects.equals(before.desarrolloSituacional(), requested.desarrolloSituacional())) {
            commands.add(new ActualizarDatosCasoCommand(CampoCaso.DESARROLLO_SITUACIONAL, requested.desarrolloSituacional()));
        }
        if (before.tiempoEstimadoSegundos() != requested.tiempoEstimadoSegundos()) {
            commands.add(new ActualizarDatosCasoCommand(CampoCaso.TIEMPO_ESTIMADO_SEGUNDOS, requested.tiempoEstimadoSegundos()));
        }
        for (int i = 0; i < before.escenarios().size(); i++) {
            var old = before.escenarios().get(i);
            var next = requested.escenarios().get(i);
            require(old.id().equals(next.id()) && old.activo() == next.activo()
                    && Objects.equals(old.descripcion(), next.descripcion()) && Objects.equals(old.orden(), next.orden()),
                    "Scenario identity, membership, activity and order must remain unchanged");
            var id = CasoPersistenceMapper.uuid(old.id());
            if (!Objects.equals(old.titulo(), next.titulo())) { commands.add(new ActualizarEscenarioCommand(id, CampoEscenario.TITULO, next.titulo())); }
            if (!Objects.equals(old.contexto(), next.contexto())) { commands.add(new ActualizarEscenarioCommand(id, CampoEscenario.CONTEXTO, next.contexto())); }
            if (!Objects.equals(old.instrucciones(), next.instrucciones())) { commands.add(new ActualizarEscenarioCommand(id, CampoEscenario.INSTRUCCIONES, next.instrucciones())); }
            require(old.preguntas().size() == next.preguntas().size(), "Question membership cannot be replaced");
            for (int j = 0; j < old.preguntas().size(); j++) {
                var question = old.preguntas().get(j);
                var replacement = next.preguntas().get(j);
                require(question.id().equals(replacement.id()) && question.opciones().equals(replacement.opciones())
                        && question.estadoEdicion().equals(replacement.estadoEdicion()) && question.visible() == replacement.visible()
                        && Objects.equals(question.orden(), replacement.orden())
                        && Objects.equals(question.fechaCreacion(), replacement.fechaCreacion())
                        && Objects.equals(question.fechaActualizacion(), replacement.fechaActualizacion()),
                        "Question identity, options, editing state, order and dates must remain unchanged");
                var questionId = CasoPersistenceMapper.uuid(question.id());
                if (!question.enunciado().equals(replacement.enunciado())) { commands.add(new ActualizarPreguntaCommand(questionId, CampoPregunta.ENUNCIADO, replacement.enunciado())); }
                if (!Objects.equals(question.textoAyuda(), replacement.textoAyuda())) { commands.add(new ActualizarPreguntaCommand(questionId, CampoPregunta.TEXTO_AYUDA, replacement.textoAyuda())); }
            }
        }
        return List.copyOf(commands);
    }

    private static void require(boolean valid, String message) {
        if (!valid) { throw new IllegalArgumentException(message); }
    }
}
