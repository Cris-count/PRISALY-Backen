package com.prisaly.m3.application.port;

/**
 * Authorization contract consumed from M1 identity context.
 * M3 does not implement authentication.
 */
public interface CaseAuthorizationPort {

    boolean canModifyCase(String userId, String caseId);

    boolean canPublishCase(String userId, String caseId);
}
