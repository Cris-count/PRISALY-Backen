package com.prisaly.m6.domain.strategy;

import com.prisaly.m6.api.SolicitudEvaluacion;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EstrategiaEvaluacionTest {
    @Test
    void sameCallerCanDelegateToInterchangeableExplicitStrategies() {
        var request = request();
        // Supplied fixtures, never computed from answers or an invented formula.
        var firstResult = new ResultadoEvaluacion(new BigDecimal("12.50"), "{\"fixture\":\"first\"}");
        var secondResult = new ResultadoEvaluacion(new BigDecimal("7.25"), "{\"fixture\":\"second\"}");
        var first = new EstrategiaDePrueba(firstResult);
        var second = new EstrategiaDePrueba(secondResult);

        assertEquals(0, first.calls); assertEquals(0, second.calls);
        assertSame(firstResult, new ConsumidorDePrueba(first).evaluar(request));
        assertEquals(1, first.calls); assertEquals(0, second.calls);
        assertSame(secondResult, new ConsumidorDePrueba(second).evaluar(request));
        assertEquals(1, first.calls); assertEquals(1, second.calls);
        assertSame(request, first.received); assertSame(request, second.received);
    }

    @Test
    void entryRecordsAreImmutableAndCarryBothWeightingOriginsUninterpreted() {
        for (Class<?> type : List.of(EntradaEvaluacion.class, EntradaEvaluacion.PreguntaEvaluable.class,
                EntradaEvaluacion.OpcionEvaluable.class)) {
            assertTrue(type.isRecord()); assertTrue(Modifier.isFinal(type.getModifiers()));
            assertTrue(Arrays.stream(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .allMatch(f -> Modifier.isPrivate(f.getModifiers()) && Modifier.isFinal(f.getModifiers())));
        }
        assertEquals(List.of("solicitud", "idCaso", "preguntas", "definicionEvaluacionRubrica"),
                Arrays.stream(EntradaEvaluacion.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(List.of("idPregunta", "idEscenario", "escenarioActivo", "visible", "valorPregunta",
                        "opciones", "opcionSeleccionada"),
                Arrays.stream(EntradaEvaluacion.PreguntaEvaluable.class.getRecordComponents())
                        .map(c -> c.getName()).toList());
        // Domain input depends only on java.* and the M6 API, never on application/infrastructure types.
        for (Class<?> type : List.of(EntradaEvaluacion.class, EntradaEvaluacion.PreguntaEvaluable.class)) {
            for (var c : type.getRecordComponents()) {
                String name = c.getType().getName();
                assertTrue(name.startsWith("java.") || name.startsWith("com.prisaly.m6.api.")
                        || name.startsWith("com.prisaly.m6.domain.") || c.getType().isPrimitive(), name);
            }
        }
        var entrada = request();
        assertThrows(UnsupportedOperationException.class, () -> entrada.preguntas().add(null));
    }

    @Test
    void constructingRequestDoesNotExecuteOrSelectStrategy() {
        AtomicInteger calls = new AtomicInteger();
        var expected = new ResultadoEvaluacion(new BigDecimal("0.00"), "null");
        EstrategiaEvaluacion supplied = input -> { calls.incrementAndGet(); return expected; };
        var request = request();
        assertEquals(0, calls.get());
        assertSame(expected, new ConsumidorDePrueba(supplied).evaluar(request));
        assertEquals(1, calls.get());
        assertEquals(2, SolicitudEvaluacion.class.getRecordComponents().length,
                "Request contains references only, no automatically assigned grade");
        assertTrue(Arrays.stream(EntradaEvaluacion.class.getRecordComponents())
                        .noneMatch(c -> c.getType() == BigDecimal.class),
                "The input carries no automatically assigned grade either");
    }

    @Test
    void failurePropagatesWithoutInventingFallbackGrade() {
        var failure = new IllegalStateException("Test-only unavailable strategy");
        EstrategiaEvaluacion supplied = input -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new ConsumidorDePrueba(supplied).evaluar(request())));
    }

    @Test
    void requestRequiresReferencesWithoutManufacturingIdentifiers() {
        UUID attempt = UUID.randomUUID(), rubric = UUID.randomUUID();
        var request = new SolicitudEvaluacion(attempt, rubric);
        assertEquals(attempt, request.idIntento()); assertEquals(rubric, request.idRubrica());
        assertThrows(NullPointerException.class, () -> new SolicitudEvaluacion(null, rubric));
        assertThrows(NullPointerException.class, () -> new SolicitudEvaluacion(attempt, null));
    }

    @Test
    void outputPreservesExactDecimalAndOpaqueDocumentWithoutStorageCoercion() {
        BigDecimal value = new BigDecimal("-1.234567890123456789");
        String document = " {\"opaque\":[1.00,true,null]} ";
        var result = new ResultadoEvaluacion(value, document);
        assertSame(value, result.valor()); assertEquals(value.scale(), result.valor().scale());
        assertEquals(document, result.detalleEvaluacion());
        assertEquals("null", new ResultadoEvaluacion(BigDecimal.ZERO, "null").detalleEvaluacion());
        assertThrows(NullPointerException.class, () -> new ResultadoEvaluacion(null, document));
        assertThrows(NullPointerException.class, () -> new ResultadoEvaluacion(value, null));
    }

    @Test
    void structuralRecordsContainOnlyImmutableComponentsAndFinalFields() {
        for (Class<?> type : List.of(SolicitudEvaluacion.class, ResultadoEvaluacion.class)) {
            assertTrue(type.isRecord()); assertTrue(Modifier.isFinal(type.getModifiers()));
            assertTrue(Arrays.stream(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .allMatch(f -> Modifier.isPrivate(f.getModifiers()) && Modifier.isFinal(f.getModifiers())));
        }
        assertEquals(List.of(UUID.class, UUID.class), Arrays.stream(SolicitudEvaluacion.class.getRecordComponents())
                .map(c -> c.getType()).toList());
        assertEquals(List.of(BigDecimal.class, String.class), Arrays.stream(ResultadoEvaluacion.class.getRecordComponents())
                .map(c -> c.getType()).toList());
    }

    /** Pattern context used only to demonstrate delegation, not a production use case. */
    private record ConsumidorDePrueba(EstrategiaEvaluacion estrategia) {
        ResultadoEvaluacion evaluar(EntradaEvaluacion entrada) { return estrategia.evaluar(entrada); }
    }

    /** Fixture-returning test double. Not an academic evaluation algorithm. */
    private static final class EstrategiaDePrueba implements EstrategiaEvaluacion {
        private final ResultadoEvaluacion supplied;
        private EntradaEvaluacion received;
        private int calls;
        EstrategiaDePrueba(ResultadoEvaluacion supplied) { this.supplied = Objects.requireNonNull(supplied); }
        public ResultadoEvaluacion evaluar(EntradaEvaluacion entrada) {
            received = Objects.requireNonNull(entrada); calls++; return supplied;
        }
    }

    private static EntradaEvaluacion request() {
        UUID opcion = UUID.randomUUID();
        var pregunta = new EntradaEvaluacion.PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true,
                Optional.empty(), List.of(new EntradaEvaluacion.OpcionEvaluable(opcion, true)), Optional.of(opcion));
        return new EntradaEvaluacion(new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), List.of(pregunta), Optional.empty());
    }
}
