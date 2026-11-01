package com.prisaly.m7.application;

import java.util.Objects;
import java.util.UUID;

/** Solicitud síncrona de registro de notificación de convocatoria (no evento de dominio). */
public record NotificarConvocatoriaCommand(UUID sesionId, UUID estudianteId) {

    public NotificarConvocatoriaCommand {
        Objects.requireNonNull(sesionId, "sesionId");
        Objects.requireNonNull(estudianteId, "estudianteId");
    }
}
