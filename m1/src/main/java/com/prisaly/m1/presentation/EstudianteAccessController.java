package com.prisaly.m1.presentation;

import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.service.StudentAccessValidationService;
import com.prisaly.m1.application.service.StudentAccessValidationService.ValidatedStudentAccess;
import com.prisaly.m1.presentation.dto.ValidarAccesoEstudianteRequest;
import com.prisaly.m1.presentation.dto.ValidarAccesoEstudianteResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ARQ-008.3 student access validation endpoint (DD-02).
 * {@code StudentAccessPort} remains {@code DenyingStudentAccessService} until session activation.
 */
@RestController
@RequestMapping("/v1/acceso-estudiante")
public class EstudianteAccessController {

    private final StudentAccessValidationService studentAccessValidationService;

    public EstudianteAccessController(StudentAccessValidationService studentAccessValidationService) {
        this.studentAccessValidationService = studentAccessValidationService;
    }

    @PostMapping("/validar")
    public ValidarAccesoEstudianteResponse validar(@Valid @RequestBody ValidarAccesoEstudianteRequest request) {
        ValidatedStudentAccess result =
                studentAccessValidationService.validate(request.challengeId(), request.codigo());
        AuthenticatedStudent student = result.student();
        return new ValidarAccesoEstudianteResponse(
                student.userId(),
                student.studentId(),
                result.challengeId(),
                result.estado());
    }
}
