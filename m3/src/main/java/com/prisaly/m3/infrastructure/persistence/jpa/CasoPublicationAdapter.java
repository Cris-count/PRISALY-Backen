package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoPublication;
import com.prisaly.m3.domain.rule.CasoValidation;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** REQUIRED, state-only publication. Academic rubric completeness is deliberately unchanged. */
public class CasoPublicationAdapter implements CasoPublicationPort {
    public static final String CODE_CASE_NOT_PUBLISHED = "CASE_NOT_PUBLISHED";
    private final EntityManagerFactory emf;
    private final CaseAuthorizationPort authorization;
    private final TransactionTemplate transaction;

    public CasoPublicationAdapter(EntityManagerFactory emf, PlatformTransactionManager manager,
            CaseAuthorizationPort authorization) {
        this.emf = Objects.requireNonNull(emf, "emf");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        if (!(manager instanceof JpaTransactionManager jpa) || jpa.getEntityManagerFactory() != emf) {
            throw new IllegalArgumentException("A JpaTransactionManager for the same EMF is required");
        }
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public CasoUpdateResult publish(String userId, UUID caseId, String expectedFingerprint) {
        validarIdentificadores(userId, caseId);
        CasoCommandUpdateAdapter.validateFingerprintVersion(expectedFingerprint);
        return publishResolvingPrecondition(userId, caseId, () -> expectedFingerprint);
    }

    @Override
    public CasoUpdateResult publishResolvingPrecondition(String userId, UUID caseId, Supplier<String> precondition) {
        return enTransaccionBloqueada("Publication", userId, caseId, precondition, (connection, before) -> {
            assertAuthorized(authorization, userId, caseId);
            assertOwner(before, userId);
            String expectedFingerprint = resolver(precondition);
            CasoCommandUpdateAdapter.assertOfficialContentGuards(connection);
            assertSchedulingGuards(connection);
            Caso published = validatePublication(before, expectedFingerprint);
            try (var statement = connection.prepareStatement("""
                    UPDATE public."Caso" SET "estado" = ?
                    WHERE "idCaso" = ? AND "estado" = ? AND "fechaBloqueoAcademico" IS NULL
                    """)) {
                statement.setString(1, "Publicado");
                statement.setObject(2, caseId);
                statement.setString(3, "Borrador");
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Publication must update exactly one draft");
                }
            }
            var after = requireExisting(CasoTransactionalSnapshotReader.read(connection, caseId, false));
            Caso persisted = CasoPersistenceMapper.toDomain(after);
            String fingerprint = CasoAggregateFingerprint.of(after);
            if (!published.equals(persisted) || !persisted.isPublished() || fingerprint.equals(expectedFingerprint)) {
                throw new IllegalStateException("Publication readback changed content or did not publish");
            }
            return new CasoUpdateResult(persisted, fingerprint);
        });
    }

    @Override
    public CasoUpdateResult archive(String userId, UUID caseId, String expectedFingerprint) {
        validarIdentificadores(userId, caseId);
        CasoCommandUpdateAdapter.validateFingerprintVersion(expectedFingerprint);
        return archiveResolvingPrecondition(userId, caseId, () -> expectedFingerprint);
    }

    @Override
    public CasoUpdateResult archiveResolvingPrecondition(String userId, UUID caseId, Supplier<String> precondition) {
        return enTransaccionBloqueada("Archiving", userId, caseId, precondition, (connection, before) -> {
            assertArchiveAuthorized(authorization, userId, caseId);
            assertOwner(before, userId);
            String expectedFingerprint = resolver(precondition);
            if (!CasoAggregateFingerprint.of(before).equals(expectedFingerprint)) {
                throw new CasoDomainException(CasoCommandUpdateAdapter.STALE_CASE, "Aggregate precondition is stale");
            }
            if ("Archivado".equals(before.caso().estado())) {
                throw new CasoDomainException(CasoValidation.CODE_ARCHIVED_CASE_OPERATION_PENDING, "Case is already archived");
            }
            if (!"Publicado".equals(before.caso().estado())) {
                throw new CasoDomainException(CODE_CASE_NOT_PUBLISHED, "Only a published case can be archived");
            }
            try (var statement = connection.prepareStatement("""
                    UPDATE public."Caso" SET "estado" = 'Archivado' WHERE "idCaso" = ? AND "estado" = 'Publicado'
                    """)) {
                statement.setObject(1, caseId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Archiving must update exactly one published case");
                }
            }
            var after = requireExisting(CasoTransactionalSnapshotReader.read(connection, caseId, false));
            return new CasoUpdateResult(CasoPersistenceMapper.toDomain(after), CasoAggregateFingerprint.of(after));
        });
    }

