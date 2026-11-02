package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.api.ContextoAcademicoPersistido;
import com.prisaly.m3.api.ContextoAcademicoPersistido.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Package-only pure mapper. No bean, authorization credential or database entry point. */
final class ContextoAcademicoProducerMapper {
    private ContextoAcademicoProducerMapper() { }

    static ContextoAcademicoPersistido map(CasoJpaAggregateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var root = snapshot.caso();
        var scenarios = index(snapshot.escenarios(), CasoJpaAggregateSnapshot.EscenarioRow::idEscenario);
        var questions = index(snapshot.preguntas(), CasoJpaAggregateSnapshot.PreguntaRow::idPregunta);
        index(snapshot.opciones(), CasoJpaAggregateSnapshot.OpcionRespuestaRow::idOpcion);
        Map<UUID, Set<Integer>> scenarioOrders = new HashMap<>();
        Map<UUID, Set<Integer>> questionOrders = new HashMap<>();
        Map<UUID, Set<Integer>> optionOrders = new HashMap<>();
        Set<UUID> correctQuestions = new HashSet<>();
        for (var scenario : snapshot.escenarios()) {
            require(root.idCaso().equals(scenario.idCaso()), "Scenario belongs to another case");
            order(scenarioOrders, scenario.idCaso(), scenario.orden());
        }
        for (var question : snapshot.preguntas()) {
            require(scenarios.containsKey(question.idEscenario()), "Question has no scenario in this case");
            order(questionOrders, question.idEscenario(), question.orden());
        }
        for (var option : snapshot.opciones()) {
            require(questions.containsKey(option.idPregunta()), "Option has no question in this case");
            order(optionOrders, option.idPregunta(), option.orden());
            // V002's partial UNIQUE index, not a requirement that a question have a correct option.
            if (option.esCorrecta()) {
                require(correctQuestions.add(option.idPregunta()), "Multiple correct options violate V002");
            }
        }
        Rubrica rubric = null;
        if (snapshot.rubrica() == null) {
            require(snapshot.rubricaPreguntas().isEmpty(), "Associations without a rubric");
        } else {
            var row = snapshot.rubrica();
            require(root.idCaso().equals(row.idCaso()), "Rubric belongs to another case");
            index(snapshot.rubricaPreguntas().stream().toList(), CasoJpaAggregateSnapshot.RubricaPreguntaRow::idRubricaPregunta);
            Set<UUID> associatedQuestions = new HashSet<>();
            for (var association : snapshot.rubricaPreguntas()) {
                require(row.idRubrica().equals(association.idRubrica()), "Association belongs to another rubric");
                require(questions.containsKey(association.idPregunta()), "Rubric question is outside the case");
                require(associatedQuestions.add(association.idPregunta()), "Duplicate rubric question");
            }
            rubric = new Rubrica(row.idRubrica(), row.idCaso(), row.nombre(), row.descripcion(),
                    row.puntajeTotal(), row.definicionEvaluacion(), row.fechaCreacion(),
                    snapshot.rubricaPreguntas().stream().map(a -> new RubricaPregunta(a.idRubricaPregunta(),
                            a.idRubrica(), a.idPregunta(), a.valorPregunta(), a.criterio()))
                            .collect(Collectors.toUnmodifiableSet()));
        }
        List<Escenario> nested = snapshot.escenarios().stream()
                .sorted(Comparator.comparingInt(CasoJpaAggregateSnapshot.EscenarioRow::orden))
                .map(e -> new Escenario(e.idEscenario(), e.idCaso(), e.titulo(), e.contexto(), e.instrucciones(),
                        e.orden(), e.activo(), snapshot.preguntas().stream().filter(p -> p.idEscenario().equals(e.idEscenario()))
                                .sorted(Comparator.comparingInt(CasoJpaAggregateSnapshot.PreguntaRow::orden))
                                .map(p -> new Pregunta(p.idPregunta(), p.idEscenario(), p.enunciado(), p.textoAyuda(),
                                        p.estadoEdicion(), p.visible(), p.orden(), p.fechaCreacion(), p.fechaActualizacion(),
                                        snapshot.opciones().stream().filter(o -> o.idPregunta().equals(p.idPregunta()))
                                                .sorted(Comparator.comparingInt(CasoJpaAggregateSnapshot.OpcionRespuestaRow::orden))
                                                .map(o -> new Opcion(o.idOpcion(), o.idPregunta(), o.texto(), o.esCorrecta(),
                                                        o.explicacion(), o.orden())).toList())).toList())).toList();
        return new ContextoAcademicoPersistido(root.idCaso(), root.idDocente(), root.nombre(), root.historia(),
                root.desarrolloSituacional(), root.tiempoEstimadoSegundos(), root.estado(), root.activo(),
                root.fechaBloqueoAcademico(), root.fechaCreacion(), root.fechaActualizacion(), nested, rubric);
    }

    private static <T> Map<UUID, T> index(List<T> rows, Function<T, UUID> id) {
        Map<UUID, T> result = new HashMap<>();
        for (T row : rows) {
            UUID key = id.apply(row);
            require(key != null && result.putIfAbsent(key, row) == null, "Missing or duplicate row UUID");
        }
        return result;
    }

    private static void order(Map<UUID, Set<Integer>> orders, UUID parent, int value) {
        require(value > 0 && orders.computeIfAbsent(parent, ignored -> new HashSet<>()).add(value),
                "Invalid or duplicate physical order");
    }

    private static void require(boolean valid, String message) {
        if (!valid) { throw new IllegalStateException("Academic snapshot integrity: " + message); }
    }
}
