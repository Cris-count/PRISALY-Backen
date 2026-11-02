package com.prisaly.m1.application.port;

/**
 * M1 → M7 contract for student access code delivery.
 * Distinct from convocatoria {@code NotificacionPort}; M7 implements the adapter.
 */
public interface StudentAccessDeliveryPort {

    void entregarCodigo(CodigoAccesoDeliveryCommand command);
}
