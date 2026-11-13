package com.prisaly.m6.domain.strategy;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion.OpcionEvaluable;
import com.prisaly.m6.domain.strategy.EntradaEvaluacion.PreguntaEvaluable;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Candidate proposal under test (not ratified policy): weights, 100%/0, scale 0–5, HALF_UP 2. */
class EstrategiaPonderadaCandidataTest {
    final EstrategiaPonderadaCandidata estrategia = new EstrategiaPonderadaCandidata();

    /** A question with options [correct, wrong]; respuesta: 0 = correct, 1 = wrong, -1 = unanswered. */
    private static PreguntaEvaluable pregunta(String peso, int respuesta) {
        UUID correcta = UUID.randomUUID(), incorrecta = UUID.randomUUID();
        return new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true,
                Optional.ofNullable(peso).map(BigDecimal::new),
                List.of(new OpcionEvaluable(correcta, true), new OpcionEvaluable(incorrecta, false)),
                respuesta == 0 ? Optional.of(correcta) : respuesta == 1 ? Optional.of(incorrecta) : Optional.empty());
    }

    private static EntradaEvaluacion entrada(Optional<String> definicion, PreguntaEvaluable... preguntas) {
        return new EntradaEvaluacion(new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(),
                List.of(preguntas), definicion);
    }

    private static EntradaEvaluacion entrada(PreguntaEvaluable... preguntas) {
        return entrada(Optional.empty(), preguntas);
    }

    @Test
    void todasCorrectasDanCincoYNingunaDaCero() {
        assertEquals(new BigDecimal("5.00"), estrategia.evaluar(entrada(pregunta("2", 0), pregunta("3", 0))).valor());
        assertEquals(new BigDecimal("0.00"), estrategia.evaluar(entrada(pregunta("2", 1), pregunta("3", -1))).valor());
    }

    @Test
    void ponderaPorPreguntaYTrataIncorrectaYNoContestadaComoCero() {
        // 3 of 4 weight points: 5 × 3/4 = 3.75 exactly.
        assertEquals(new BigDecimal("3.75"),
                estrategia.evaluar(entrada(pregunta("3.00", 0), pregunta("1.00", 1))).valor());
        assertEquals(new BigDecimal("3.75"),
                estrategia.evaluar(entrada(pregunta("3.00", 0), pregunta("1.00", -1))).valor());
        // Weights matter: the same single right answer is worth differently.
        assertEquals(new BigDecimal("1.25"),
                estrategia.evaluar(entrada(pregunta("3.00", 1), pregunta("1.00", 0))).valor());
    }

    @Test
    void redondeaUnaSolaVezHalfUpADosDecimales() {
        // 5 × 1/3 = 1.6666… → 1.67; 5 × 2/3 = 3.3333… → 3.33; 5 × 1/8 = 0.625 → 0.63 (HALF_UP, not HALF_EVEN).
        assertEquals(new BigDecimal("1.67"), estrategia.evaluar(entrada(pregunta("1", 0), pregunta("2", 1))).valor());
        assertEquals(new BigDecimal("3.33"), estrategia.evaluar(entrada(pregunta("2", 0), pregunta("1", 1))).valor());
        assertEquals(new BigDecimal("0.63"), estrategia.evaluar(entrada(pregunta("1", 0), pregunta("7", -1))).valor());
        assertEquals(2, estrategia.evaluar(entrada(pregunta("1", 0), pregunta("2", 1))).valor().scale());
    }

    @Test
    void pesosIncompletosOInconsistentesSeRechazanSinResultado() {
        assertCodigo("PESOS_INCOMPLETOS", entrada(pregunta("1", 0), pregunta(null, 0)));
        assertCodigo("PESOS_INCONSISTENTES", entrada(pregunta("1", 0), pregunta("0", 0)));
        assertCodigo("PESOS_INCONSISTENTES", entrada(pregunta("-1", 0)));
        assertCodigo("SIN_PREGUNTAS", entrada());
    }

    @Test
    void unaClaveSinUnicaOpcionCorrectaSeRechaza() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var dosCorrectas = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true,
                Optional.of(BigDecimal.ONE), List.of(new OpcionEvaluable(a, true), new OpcionEvaluable(b, true)),
                Optional.of(a));
        var ninguna = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true,
                Optional.of(BigDecimal.ONE), List.of(new OpcionEvaluable(a, false)), Optional.empty());
        assertCodigo("CLAVE_INCONSISTENTE", entrada(dosCorrectas));
        assertCodigo("CLAVE_INCONSISTENTE", entrada(ninguna));
    }

    @Test
    void preguntasNoVisiblesOInactivasNoSeExcluyenNiSeAdivinan() {
        var oculta = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), false, false, Optional.empty(),
                List.of(new OpcionEvaluable(UUID.randomUUID(), true)), Optional.empty());
        assertCodigo("PESOS_INCOMPLETOS", entrada(pregunta("1", 0), oculta));
    }

    @Test
    void losCriteriosJsonbNoSeInterpretanComoPesos() {
        var preguntas = new PreguntaEvaluable[] {pregunta("3", 0), pregunta("1", 1)};
        var sin = estrategia.evaluar(entrada(Optional.empty(), preguntas));
        var con = estrategia.evaluar(entrada(Optional.of("{\"criterios\":[{\"peso\":100},{\"peso\":0}]}"), preguntas));
        assertEquals(sin, con);
        // Without per-question weights, JSONB criteria do not make the input evaluable.
        assertCodigo("PESOS_INCOMPLETOS", entrada(Optional.of("{\"criterios\":[{\"peso\":1}]}"), pregunta(null, 0)));
    }

    @Test
    void elDetalleDocumentaLaPropuestaNoRatificadaYElDesgloseExacto() {
        var p1 = pregunta("3.00", 0);
        var p2 = pregunta("1.00", -1);
        String detalle = estrategia.evaluar(entrada(p1, p2)).detalleEvaluacion();
        assertTrue(detalle.contains("\"ratificada\":false"), detalle);
        assertTrue(detalle.contains("\"estrategia\":\"" + EstrategiaPonderadaCandidata.IDENTIFICADOR + "\""), detalle);
        assertTrue(detalle.contains("\"pesoTotal\":4.00") && detalle.contains("\"pesoObtenido\":3.00"), detalle);
        assertTrue(detalle.contains("\"idPregunta\":\"" + p2.idPregunta() + "\",\"peso\":1.00,\"respondida\":false"), detalle);
        assertTrue(EstrategiaPonderadaCandidata.IDENTIFICADOR.length() <= 50);
    }

    @Test
    void noEsUnComponenteDeSpring() {
        for (Annotation a : EstrategiaPonderadaCandidata.class.getAnnotations()) {
            assertFalse(a.annotationType().getName().startsWith("org.springframework"), a.toString());
        }
        assertTrue(Arrays.stream(EstrategiaPonderadaCandidata.class.getInterfaces()).anyMatch(EstrategiaEvaluacion.class::equals));
    }

    private void assertCodigo(String codigo, EntradaEvaluacion entrada) {
        var error = assertThrows(EvaluacionRechazadaException.class, () -> estrategia.evaluar(entrada));
        assertEquals(codigo, error.codigo());
    }
}
