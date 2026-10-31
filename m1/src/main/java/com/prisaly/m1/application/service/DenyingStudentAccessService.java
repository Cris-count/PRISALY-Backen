package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import org.springframework.stereotype.Service;

/**
 * Provisional production adapter while DD-02 (student authentication) remains open.
 * Never reads SecurityContext or client-supplied ids; never treats personnel tokens as students.
 */
@Service
public class DenyingStudentAccessService implements StudentAccessPort {

    public static final String CODE_STUDENT_IDENTITY_UNAVAILABLE = "STUDENT_IDENTITY_UNAVAILABLE";

    @Override
    public AuthenticatedStudent requireCurrentStudent() {
        throw new IdentityDomainException(
                CODE_STUDENT_IDENTITY_UNAVAILABLE,
                "Student identity is unavailable until DD-02 student authentication is ratified");
    }
}
