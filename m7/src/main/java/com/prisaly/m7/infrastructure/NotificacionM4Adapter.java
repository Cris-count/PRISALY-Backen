package com.prisaly.m7.infrastructure;

import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m7.application.NotificarConvocatoriaCommand;
import com.prisaly.m7.application.NotificarConvocatoriaService;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proveedor productivo de {@link NotificacionPort}; publicado por {@link M7Configuration} y
 * sustituye al fallback de M4 ({@code @ConditionalOnMissingBean}).
 * Un retorno normal = solicitud registrada (aviso + correo Pendiente), no entrega SMTP.
 * Cualquier fallo se traduce a {@code M4-PROVEEDOR-NO-DISPONIBLE} con mensaje seguro; la causa
 * técnica queda encadenada y en el log, nunca en el cuerpo HTTP.
 */
public class NotificacionM4Adapter implements NotificacionPort {

    private static final Logger log = LoggerFactory.getLogger(NotificacionM4Adapter.class);

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
            log.atWarn()
                    .setMessage("M7 could not register convocatoria notification")
                    .addKeyValue("sesionId", sesionId)
                    .addKeyValue("estudianteId", estudianteId)
                    .addKeyValue("causa", error.getClass().getName())
                    .setCause(error)
                    .log();
            var rechazo = new ReglaNegocioException(
                    "M4-PROVEEDOR-NO-DISPONIBLE",
                    "No fue posible registrar la solicitud de notificación de convocatoria");
            rechazo.initCause(error);
            throw rechazo;
        }
    }
}
