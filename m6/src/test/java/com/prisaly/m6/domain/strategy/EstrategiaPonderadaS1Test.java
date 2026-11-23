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

/** Ratified S1 (ACTA-M6-POLITICAS-55): weights, 100%/0, scale 0–5, HALF_UP 2, C1 rejection, formative. */
class EstrategiaPonderadaS1Test {
    final EstrategiaPonderadaS1 estrategia = new EstrategiaPonderadaS1();

    /** A question with options [correct, wrong]; respuesta: 0 = correct, 1 = wrong, -1 = unanswered. */
    private static PreguntaEvaluable pregunta(String peso, int respuesta) {
        UUID correcta = UUID.randomUUID(), incorrecta = UUID.randomUUID();
        return new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true, "Lista",
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
        var dosCorrectas = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true, "Lista",
                Optional.of(BigDecimal.ONE), List.of(new OpcionEvaluable(a, true), new OpcionEvaluable(b, true)),
                Optional.of(a));
        var ninguna = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true, "Lista",
                Optional.of(BigDecimal.ONE), List.of(new OpcionEvaluable(a, false)), Optional.empty());
        assertCodigo("CLAVE_INCONSISTENTE", entrada(dosCorrectas));
        assertCodigo("CLAVE_INCONSISTENTE", entrada(ninguna));
    }

    // DC-55 C1 (M6-R5): before DC-55 this case expected PESOS_INCOMPLETOS (candidate behaviour, nothing excluded);
    // the ratified rule rejects the evaluation as PREGUNTAS_NO_ACCESIBLES, still without excluding anything.
    @Test
    void preguntasNoVisiblesOInactivasNoSeExcluyenNiSeAdivinan() {
        var oculta = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), false, false, "Lista", Optional.empty(),
                List.of(new OpcionEvaluable(UUID.randomUUID(), true)), Optional.empty());
        assertCodigo("PREGUNTAS_NO_ACCESIBLES", entrada(pregunta("1", 0), oculta));
    }

    @Test
    void c1PreguntaOcultaConPesoYRespuestaCorrectaRechazaLaEvaluacionCompleta() {
        UUID correcta = UUID.randomUUID();
        var ocultaConPeso = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, false, "Lista",
                Optional.of(new BigDecimal("2.00")), List.of(new OpcionEvaluable(correcta, true)), Optional.of(correcta));
        // If it were silently excluded the result would be 5.00; if counted, also 5.00: neither is allowed.
        assertCodigo("PREGUNTAS_NO_ACCESIBLES", entrada(pregunta("1", 0), ocultaConPeso));
        // Order: an empty input is SIN_PREGUNTAS; inaccessibility is reported before any weight problem.
        assertCodigo("PREGUNTAS_NO_ACCESIBLES", entrada(pregunta(null, 0), ocultaConPeso));
    }

    // DC-BE-M6-CORRECCION-QA-57: Codex's QA56-L1 case at domain level. Visible, active scenario, weighted and
    // correctly answered, but Borrador: it was never delivered by M3, so the grade is refused (was 5.00-capable).
    @Test
    void preguntaEnBorradorRechazaLaEvaluacion() {
        UUID correcta = UUID.randomUUID();
        var borrador = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), true, true, "Borrador",
                Optional.of(BigDecimal.ONE), List.of(new OpcionEvaluable(correcta, true)), Optional.of(correcta));
        assertCodigo("PREGUNTAS_NO_ACCESIBLES", entrada(borrador));
        assertCodigo("PREGUNTAS_NO_ACCESIBLES", entrada(pregunta("1", 0), borrador));
    }

    @Test
    void accesibilidadEsLaCondicionDeEntregaDeM3() {
        UUID o = UUID.randomUUID();
        for (boolean activo : new boolean[] {true, false}) {
            for (boolean visible : new boolean[] {true, false}) {
                for (String estado : List.of("Lista", "Borrador")) {
                    var p = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), activo, visible, estado,
                            Optional.of(BigDecimal.ONE), List.of(new OpcionEvaluable(o, true)), Optional.empty());
                    assertEquals(activo && visible && "Lista".equals(estado), p.accesibleAlEstudiante(), p.toString());
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(),
                true, true, "Publicada", Optional.empty(), List.of(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(),
                true, true, null, Optional.empty(), List.of(), Optional.empty()));
    }

    @Test
    void c1EscenarioInactivoRechazaLaEvaluacionCompleta() {
        UUID correcta = UUID.randomUUID();
        var deEscenarioInactivo = new PreguntaEvaluable(UUID.randomUUID(), UUID.randomUUID(), false, true, "Lista",
                Optional.of(BigDecimal.ONE), List.of(new OpcionEvaluable(correcta, true)), Optional.empty());
        assertCodigo("PREGUNTAS_NO_ACCESIBLES", entrada(pregunta("1", 0), deEscenarioInactivo));
    }

    @Test
    void noContestadasCuentanCeroEnElDenominador() {
        // Only one of three equal-weight questions answered (right): 5 × 1/3 = 1.67; all unanswered = 0.00.
        assertEquals(new BigDecimal("1.67"),
                estrategia.evaluar(entrada(pregunta("1", 0), pregunta("1", -1), pregunta("1", -1))).valor());
        assertEquals(new BigDecimal("0.00"),
                estrategia.evaluar(entrada(pregunta("1", -1), pregunta("4", -1))).valor());
    }

    @Test
    void fronteraHalfUpConPesosDecimales() {
        // 5 × 0.25/2.00 = 0.625 → 0.63; 5 × 1.99/2.00 = 4.975 → 4.98; 5 × 0.01/3.00 = 0.01666… → 0.02.
        assertEquals(new BigDecimal("0.63"), estrategia.evaluar(entrada(pregunta("0.25", 0), pregunta("1.75", 1))).valor());
        assertEquals(new BigDecimal("4.98"), estrategia.evaluar(entrada(pregunta("1.99", 0), pregunta("0.01", -1))).valor());
        assertEquals(new BigDecimal("0.02"), estrategia.evaluar(entrada(pregunta("0.01", 0), pregunta("2.99", 1))).valor());
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
    void elDetalleDocumentaLaPoliticaRatificadaFormativaYElDesgloseExacto() {
        var p1 = pregunta("3.00", 0);
        var p2 = pregunta("1.00", -1);
        String detalle = estrategia.evaluar(entrada(p1, p2)).detalleEvaluacion();
        // DC-55: was "ratificada":false while the strategy was a candidate (M6-R1, M6-R11).
        assertTrue(detalle.contains("\"ratificada\":true"), detalle);
        assertTrue(detalle.contains("\"caracter\":\"formativa\""), detalle);
        assertEquals("ponderada-s1-v1", EstrategiaPonderadaS1.IDENTIFICADOR);
        assertTrue(detalle.contains("\"estrategia\":\"" + EstrategiaPonderadaS1.IDENTIFICADOR + "\""), detalle);
        assertTrue(detalle.contains("\"pesoTotal\":4.00") && detalle.contains("\"pesoObtenido\":3.00"), detalle);
        assertTrue(detalle.contains("\"idPregunta\":\"" + p2.idPregunta() + "\",\"peso\":1.00,\"respondida\":false"), detalle);
        assertTrue(EstrategiaPonderadaS1.IDENTIFICADOR.length() <= 50);
    }

    @Test
    void noEsUnComponenteDeSpring() {
        for (Annotation a : EstrategiaPonderadaS1.class.getAnnotations()) {
            assertFalse(a.annotationType().getName().startsWith("org.springframework"), a.toString());
        }
        assertTrue(Arrays.stream(EstrategiaPonderadaS1.class.getInterfaces()).anyMatch(EstrategiaEvaluacion.class::equals));
    }

    private void assertCodigo(String codigo, EntradaEvaluacion entrada) {
        var error = assertThrows(EvaluacionRechazadaException.class, () -> estrategia.evaluar(entrada));
        assertEquals(codigo, error.codigo());
    }
}
