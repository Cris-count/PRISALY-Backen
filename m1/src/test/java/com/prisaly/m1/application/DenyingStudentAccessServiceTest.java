package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import org.junit.jupiter.api.Test;

class DenyingStudentAccessServiceTest {

    @Test
    void requireCurrentStudent_alwaysDeniesWithDomainCode() {
        DenyingStudentAccessService service = new DenyingStudentAccessService();

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                service::requireCurrentStudent);

        assertEquals(DenyingStudentAccessService.CODE_STUDENT_IDENTITY_UNAVAILABLE, error.code());
    }
}
