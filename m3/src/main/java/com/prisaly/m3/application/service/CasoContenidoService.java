package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoUpdatePort;
import com.prisaly.m3.application.update.CasoUpdateCommand;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adds evaluable content to a draft case. Evaluation order: identity, permission, existence,
 * ownership, editable state, precondition, body, and the final comparison under the row lock.
 */
public final class CasoContenidoService {
    public static final String CODE_CONTENT_CONFLICT = "CONTENT_CONFLICT";
    private static final String SQL_UNIQUE_VIOLATION = "23505";
    private static final String DENEGADO = "Case modification denied";

    private final CasoCurrentUserPort identity;
    private final CaseAuthorizationPort authorization;
    private final CasoUpdatePort updates;

    public CasoContenidoService(CasoCurrentUserPort identity, CaseAuthorizationPort authorization,
            CasoUpdatePort updates) {
        this.identity = identity;
        this.authorization = authorization;
        this.updates = updates;
    }

    public CasoUpdateResult aplicar(UUID caseId, Supplier<String> fingerprint, Supplier<CasoUpdateCommand> command) {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(command, "command");
        UUID user = VerificacionCaso.usuarioActual(identity);
        VerificacionCaso.exigir(() -> authorization != null && authorization.canModifyCase(user.toString(), caseId.toString()),
                DENEGADO);
        if (updates == null) { throw VerificacionCaso.noDisponible(); }
        var stored = updates.findById(caseId).orElseThrow(
                () -> new CasoDomainException("CASE_NOT_FOUND", "Case does not exist")).caso();
        if (!user.toString().equalsIgnoreCase(stored.docenteId())) { throw denied(); }
        ContenidoEvaluableLock.assertDraftEditable(stored);
        String expected = fingerprint.get();
        CasoUpdateCommand parsed = Objects.requireNonNull(command.get());
        try {
            return updates.update(caseId, expected, List.of(parsed));
        } catch (CasoDomainException classified) {
            throw classified;
        } catch (RuntimeException failure) {
            if (hasUniqueViolation(failure)) {
                throw new CasoDomainException(CODE_CONTENT_CONFLICT, "The content conflicts with existing elements");
            }
            throw failure;
        }
    }

    private static boolean hasUniqueViolation(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql && SQL_UNIQUE_VIOLATION.equals(sql.getSQLState())) { return true; }
        }
        return false;
    }

    private static CasoDomainException denied() {
        return VerificacionCaso.denegado(DENEGADO);
    }
}
