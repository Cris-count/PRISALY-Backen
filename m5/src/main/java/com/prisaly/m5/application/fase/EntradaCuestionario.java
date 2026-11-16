package com.prisaly.m5.application.fase;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de la entrada al cuestionario (contrato P3, candidato). {@code yaIniciado=true} indica una repetición: la
 * fecha es la persistida la primera vez y no se escribió nada (R3, idempotente).
 */
public record EntradaCuestionario(UUID idIntento, Instant fechaInicioCuestionario, boolean yaIniciado) {
    public EntradaCuestionario {
        Objects.requireNonNull(idIntento, "idIntento");
        Objects.requireNonNull(fechaInicioCuestionario, "fechaInicioCuestionario");
    }

    public FaseAcademica fase() {
        return FaseAcademica.CUESTIONARIO;
    }
}