    @FunctionalInterface
    private interface TransicionBloqueada {
        CasoUpdateResult aplicar(Connection connection, CasoJpaAggregateSnapshot before) throws SQLException;
    }

    private CasoUpdateResult enTransaccionBloqueada(String operacion, String userId, UUID caseId,
            Supplier<String> precondition, TransicionBloqueada transicion) {
        validarIdentificadores(userId, caseId);
        Objects.requireNonNull(precondition, "precondition");
        return Objects.requireNonNull(transaction.execute(status -> {
            var em = Objects.requireNonNull(EntityManagerFactoryUtils.getTransactionalEntityManager(emf));
            return em.unwrap(Session.class).doReturningWork(connection -> {
                if (connection.getAutoCommit()
                        || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                    throw new IllegalStateException(operacion + " requires an effective READ COMMITTED transaction");
                }
                var before = requireExisting(CasoTransactionalSnapshotReader.read(connection, caseId, true));
                return transicion.aplicar(connection, before);
            });
        }));
    }

    private static void validarIdentificadores(String userId, UUID caseId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        Objects.requireNonNull(caseId, "caseId");
    }

    /**
     * N1/F4-H1: called only after permission and ownership. Absence (428), malformed (400) and stale (412)
     * validators are therefore never observable by a caller that is not allowed to act on the case.
     */
    private static String resolver(Supplier<String> precondition) {
        String fingerprint = precondition.get();
        CasoCommandUpdateAdapter.validateFingerprintVersion(fingerprint);
        return fingerprint;
    }

    private static void assertArchiveAuthorized(CaseAuthorizationPort authorization, String userId, UUID caseId) {
        boolean allowed;
        try { allowed = authorization.canArchiveCase(userId, caseId.toString()); }
        catch (RuntimeException failure) {
            throw new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, "Archiving is not authorized");
        }
        if (!allowed) {
            throw new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, "Archiving is not authorized");
        }
    }

    static CasoJpaAggregateSnapshot requireExisting(CasoJpaAggregateSnapshot snapshot) {
        if (snapshot == null) {
            throw new CasoDomainException(CasoCommandUpdateAdapter.CASE_NOT_FOUND, "Case does not exist");
        }
        return snapshot;
    }

    private static void assertSchedulingGuards(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT count(*) FROM pg_trigger t
                JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname = 'Sesion'
                  AND NOT t.tgisinternal AND t.tgenabled IN ('O','A')
                  AND t.tgname IN ('tr_sesion_bloqueo_caso_f303',
                    'tr_sesion_bloqueo_reasignacion_f303', 'tr_sesion_00_rf09_caso_programable')
                """); var rows = statement.executeQuery()) {
            if (!rows.next() || rows.getInt(1) != 3) {
                throw new IllegalStateException("Publication requires the official V007-V009 scheduling guards");
            }
        }
    }

    static void assertAuthorized(CaseAuthorizationPort authorization, String userId, UUID caseId) {
        boolean allowed;
        try { allowed = authorization.canPublishCase(userId, caseId.toString()); }
        catch (RuntimeException failure) {
            throw new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, "Publication is not authorized");
        }
        if (!allowed) {
            throw new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, "Publication is not authorized");
        }
    }

    static void assertOwner(CasoJpaAggregateSnapshot snapshot, String userId) {
        UUID user;
        try { user = CasoPersistenceMapper.uuid(userId); }
        catch (RuntimeException failure) {
            throw new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, "Publication owner mismatch");
        }
        if (!user.equals(snapshot.caso().idDocente())) {
            throw new CasoDomainException(CasoApplicationService.CODE_FORBIDDEN, "Publication owner mismatch");
        }
    }

    static Caso validatePublication(CasoJpaAggregateSnapshot before, String expected) {
        if (!CasoAggregateFingerprint.of(before).equals(expected)) {
            throw new CasoDomainException(CasoCommandUpdateAdapter.STALE_CASE, "Aggregate precondition is stale");
        }
        Caso caso = CasoPersistenceMapper.toDomain(before);
        CasoValidation.assertNotArchived(caso);
        ContenidoEvaluableLock.assertEditable(caso);
        // PUBLICADO is rejected by the existing transition; enum leaves only BORRADOR.
        return CasoPublication.publish(caso);
    }
}
