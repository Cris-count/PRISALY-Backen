package com.prisaly.m1.presentation;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.service.StudentAccessValidationService;
import com.prisaly.m1.application.service.StudentAccessValidationService.ValidatedStudentAccess;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import com.prisaly.m1.presentation.dto.ValidarAccesoEstudianteRequest;
import com.prisaly.m1.presentation.dto.ValidarAccesoEstudianteResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ARQ-008.3 student access validation endpoint (DD-02).
 * Productive path stays 501 while {@code prisaly.m1.student-access.enabled=false}.
 * {@code StudentAccessPort} remains {@code DenyingStudentAccessService} until activation.
 * This route never creates a student session, even when the flag is on.
 */
@RestController
@RequestMapping("/v1/acceso-estudiante")
public class EstudianteAccessController {

    public static final String CODE_ESTUDIANTE_ACCESS_PENDING = "ESTUDIANTE_ACCESS_PENDING_CONTRACT";

    private final StudentAccessValidationService studentAccessValidationService;
    private final boolean studentAccessEnabled;

    public EstudianteAccessController(
            StudentAccessValidationService studentAccessValidationService,
            @Value("${prisaly.m1.student-access.enabled}") boolean studentAccessEnabled) {
        this.studentAccessValidationService = studentAccessValidationService;
        this.studentAccessEnabled = studentAccessEnabled;
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validar(@Valid @RequestBody ValidarAccesoEstudianteRequest request) {
        if (!studentAccessEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(new ErrorResponse(
                            CODE_ESTUDIANTE_ACCESS_PENDING,
                            "Student access must be coordinated with M4/M5; DD-02 is not activated"));
        }
        ValidatedStudentAccess result =
                studentAccessValidationService.validate(request.challengeId(), request.codigo());
        AuthenticatedStudent student = result.student();
        return ResponseEntity.ok(new ValidarAccesoEstudianteResponse(
                student.userId(),
                student.studentId(),
                result.challengeId(),
                result.estado()));
    }
}
