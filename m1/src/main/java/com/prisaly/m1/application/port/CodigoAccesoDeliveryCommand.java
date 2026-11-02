package com.prisaly.m1.application.port;

import java.time.Instant;

/**
 * Delivery intent from M1 to M7 for a one-time student access code.
 * Plain {@code codigoTemporal} must exist only in memory toward the encrypting adapter.
 */
public record CodigoAccesoDeliveryCommand(
        String challengeId,
        String correoDestinatario,
        String codigoTemporal,
        Instant fechaExpiracion,
        String claveIdempotencia
) {
}
