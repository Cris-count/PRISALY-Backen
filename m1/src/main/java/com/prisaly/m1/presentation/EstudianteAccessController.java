package com.prisaly.m1.presentation;

import com.prisaly.m1.presentation.dto.ErrorResponse;
import com.prisaly.m1.presentation.dto.ValidarAccesoEstudianteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ARQ-008.3 student access validation route (DD-02). It always answers 501: alternative A
 * (own account, activation and password) uses {@code /activar} and {@code /autenticar}, and the
 * former in-memory challenge (plain-text codes, no expiry nor attempt limit) was removed in
 * DC-M1-POSTCIERRE-02. This route never validates codes nor discloses identities.
 */
@RestController
@RequestMapping("/v1/acceso-estudiante")
public class EstudianteAccessController {

    public static final String CODE_ESTUDIANTE_ACCESS_PENDING = "ESTUDIANTE_ACCESS_PENDING_CONTRACT";

    /** Ruta retirada. Siempre responde 501 y no comprueba el código. */
    @PostMapping("/validar")
    public ResponseEntity<ErrorResponse> validar(@Valid @RequestBody ValidarAccesoEstudianteRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ErrorResponse(
                        CODE_ESTUDIANTE_ACCESS_PENDING,
                        "Student access must be coordinated with M4/M5; DD-02 is not activated"));
    }
}
