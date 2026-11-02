package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registers a one-time challenge in M1 and hands delivery to M7 via
 * {@link StudentAccessDeliveryPort}. No SMTP; productive OTP remains off.
 */
public final class StudentAccessCodeIssuanceService {

    private final StudentAccessValidationService validationService;
    private final StudentAccessDeliveryPort deliveryPort;

    public StudentAccessCodeIssuanceService(
            StudentAccessValidationService validationService,
            StudentAccessDeliveryPort deliveryPort) {
        this.validationService = Objects.requireNonNull(validationService, "validationService");
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort");
    }

    /**
     * Test/prep issuance: registers the challenge then enqueues delivery in M7.
     * Returns the plain code only for evidence harnesses — not a public HTTP contract.
     */
    public IssuedAccessCode emitirCodigoDePrueba(
            String challengeId,
            String codigo,
            String correoDestinatario,
            UUID userId,
            UUID studentId,
            Instant fechaExpiracion,
            String claveIdempotencia) {
        Objects.requireNonNull(correoDestinatario, "correoDestinatario");
        Objects.requireNonNull(fechaExpiracion, "fechaExpiracion");
        Objects.requireNonNull(claveIdempotencia, "claveIdempotencia");
        if (correoDestinatario.isBlank()) {
            throw new IllegalArgumentException("correoDestinatario must not be blank");
        }
        if (claveIdempotencia.isBlank()) {
            throw new IllegalArgumentException("claveIdempotencia must not be blank");
        }

        validationService.registerChallenge(challengeId, codigo, userId, studentId);
        deliveryPort.entregarCodigo(new CodigoAccesoDeliveryCommand(
                challengeId,
                correoDestinatario,
                codigo,
                fechaExpiracion,
                claveIdempotencia));
        return new IssuedAccessCode(challengeId, codigo, userId, studentId, correoDestinatario);
    }

    public record IssuedAccessCode(
            String challengeId,
            String codigo,
            UUID userId,
            UUID studentId,
            String correoDestinatario
    ) {
    }
}
