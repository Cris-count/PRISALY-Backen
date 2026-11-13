package com.prisaly.m6.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.ContextoEvaluacionPreparado;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.integration.IntentoEvaluacion.RespuestaReferencia;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion.OpcionEvaluable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** C2: the mapper copies and cross-checks references; it never computes, filters or weighs anything. */
class EntradaEvaluacionMapperTest {
    final ContextoPreparadoPrueba f = new ContextoPreparadoPrueba();

    @Test
    void conservaOrdenDelContextoYTodasLasPreguntasSinFiltrar() {
        EntradaEvaluacion entrada = EntradaEvaluacionMapper.mapear(f.solicitud(), f.preparado());
        assertEquals(f.solicitud(), entrada.solicitud());
        assertEquals(f.caso, entrada.idCaso());
        assertEquals(List.of(f.pregunta1, f.pregunta2, f.pregunta3),
                entrada.preguntas().stream().map(EntradaEvaluacion.PreguntaEvaluable::idPregunta).toList());
        var p2 = entrada.preguntas().get(1);
        var p3 = entrada.preguntas().get(2);
        assertFalse(p2.visible(), "the visible flag is passed through, not used to filter");
        assertFalse(p3.escenarioActivo(), "the scenario flag is passed through, not used to filter");
        assertEquals(f.escenario2, p3.idEscenario());
        assertEquals(List.of(new OpcionEvaluable(f.opcion1a, false), new OpcionEvaluable(f.opcion1b, true)),
                entrada.preguntas().get(0).opciones());
    }

    @Test
    void preguntaSinResponderTieneSeleccionVaciaYLaRespondidaConservaLaOpcion() {
        var entrada = EntradaEvaluacionMapper.mapear(f.solicitud(), f.preparado());
        assertEquals(Optional.of(f.opcion1b), entrada.preguntas().get(0).opcionSeleccionada());
        assertEquals(Optional.empty(), entrada.preguntas().get(1).opcionSeleccionada());
        assertEquals(Optional.empty(), entrada.preguntas().get(2).opcionSeleccionada());
    }

    @Test
    void losDosOrigenesDePonderacionSeEntreganTalCualSinInterpretarse() {
        var entrada = EntradaEvaluacionMapper.mapear(f.solicitud(), f.preparado());
        // Origin 1: RubricaPregunta.valorPregunta, exact decimal; empty when there is no association.
        assertEquals(Optional.of(f.valorPregunta1), entrada.preguntas().get(0).valorPregunta());
        assertSame(f.valorPregunta1, entrada.preguntas().get(0).valorPregunta().orElseThrow());
        assertEquals(Optional.empty(), entrada.preguntas().get(1).valorPregunta());
        // Origin 2: the rubric's opaque definicionEvaluacion, byte for byte; empty when not persisted.
        assertEquals(Optional.of(f.definicion), entrada.definicionEvaluacionRubrica());
        var sinDefinicion = EntradaEvaluacionMapper.mapear(f.solicitud(), f.preparado(
                List.of(), null, Set.of()));
        assertEquals(Optional.empty(), sinDefinicion.definicionEvaluacionRubrica());
        assertTrue(sinDefinicion.preguntas().stream().allMatch(p -> p.valorPregunta().isEmpty()));
    }

    @Test
    void laEntradaEsInmutable() {
        var entrada = EntradaEvaluacionMapper.mapear(f.solicitud(), f.preparado());
        assertThrows(UnsupportedOperationException.class, () -> entrada.preguntas().clear());
        assertThrows(UnsupportedOperationException.class, () -> entrada.preguntas().get(0).opciones().clear());
        var lista = new java.util.ArrayList<EntradaEvaluacion.PreguntaEvaluable>(entrada.preguntas());
        var copia = new EntradaEvaluacion(entrada.solicitud(), entrada.idCaso(), lista,
                entrada.definicionEvaluacionRubrica());
        lista.clear();
        assertEquals(3, copia.preguntas().size(), "defensive copy of the supplied list");
    }

