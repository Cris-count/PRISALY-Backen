package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.infrastructure.persistence.jpa.CasoJpaAggregateSnapshot.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Caller-owned connection only. No transaction creation, REQUIRES_NEW or cache. */
final class CasoTransactionalSnapshotReader {
    private CasoTransactionalSnapshotReader() { }

    static CasoJpaAggregateSnapshot read(Connection connection, UUID id, boolean lock) throws SQLException {
        var cases = query(connection, "SELECT * FROM public.\"Caso\" WHERE \"idCaso\" = ?"
                + (lock ? " FOR UPDATE" : ""), id, r -> new CasoRow(
                        uuid(r, "idCaso"), uuid(r, "idDocente"), r.getString("nombre"), r.getString("historia"),
                        r.getString("desarrolloSituacional"), r.getInt("tiempoEstimadoSegundos"), r.getString("estado"),
                        r.getBoolean("activo"), instant(r, "fechaBloqueoAcademico"), instant(r, "fechaCreacion"),
                        instant(r, "fechaActualizacion")));
        if (cases.isEmpty()) { return null; }
        var scenarios = query(connection, """
                SELECT * FROM public."Escenario" WHERE "idCaso" = ? ORDER BY "orden"
                """, id, r -> new EscenarioRow(uuid(r, "idEscenario"), uuid(r, "idCaso"), r.getString("titulo"),
                        r.getString("contexto"), r.getString("instrucciones"), r.getInt("orden"), r.getBoolean("activo")));
        var questions = query(connection, """
                SELECT p.* FROM public."Pregunta" p JOIN public."Escenario" e USING ("idEscenario")
                WHERE e."idCaso" = ? ORDER BY p."idEscenario", p."orden"
                """, id, r -> new PreguntaRow(uuid(r, "idPregunta"), uuid(r, "idEscenario"), r.getString("enunciado"),
                        r.getString("textoAyuda"), r.getString("estadoEdicion"), r.getBoolean("visible"), r.getInt("orden"),
                        instant(r, "fechaCreacion"), instant(r, "fechaActualizacion")));
        var options = query(connection, """
                SELECT o.* FROM public."OpcionRespuesta" o JOIN public."Pregunta" p USING ("idPregunta")
                JOIN public."Escenario" e USING ("idEscenario")
                WHERE e."idCaso" = ? ORDER BY o."idPregunta", o."orden"
                """, id, r -> new OpcionRespuestaRow(uuid(r, "idOpcion"), uuid(r, "idPregunta"), r.getString("texto"),
                        r.getBoolean("esCorrecta"), r.getString("explicacion"), r.getInt("orden")));
        var rubrics = query(connection, """
                SELECT "idRubrica", "idCaso", "nombre", "descripcion", "puntajeTotal",
                    "definicionEvaluacion"::text AS json_document, "fechaCreacion"
                FROM public."Rubrica" WHERE "idCaso" = ?
                """, id, r -> new RubricaRow(uuid(r, "idRubrica"), uuid(r, "idCaso"), r.getString("nombre"),
                        r.getString("descripcion"), r.getBigDecimal("puntajeTotal"), r.getString("json_document"),
                        instant(r, "fechaCreacion")));
        var associations = query(connection, """
                SELECT rp.* FROM public."RubricaPregunta" rp JOIN public."Rubrica" r USING ("idRubrica")
                WHERE r."idCaso" = ?
                """, id, r -> new RubricaPreguntaRow(uuid(r, "idRubricaPregunta"), uuid(r, "idRubrica"),
                        uuid(r, "idPregunta"), r.getBigDecimal("valorPregunta"), r.getString("criterio")));
        return new CasoJpaAggregateSnapshot(cases.getFirst(), scenarios, questions, options,
                rubrics.isEmpty() ? null : rubrics.getFirst(), new HashSet<>(associations));
    }

    private static UUID uuid(ResultSet row, String column) throws SQLException {
        return row.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    @FunctionalInterface
    private interface RowMapper<T> { T map(ResultSet row) throws SQLException; }

    private static <T> List<T> query(Connection connection, String sql, UUID id, RowMapper<T> mapper) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) { rows.add(mapper.map(result)); }
                return rows;
            }
        }
    }
}
