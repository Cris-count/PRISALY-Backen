package com.prisaly.m5.application.port;

import com.prisaly.m5.application.ReferenciaConvocatoria;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de M5 hacia el guion narrativo publicado de M3 (DC-BE-SIM-B1-B2A-24). Su adaptador reutiliza
 * {@code GuionNarrativoEstudiantilPort} de M3 y resuelve el caso de la convocatoria con el contrato de M4, nunca con SQL
 * entre módulos. Exige una transacción existente.
 */
public interface GuionesDelIntento {

    /** Versión concreta de un guion publicado (o archivado después de publicarse). */
    record VersionGuion(UUID idGuion, int version, String hashDefinicion, String definicionJson) {
        public VersionGuion {
            Objects.requireNonNull(idGuion, "idGuion");
            Objects.requireNonNull(hashDefinicion, "hashDefinicion");
            Objects.requireNonNull(definicionJson, "definicionJson");
        }
    }

    /** Guion publicado vigente del caso de la convocatoria del estudiante autenticado; vacío si el caso no tiene. */
    Optional<UUID> publicadoParaConvocatoria(ReferenciaConvocatoria convocatoria);

    /** La versión exacta que referencia un intento; vacío si ya no está disponible. */
    Optional<VersionGuion> version(UUID idGuion);
}
