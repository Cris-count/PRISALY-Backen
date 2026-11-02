package com.prisaly.m7.infrastructure;

import com.prisaly.m1.application.port.CodigoAccesoDeliveryCommand;
import com.prisaly.m1.application.port.StudentAccessDeliveryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prep outbox for DD-02 student access codes. Does not use {@code NotificacionPort},
 * {@code EnvioCorreo}, or productive SMTP.
 */
public final class InMemoryStudentAccessDeliveryAdapter implements StudentAccessDeliveryPort {

    public enum EstadoEntrega {
        PENDIENTE,
        ENTREGADO_PREP
    }

    private final ConcurrentHashMap<String, DeliveryRecord> byIdempotencia = new ConcurrentHashMap<>();

    @Override
    public void entregarCodigo(CodigoAccesoDeliveryCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.challengeId(), "challengeId");
        Objects.requireNonNull(command.correoDestinatario(), "correoDestinatario");
        Objects.requireNonNull(command.codigoTemporal(), "codigoTemporal");
        Objects.requireNonNull(command.fechaExpiracion(), "fechaExpiracion");
        Objects.requireNonNull(command.claveIdempotencia(), "claveIdempotencia");
        if (command.claveIdempotencia().isBlank()) {
            throw new IllegalArgumentException("claveIdempotencia must not be blank");
        }
        if (!command.codigoTemporal().matches("\\d{8}")) {
            throw new IllegalArgumentException("codigoTemporal must be exactly 8 digits");
        }

        DeliveryRecord nuevo = new DeliveryRecord(
                command.challengeId(),
                command.correoDestinatario(),
                command.codigoTemporal(),
                command.fechaExpiracion(),
                command.claveIdempotencia(),
                EstadoEntrega.PENDIENTE);
        DeliveryRecord previo = byIdempotencia.putIfAbsent(command.claveIdempotencia(), nuevo);
        if (previo != null) {
            throw new IllegalStateException(
                    "Duplicate student-access delivery idempotency key: " + command.claveIdempotencia());
        }
    }

    /**
     * Marks pending outbox rows as delivered without calling SMTP (prep / evidence only).
     */
    public int entregarPendientesSinSmtp() {
        int entregados = 0;
        for (var entry : byIdempotencia.entrySet()) {
            DeliveryRecord actual = entry.getValue();
            if (actual.estado() != EstadoEntrega.PENDIENTE) {
                continue;
            }
            DeliveryRecord marcado = actual.conEstado(EstadoEntrega.ENTREGADO_PREP);
            if (byIdempotencia.replace(entry.getKey(), actual, marcado)) {
                entregados++;
            }
        }
        return entregados;
    }

    public List<DeliveryRecord> registros() {
        return List.copyOf(new ArrayList<>(byIdempotencia.values()));
    }

    public record DeliveryRecord(
            String challengeId,
            String correoDestinatario,
            String codigoTemporal,
            Instant fechaExpiracion,
            String claveIdempotencia,
            EstadoEntrega estado
    ) {
        DeliveryRecord conEstado(EstadoEntrega nuevoEstado) {
            return new DeliveryRecord(
                    challengeId,
                    correoDestinatario,
                    codigoTemporal,
                    fechaExpiracion,
                    claveIdempotencia,
                    nuevoEstado);
        }
    }
}
