package com.prisaly.m6.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Structural representation of BD-005.2 Calificacion, not a grading algorithm.
 * PostgreSQL prohibits updating/deleting historical grades (V003).
 * JSON is opaque document text: syntax and JSONB compatibility are not checked here.
 */
public record Calificacion(
        UUID idCalificacion,
        UUID idIntento,
        UUID idRubrica,
        BigDecimal valor,
        Instant fechaCalculo,
        String detalleEvaluacion,
        String versionPolitica) {

    private static final BigDecimal MAX_VALUE = new BigDecimal("999999.99");

    public Calificacion {
        Objects.requireNonNull(idCalificacion, "idCalificacion");
        Objects.requireNonNull(idIntento, "idIntento");
        Objects.requireNonNull(idRubrica, "idRubrica");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(fechaCalculo, "fechaCalculo");
        Objects.requireNonNull(detalleEvaluacion, "detalleEvaluacion");
        // Physical NUMERIC(8,2) capacity only. Keep the supplied value and scale;
        // reject values requiring rounding rather than changing them silently.
        if (valor.abs().compareTo(MAX_VALUE) > 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("valor must be exactly representable as NUMERIC(8,2)");
        }
        if (fechaCalculo.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("fechaCalculo must preserve PostgreSQL microsecond precision");
        }
        if (versionPolitica != null && versionPolitica.codePointCount(0, versionPolitica.length()) > 50) {
            throw new IllegalArgumentException("versionPolitica must not exceed 50 Unicode code points");
        }
    }
}
