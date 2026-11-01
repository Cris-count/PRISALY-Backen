package com.prisaly.m7.application;

import com.prisaly.m7.domain.EnvioCorreoConvocatoria;

public interface RegistroEnvioCorreoPort {

    /**
     * Encola de forma idempotente. Éxito = fila existente con los mismos datos inmutables
     * (cualquier estado). No envía correo ni reactiva Fallido/Enviado.
     */
    void encolar(EnvioCorreoConvocatoria solicitud);
}
