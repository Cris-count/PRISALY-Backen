package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.api.IdentityDomainException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Adaptador que niega. Está activo cuando el flag de acceso estudiantil es falso.
 * requireCurrentStudent() siempre lanza STUDENT_IDENTITY_UNAVAILABLE. No lee el
 * SecurityContext ni acepta un id de estudiante enviado por el cliente.
 */
@Service
@ConditionalOnProperty(
        name = "prisaly.m1.student-access.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DenyingStudentAccessService implements StudentAccessPort {

    public static final String CODE_STUDENT_IDENTITY_UNAVAILABLE = "STUDENT_IDENTITY_UNAVAILABLE";

    @Override
    public AuthenticatedStudent requireCurrentStudent() {
        throw new IdentityDomainException(
                CODE_STUDENT_IDENTITY_UNAVAILABLE,
                "Student identity is unavailable until DD-02 student authentication is ratified");
    }
}
