package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;
import java.util.UUID;

public interface ContextoConvocatoriaPort {
    /** Resuelve la convocatoria persistida y Estudiante.idUsuario en la transacción actual. */
    ConvocatoriaNotificable resolver(UUID sesionId, UUID estudianteId);
}
