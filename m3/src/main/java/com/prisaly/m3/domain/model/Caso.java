package com.prisaly.m3.domain.model;

import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.RubricaStructureValidation;
import java.time.Instant;
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
        boolean contenidoEvaluableBloqueado,
        String historia,
        String desarrolloSituacional,
        boolean activo,
        Instant fechaCreacion,
        Instant fechaActualizacion,
        Instant fechaBloqueoAcademico
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
        RubricaStructureValidation.assertBelongsToCase(id, escenarios, rubrica);
        // A known lock date implies locking; a boolean never manufactures a date.
        contenidoEvaluableBloqueado = contenidoEvaluableBloqueado || fechaBloqueoAcademico != null;
    }

    /**
     * Legacy/transient construction: no persistence dates have been assigned.
     * activo follows V001's true default. This does not represent a complete SQL row:
     * persisted fechaCreacion is mandatory and must be supplied when rehydrating.
     */
    public Caso(String id, String titulo, String docenteId, int tiempoEstimadoSegundos,
            CasoEstado estado, List<Escenario> escenarios, Rubrica rubrica,
            boolean contenidoEvaluableBloqueado) {
        this(id, titulo, docenteId, tiempoEstimadoSegundos, estado, escenarios, rubrica,
                contenidoEvaluableBloqueado, null, null, true, null, null, null);
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
