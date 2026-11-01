package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.application.port.CasoUpdatePort;
import com.prisaly.m3.application.update.CasoUpdateCommand;
import com.prisaly.m3.application.update.CasoUpdateCommand.*;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** M3-INC-10B. Local persistence contract; authorization must be applied by application/M1. */
@Repository
public class CasoCommandUpdateAdapter implements CasoUpdatePort {
    public static final String STALE_CASE = "STALE_CASE";
    public static final String CASE_NOT_FOUND = "CASE_NOT_FOUND";
    public static final String ELEMENT_NOT_IN_CASE = "ELEMENT_NOT_IN_CASE";
    private final EntityManagerFactory emf;
    private final CasoJpaAggregateReader reader;
    private final TransactionTemplate transaction;

    public CasoCommandUpdateAdapter(EntityManagerFactory emf, CasoJpaAggregateReader reader,
            PlatformTransactionManager manager) {
        this.emf = Objects.requireNonNull(emf, "emf");
        this.reader = Objects.requireNonNull(reader, "reader");
        if (!(manager instanceof JpaTransactionManager jpa) || jpa.getEntityManagerFactory() != emf) {
            throw new IllegalArgumentException("A JpaTransactionManager for the same EMF is required");
        }
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public Optional<CasoUpdateResult> findById(UUID caseId) {
        return reader.findById(Objects.requireNonNull(caseId, "caseId")).map(CasoCommandUpdateAdapter::result);
    }

    @Override
    public CasoUpdateResult update(UUID caseId, String expectedFingerprint, List<CasoUpdateCommand> commands) {
        Objects.requireNonNull(caseId, "caseId");
        validateFingerprintVersion(expectedFingerprint);
        List<CasoUpdateCommand> requested = List.copyOf(commands);
        return Objects.requireNonNull(transaction.execute(status -> {
            var em = Objects.requireNonNull(EntityManagerFactoryUtils.getTransactionalEntityManager(emf));
            return em.unwrap(Session.class).doReturningWork(connection -> {
                if (connection.getAutoCommit() || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                    throw new IllegalStateException("Update requires a real READ COMMITTED transaction");
                }
                var before = CasoTransactionalSnapshotReader.read(connection, caseId, true);
                if (before == null) { throw error(CASE_NOT_FOUND, "Case does not exist"); }
                assertOfficialContentGuards(connection);
                validateExpected(before, expectedFingerprint, requested);
                // Validate membership before the first mutation, against the locked aggregate.
                for (var command : requested) { validateMembership(before, command); }
                for (var command : requested) { apply(connection, caseId, command); }
                return result(Objects.requireNonNull(CasoTransactionalSnapshotReader.read(connection, caseId, false)));
            });
        }));
    }

    private static CasoUpdateResult result(CasoJpaAggregateSnapshot snapshot) {
        return new CasoUpdateResult(CasoPersistenceMapper.toDomain(snapshot), CasoAggregateFingerprint.of(snapshot));
    }

    static void validateFingerprintVersion(String fingerprint) {
        if (fingerprint != null && fingerprint.matches("m3-v[1-9][0-9]*:[0-9a-f]{64}")
                && !fingerprint.startsWith(CasoAggregateFingerprint.PREFIX)) {
            throw error(STALE_CASE, "Incompatible fingerprint version; reload the case before updating");
        }
        if (fingerprint == null || !fingerprint.matches(CasoAggregateFingerprint.PREFIX + "[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A previously obtained m3-v2 fingerprint is required");
        }
    }

    static void validateExpected(CasoJpaAggregateSnapshot snapshot, String expected, List<CasoUpdateCommand> commands) {
        if (!CasoAggregateFingerprint.of(snapshot).equals(expected)) {
            throw error(STALE_CASE, "Aggregate changed since the precondition was obtained");
        }
        if ("Archivado".equals(snapshot.caso().estado())) {
            throw error(CasoValidation.CODE_ARCHIVED_CASE_OPERATION_PENDING, "Archived case operations are pending");
        }
        if (!commands.isEmpty() && snapshot.caso().fechaBloqueoAcademico() != null) {
            throw error(ContenidoEvaluableLock.CODE_EVALUABLE_CONTENT_LOCKED, "Scheduled case content is locked");
        }
    }

    private static void assertOfficialContentGuards(Connection connection) throws SQLException {
        // Detect missing/disabled guards rather than claim that locking Caso alone locks its children.
        try (var statement = connection.prepareStatement("""
                WITH required(table_name, trigger_name) AS (VALUES
                  ('Caso','tr_caso_contenido_f303'), ('Escenario','tr_escenario_bloqueo_f303'),
                  ('Pregunta','tr_pregunta_bloqueo_f303'), ('OpcionRespuesta','tr_opcion_bloqueo_f303'),
                  ('Rubrica','tr_rubrica_bloqueo_f303'), ('RubricaPregunta','tr_rubricapregunta_bloqueo_f303'))
                SELECT count(*) FROM required r
                  JOIN pg_class c ON c.relname = r.table_name
                  JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
                  JOIN pg_trigger t ON t.tgrelid = c.oid AND t.tgname = r.trigger_name
                WHERE NOT t.tgisinternal AND t.tgenabled IN ('O','A')
                  AND current_setting('session_replication_role') = 'origin'
                  AND current_setting('transaction_isolation') = 'read committed'
                  AND current_setting('transaction_read_only') = 'off'
                """); var rows = statement.executeQuery()) {
            if (!rows.next() || rows.getInt(1) != 6) {
                throw new IllegalStateException("Update requires active F3-03 guards, origin mode and a writable READ COMMITTED transaction");
            }
        }
    }

    static void validateMembership(CasoJpaAggregateSnapshot snapshot, CasoUpdateCommand command) {
        boolean belongs = switch (command) {
            case ActualizarDatosCasoCommand ignored -> true;
            case ActualizarEscenarioCommand c -> snapshot.escenarios().stream().anyMatch(e -> e.idEscenario().equals(c.idEscenario()));
            case ActualizarPreguntaCommand c -> snapshot.preguntas().stream().anyMatch(p -> p.idPregunta().equals(c.idPregunta()));
            case EliminarOpcionCommand c -> snapshot.opciones().stream().anyMatch(o -> o.idOpcion().equals(c.idOpcion()));
        };
        if (!belongs) { throw error(ELEMENT_NOT_IN_CASE, "Element does not exist in this case"); }
    }

    private static void apply(Connection connection, UUID caseId, CasoUpdateCommand command) throws SQLException {
        switch (command) {
            case ActualizarDatosCasoCommand c -> change(connection, "Caso", "idCaso", caseId,
                    switch (c.campo()) {
                        case NOMBRE -> "nombre";
                        case HISTORIA -> "historia";
                        case DESARROLLO_SITUACIONAL -> "desarrolloSituacional";
                        case TIEMPO_ESTIMADO_SEGUNDOS -> "tiempoEstimadoSegundos";
                    }, c.valor());
            case ActualizarEscenarioCommand c -> change(connection, "Escenario", "idEscenario", c.idEscenario(),
                    switch (c.campo()) {
                        case TITULO -> "titulo";
                        case CONTEXTO -> "contexto";
                        case INSTRUCCIONES -> "instrucciones";
                        case ORDEN -> "orden";
                    }, c.valor());
            case ActualizarPreguntaCommand c -> change(connection, "Pregunta", "idPregunta", c.idPregunta(),
                    switch (c.campo()) {
                        case ENUNCIADO -> "enunciado";
                        case TEXTO_AYUDA -> "textoAyuda";
                        case ORDEN -> "orden";
                    }, c.valor());
            case EliminarOpcionCommand c -> {
                try (var statement = connection.prepareStatement("DELETE FROM public.\"OpcionRespuesta\" WHERE \"idOpcion\" = ?")) {
                    statement.setObject(1, c.idOpcion());
                    if (statement.executeUpdate() != 1) { throw error(ELEMENT_NOT_IN_CASE, "Option is no longer present"); }
                }
            }
        }
    }

    private static void change(Connection connection, String table, String idColumn, UUID id,
            String column, Object value) throws SQLException {
        // Identifiers come only from the closed command switches above. Values are always bound.
        String sql = "UPDATE public.\"" + table + "\" SET \"" + column + "\" = ? WHERE \"" + idColumn
                + "\" = ? AND \"" + column + "\" IS DISTINCT FROM ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            statement.setObject(2, id);
            statement.setObject(3, value);
            statement.executeUpdate(); // 0 for an unchanged value: do not fire unnecessary row triggers.
        }
    }

    private static CasoDomainException error(String code, String message) { return new CasoDomainException(code, message); }
}
