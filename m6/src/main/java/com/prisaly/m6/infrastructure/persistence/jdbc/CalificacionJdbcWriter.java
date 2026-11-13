package com.prisaly.m6.infrastructure.persistence.jdbc;

import com.prisaly.m6.application.port.RegistroCalificacionPort;
import com.prisaly.m6.domain.model.Calificacion;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC insert-only adapter over the official {@code Calificacion} table. Deliberately not a {@code @Component}: no
 * evaluative writer is published until the evaluation is ratified (see {@code EnsamblajeEvaluacionNoPublicadoTest}).
 *
 * <ul>
 *   <li>The value and policy are validated with the domain {@link Calificacion} rules <b>before</b> the insert, so
 *       PostgreSQL never rounds a value silently into {@code NUMERIC(8,2)}.</li>
 *   <li>{@code ON CONFLICT ("idIntento") DO NOTHING}: a second grade for the same attempt (retry or race) writes
 *       nothing and returns empty. The V003 trigger still verifies that the rubric belongs to the evaluated case.</li>
 *   <li>{@code fechaCalculo} is the database default ({@code now()}), returned by the insert.</li>
 * </ul>
 */
public final class CalificacionJdbcWriter implements RegistroCalificacionPort {
    private static final String INSERT = """
            INSERT INTO public."Calificacion" ("idCalificacion","idIntento","idRubrica","valor","detalleEvaluacion","versionPolitica")
            VALUES (?,?,?,?,CAST(? AS jsonb),?)
            ON CONFLICT ("idIntento") DO NOTHING
            RETURNING "fechaCalculo"
            """;
    private final JdbcTemplate jdbc;

    public CalificacionJdbcWriter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<Calificacion> registrar(UUID idIntento, UUID idRubrica, ResultadoEvaluacion resultado,
            String versionPolitica) {
        Objects.requireNonNull(resultado, "resultado");
        UUID idCalificacion = UUID.randomUUID();
        // Same physical rules as the persisted record; throws IllegalArgumentException before any write.
        new Calificacion(idCalificacion, idIntento, idRubrica, resultado.valor(),
                Instant.now().truncatedTo(ChronoUnit.MICROS), resultado.detalleEvaluacion(), versionPolitica);
        List<Timestamp> fechas = jdbc.query(INSERT, (rs, n) -> rs.getTimestamp(1), idCalificacion, idIntento, idRubrica,
                resultado.valor(), resultado.detalleEvaluacion(), versionPolitica);
        if (fechas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Calificacion(idCalificacion, idIntento, idRubrica, resultado.valor(),
                fechas.getFirst().toInstant(), resultado.detalleEvaluacion(), versionPolitica));
    }
}
