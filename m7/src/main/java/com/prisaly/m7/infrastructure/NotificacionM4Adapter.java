package com.prisaly.m7.infrastructure;

import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m7.application.NotificarConvocatoriaCommand;
import com.prisaly.m7.application.NotificarConvocatoriaService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter hacia {@link NotificacionPort} (Fase 2 productiva).
 * Un retorno normal = solicitud registrada (aviso + correo Pendiente), no entrega SMTP.
 * No se registra como bean productivo en Fase 1; solo pruebas / cableado explícito.
 */
public class NotificacionM4Adapter implements NotificacionPort {

    private final NotificarConvocatoriaService servicio;

    public NotificacionM4Adapter(NotificarConvocatoriaService servicio) {
        this.servicio = Objects.requireNonNull(servicio, "servicio");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notificarConvocatoria(UUID sesionId, UUID estudianteId) {
        try {
            servicio.ejecutar(new NotificarConvocatoriaCommand(sesionId, estudianteId));
        } catch (RuntimeException error) {
            throw new ReglaNegocioException(
                    "M4-PROVEEDOR-NO-DISPONIBLE",
                    "No fue posible registrar la solicitud de notificación de convocatoria");
        }
    }
}
