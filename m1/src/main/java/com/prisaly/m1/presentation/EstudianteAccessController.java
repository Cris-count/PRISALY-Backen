package com.prisaly.m1.presentation;

import com.prisaly.m1.presentation.dto.ErrorResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ARQ-008.3 route reserved for student convocatoria access.
 * Policy and code validation remain coordinated with M4/M5 (DD-02 open).
 */
@RestController
@RequestMapping("/v1/acceso-estudiante")
public class EstudianteAccessController {

    @PostMapping("/validar")
    public ResponseEntity<ErrorResponse> validar(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ErrorResponse(
                        "ESTUDIANTE_ACCESS_PENDING_CONTRACT",
                        "Student access must be coordinated with M4/M5; DD-02 is not ratified"));
    }
}
