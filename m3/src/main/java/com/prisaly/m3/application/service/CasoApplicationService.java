package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.domain.rule.CasoPublication;
import com.prisaly.m3.domain.rule.ContenidoEvaluableLock;

/**
 * Application use cases for academic cases. Coordinates authorization and domain rules.
 */
public final class CasoApplicationService {

    public static final String CODE_FORBIDDEN = "FORBIDDEN";

    private final CaseAuthorizationPort authorizationPort;

    public CasoApplicationService(CaseAuthorizationPort authorizationPort) {
        if (authorizationPort == null) {
            throw new IllegalArgumentException("authorizationPort is required");
        }
        this.authorizationPort = authorizationPort;
    }

    public Caso createDraft(String userId, CasoBuilder builder) {
        Caso draft = builder.buildDraft();
        assertCanModify(userId, draft.id());
        return draft;
    }

    public Caso publish(String userId, Caso caso) {
        assertCanPublish(userId, caso.id());
        return CasoPublication.publish(caso);
    }

    public Caso replaceEvaluableContent(String userId, Caso caso, Caso replacementDraft) {
        assertCanModify(userId, caso.id());
        ContenidoEvaluableLock.assertEditable(caso);
        return replacementDraft;
    }

    public Caso lockAfterFirstSessionScheduled(Caso caso) {
        return ContenidoEvaluableLock.lockAfterFirstSessionScheduled(caso);
    }

    public Caso createIndependentCopy(String userId, Caso source, String newCaseId) {
        assertCanModify(userId, source.id());
        Caso copy = ContenidoEvaluableLock.createIndependentCopy(source, newCaseId);
        assertCanModify(userId, copy.id());
        return copy;
    }

    public boolean isAvailableForScheduling(Caso caso) {
        return caso.isPublished();
    }

    private void assertCanModify(String userId, String caseId) {
        if (!authorizationPort.canModifyCase(userId, caseId)) {
            throw new CasoDomainException(
                    CODE_FORBIDDEN,
                    "User " + userId + " is not authorized to modify case " + caseId);
        }
    }

    private void assertCanPublish(String userId, String caseId) {
        if (!authorizationPort.canPublishCase(userId, caseId)) {
            throw new CasoDomainException(
                    CODE_FORBIDDEN,
                    "User " + userId + " is not authorized to publish case " + caseId);
        }
    }
}