    @Test
    void contextoIncoherenteSeRechazaAntesDeLaEstrategia() {
        // Answer to a question outside the case.
        assertIncoherente(f.solicitud(), f.preparado(
                List.of(new RespuestaReferencia(UUID.randomUUID(), f.opcion1a)), f.definicion, Set.of()));
        // Answer with an option of another question.
        assertIncoherente(f.solicitud(), f.preparado(
                List.of(new RespuestaReferencia(f.pregunta1, f.opcion2a)), f.definicion, Set.of()));
        // Rubric association to a question outside the case.
        assertIncoherente(f.solicitud(), f.preparado(
                List.of(), f.definicion, Set.of(f.asociacion(UUID.randomUUID(), BigDecimal.ONE))));
        // Same question associated twice to the rubric.
        assertIncoherente(f.solicitud(), f.preparado(List.of(), f.definicion,
                Set.of(f.asociacion(f.pregunta1, BigDecimal.ONE), f.asociacion(f.pregunta1, BigDecimal.TEN))));
        // Request for another attempt or another rubric than the prepared one.
        assertIncoherente(new SolicitudEvaluacion(UUID.randomUUID(), f.rubrica), f.preparado());
        assertIncoherente(new SolicitudEvaluacion(f.intento, UUID.randomUUID()), f.preparado());
    }

    @Test
    void intentoDeOtroCasoONoFinalizadoSeRechaza() {
        var base = f.preparado();
        var otroCaso = new IntentoEvaluacion(f.intento, f.participacion, UUID.randomUUID(),
                IntentoEvaluacion.EstadoIntento.FINALIZADO, List.of());
        assertIncoherente(f.solicitud(), new ContextoEvaluacionPreparado(otroCaso, base.contextoAcademico()));
        var enCurso = new IntentoEvaluacion(f.intento, f.participacion, f.caso,
                IntentoEvaluacion.EstadoIntento.EN_CURSO, List.of());
        assertIncoherente(f.solicitud(), new ContextoEvaluacionPreparado(enCurso, base.contextoAcademico()));
        ContextoAcademico ctx = base.contextoAcademico();
        var sinRubrica = new ContextoAcademico(ctx.idCaso(), ctx.idDocente(), ctx.nombre(), null, null, 600,
                "Publicado", true, null, null, null, ctx.escenarios(), null);
        assertIncoherente(f.solicitud(), new ContextoEvaluacionPreparado(base.intento(), sinRubrica));
    }

    @Test
    void elDominioRechazaSeleccionesYDuplicadosInconsistentes() {
        UUID opcion = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new EntradaEvaluacion.PreguntaEvaluable(
                UUID.randomUUID(), UUID.randomUUID(), true, true, Optional.empty(),
                List.of(new OpcionEvaluable(opcion, true)), Optional.of(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> new EntradaEvaluacion.PreguntaEvaluable(
                UUID.randomUUID(), UUID.randomUUID(), true, true, Optional.empty(),
                List.of(new OpcionEvaluable(opcion, true), new OpcionEvaluable(opcion, false)), Optional.empty()));
        var pregunta = new EntradaEvaluacion.PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true,
                Optional.empty(), List.of(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> new EntradaEvaluacion(f.solicitud(), f.caso,
                List.of(pregunta, pregunta), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new EntradaEvaluacion(f.solicitud(), f.caso,
                List.of(), null));
    }

    private static void assertIncoherente(SolicitudEvaluacion solicitud, ContextoEvaluacionPreparado preparado) {
        var error = assertThrows(IllegalStateException.class, () -> EntradaEvaluacionMapper.mapear(solicitud, preparado));
        assertTrue(error.getMessage().startsWith("Contexto de evaluacion incoherente"), error.getMessage());
    }
}
