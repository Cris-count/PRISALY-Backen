package com.prisaly.security;

import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only probe registered via {@code @Import} in security ITs.
 * Maps StudentAccessPort denial to HTTP 403 without a production ARQ-008.3 route.
 */
@RestController
@RequestMapping("/v1/test/student-access")
public class StudentAccessProbeTestController {

    private final StudentAccessPort studentAccessPort;

    public StudentAccessProbeTestController(StudentAccessPort studentAccessPort) {
        this.studentAccessPort = studentAccessPort;
    }

    @GetMapping
    public ResponseEntity<ErrorResponse> probe() {
        try {
            studentAccessPort.requireCurrentStudent();
            return ResponseEntity.ok(new ErrorResponse("UNEXPECTED", "Student identity should be unavailable"));
        } catch (IdentityDomainException ex) {
            if (DenyingStudentAccessService.CODE_STUDENT_IDENTITY_UNAVAILABLE.equals(ex.code())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(ex.code(), ex.getMessage()));
            }
            throw ex;
        }
    }
}
