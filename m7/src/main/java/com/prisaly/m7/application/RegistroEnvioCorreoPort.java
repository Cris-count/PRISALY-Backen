package com.prisaly.m7.application;

import com.prisaly.m7.domain.EnvioCorreoConvocatoria;

public interface RegistroEnvioCorreoPort {

    /**
     * Encola de forma idempotente. Éxito = fila Pendiente consistente con la solicitud.
     * No envía correo.
     */
    void encolar(EnvioCorreoConvocatoria solicitud);
}
