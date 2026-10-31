package com.prisaly.m3.domain.model;

import com.prisaly.m3.domain.rule.CasoValidation;
import java.util.List;

/**
 * Academic case aggregate. Draft cases may be incomplete; publication requires
 * domain validation. F3-03 locks evaluable content after the first scheduled session.
 */
public record Caso(
        String id,
        String titulo,
        String docenteId,
        int tiempoEstimadoSegundos,
        CasoEstado estado,
        List<Escenario> escenarios,
        Rubrica rubrica,
        boolean contenidoEvaluableBloqueado
) {

    public Caso {
        CasoValidation.assertValidEstimatedTime(tiempoEstimadoSegundos);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Caso.id is required");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Caso.titulo is required");
        }
        if (docenteId == null || docenteId.isBlank()) {
            throw new IllegalArgumentException("Caso.docenteId is required");
        }
        if (estado == null) {
            throw new IllegalArgumentException("Caso.estado is required");
        }
        if (escenarios == null) {
            throw new IllegalArgumentException("Caso.escenarios is required");
        }
        escenarios = List.copyOf(escenarios);
    }

    public boolean isPublished() {
        return estado == CasoEstado.PUBLICADO;
    }

    public int questionCount() {
        return escenarios.stream()
                .mapToInt(escenario -> escenario.preguntas().size())
                .sum();
    }
}
