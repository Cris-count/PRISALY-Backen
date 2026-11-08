package com.prisaly.m3.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CasoPersistenceMapperIntegrityTest {
    @Test
    void rubricAssociationsWithoutTheirRubricCannotBeSilentlyLostDuringReconstruction() {
        var source = ContextoAcademicoProducerMapperTest.full();
        var orphan = new RubricaPreguntaRow(UUID.randomUUID(), UUID.randomUUID(),
                source.preguntas().getFirst().idPregunta(), new java.math.BigDecimal("1.25"), "Original criterion");
        var inconsistent = new CasoJpaAggregateSnapshot(source.caso(), source.escenarios(), source.preguntas(),
                source.opciones(), null, Set.of(orphan));
        assertThrows(IllegalStateException.class, () -> CasoPersistenceMapper.toDomain(inconsistent));
    }

    @Test
    void scenarioFromAnotherCaseCannotBeAttachedToTheReconstructedCase() {
        var source = ContextoAcademicoProducerMapperTest.full();
        var scenario = source.escenarios().getFirst();
        var invalid = new CasoJpaAggregateSnapshot(source.caso(),
                List.of(new EscenarioRow(scenario.idEscenario(), UUID.randomUUID(), scenario.titulo(),
                        scenario.contexto(), scenario.instrucciones(), scenario.orden(), scenario.activo())),
                source.preguntas(), source.opciones(), null, Set.of());
        assertThrows(IllegalStateException.class, () -> CasoPersistenceMapper.toDomain(invalid));
    }

    @Test
    void orphanQuestionCannotBeSilentlyDroppedEvenWithoutARubric() {
        var source = ContextoAcademicoProducerMapperTest.full();
        var question = source.preguntas().getFirst();
        var invalid = new CasoJpaAggregateSnapshot(source.caso(), source.escenarios(),
                List.of(new PreguntaRow(question.idPregunta(), UUID.randomUUID(), question.enunciado(),
                        question.textoAyuda(), question.estadoEdicion(), question.visible(), question.orden(),
                        question.fechaCreacion(), question.fechaActualizacion())), source.opciones(), null, Set.of());
        assertThrows(IllegalStateException.class, () -> CasoPersistenceMapper.toDomain(invalid));
    }

    @Test
    void orphanOptionCannotBeSilentlyDroppedAlongsideValidContent() {
        var source = ContextoAcademicoProducerMapperTest.full();
        var validOption = source.opciones().getFirst();
        var invalid = new CasoJpaAggregateSnapshot(source.caso(), source.escenarios(), source.preguntas(),
                List.of(validOption, new OpcionRespuestaRow(UUID.randomUUID(), UUID.randomUUID(),
                        "Orphan option", false, null, 29)), null, Set.of());
        assertThrows(IllegalStateException.class, () -> CasoPersistenceMapper.toDomain(invalid));
        var valid = CasoPersistenceMapper.toDomain(source);
        assertEquals(validOption.idOpcion().toString(), valid.escenarios().getFirst()
                .preguntas().getFirst().opciones().getFirst().id());
        assertEquals(1, valid.escenarios().getFirst().preguntas().getFirst().opciones().size());
    }
}
