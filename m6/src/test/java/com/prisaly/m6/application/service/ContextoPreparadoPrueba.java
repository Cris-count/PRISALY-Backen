package com.prisaly.m6.application.service;

import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.ContextoEvaluacionPreparado;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only prepared context: two scenarios (the second inactive) with three questions, a rubric associated to
 * only the first question, and an answer to the first question only. Values are fixtures, not grades or weights.
 */
final class ContextoPreparadoPrueba {
    final UUID caso = UUID.randomUUID(), docente = UUID.randomUUID();
    final UUID intento = UUID.randomUUID(), participacion = UUID.randomUUID(), rubrica = UUID.randomUUID();
    final UUID escenario1 = UUID.randomUUID(), escenario2 = UUID.randomUUID();
    final UUID pregunta1 = UUID.randomUUID(), pregunta2 = UUID.randomUUID(), pregunta3 = UUID.randomUUID();
    final UUID opcion1a = UUID.randomUUID(), opcion1b = UUID.randomUUID();
    final UUID opcion2a = UUID.randomUUID(), opcion3a = UUID.randomUUID();
    final BigDecimal valorPregunta1 = new BigDecimal("2.50");
    final String definicion = "{\"criterios\":[\"fixture\"]}";

    SolicitudEvaluacion solicitud() {
        return new SolicitudEvaluacion(intento, rubrica);
    }

    ContextoEvaluacionPreparado preparado() {
        return preparado(List.of(new IntentoEvaluacion.RespuestaReferencia(pregunta1, opcion1b)), definicion,
                Set.of(asociacion(pregunta1, valorPregunta1)));
    }

    ContextoEvaluacionPreparado preparado(List<IntentoEvaluacion.RespuestaReferencia> respuestas,
            String definicionEvaluacion, Set<ContextoAcademico.RubricaPregunta> asociaciones) {
        var p1 = new ContextoAcademico.Pregunta(pregunta1, escenario1, "P1", null, "Lista", true, 1, null, null,
                List.of(opcion(opcion1a, pregunta1, false), opcion(opcion1b, pregunta1, true)));
        var p2 = new ContextoAcademico.Pregunta(pregunta2, escenario1, "P2", null, "Lista", false, 2, null, null,
                List.of(opcion(opcion2a, pregunta2, true)));
        var p3 = new ContextoAcademico.Pregunta(pregunta3, escenario2, "P3", null, "Lista", true, 1, null, null,
                List.of(opcion(opcion3a, pregunta3, true)));
        var e1 = new ContextoAcademico.Escenario(escenario1, caso, "E1", null, null, 1, true, List.of(p1, p2));
        var e2 = new ContextoAcademico.Escenario(escenario2, caso, "E2", null, null, 2, false, List.of(p3));
        var r = new ContextoAcademico.Rubrica(rubrica, caso, "Rubrica fixture", null, null, definicionEvaluacion,
                null, asociaciones);
        var contexto = new ContextoAcademico(caso, docente, "Caso fixture", null, null, 600, "Publicado", true,
                null, null, null, List.of(e1, e2), r);
        var i = new IntentoEvaluacion(intento, participacion, caso, IntentoEvaluacion.EstadoIntento.FINALIZADO,
                respuestas);
        return new ContextoEvaluacionPreparado(i, contexto);
    }

    ContextoAcademico.RubricaPregunta asociacion(UUID pregunta, BigDecimal valor) {
        return new ContextoAcademico.RubricaPregunta(UUID.randomUUID(), rubrica, pregunta, valor, null);
    }

    private static ContextoAcademico.Opcion opcion(UUID id, UUID pregunta, boolean correcta) {
        return new ContextoAcademico.Opcion(id, pregunta, "texto", correcta, null, null);
    }
}
