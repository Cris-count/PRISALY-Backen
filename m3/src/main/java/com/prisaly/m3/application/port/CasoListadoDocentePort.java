package com.prisaly.m3.application.port;

import com.prisaly.m3.domain.model.CasoEstado;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** DC-67 candidate: the cases created by one teacher ({@code Caso.idDocente}), summary fields only. */
@FunctionalInterface
public interface CasoListadoDocentePort {
    record FilaCaso(UUID idCaso, String titulo, int tiempoEstimadoSegundos, CasoEstado estado, boolean activo) {
        public FilaCaso {
            Objects.requireNonNull(idCaso, "idCaso");
            Objects.requireNonNull(titulo, "titulo");
            Objects.requireNonNull(estado, "estado");
        }
    }

    /** Deterministic order: most recent first, then id. Technical failures propagate. */
    List<FilaCaso> listarPorDocente(UUID idDocente);
}
