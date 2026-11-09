package com.prisaly.m3.application.port;

/**
 * Authorization contract consumed from M1 identity context.
 * M3 does not implement authentication.
 */
public interface CaseAuthorizationPort {

    boolean canCreateCase(String userId, String docenteId);

    boolean canModifyCase(String userId, String caseId);

    boolean canPublishCase(String userId, String caseId);

    /** PD-02: independent from publication; denied unless a provider grants it explicitly. */
    default boolean canArchiveCase(String userId, String caseId) {
        return false;
    }
}
