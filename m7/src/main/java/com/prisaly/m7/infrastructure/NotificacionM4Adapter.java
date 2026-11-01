package com.prisaly.m7.infrastructure;

import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m7.application.ContextoConvocatoriaPort;
import com.prisaly.m7.application.PublicadorConvocatoria;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Adapter de entrada. Un retorno normal significa registro interno en la transacción vigente. */
public class NotificacionM4Adapter implements NotificacionPort {
    private final ContextoConvocatoriaPort contexto;
    private final PublicadorConvocatoria publicador;

    public NotificacionM4Adapter(ContextoConvocatoriaPort contexto, PublicadorConvocatoria publicador) {
        this.contexto = Objects.requireNonNull(contexto);
        this.publicador = Objects.requireNonNull(publicador);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notificarConvocatoria(UUID sesionId, UUID estudianteId) {
        try {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw new IllegalStateException("Se requiere transacción de escritura");
            }
            Objects.requireNonNull(sesionId);
            Objects.requireNonNull(estudianteId);
            var evento = contexto.resolver(sesionId, estudianteId);
            if (!sesionId.equals(evento.sesionId()) || !estudianteId.equals(evento.estudianteId())) {
                throw new IllegalStateException("Contexto de convocatoria inconsistente");
            }
            publicador.publicar(evento);
        } catch (RuntimeException error) {
            // Conservar contrato HTTP M4 sin filtrar SQL, correo ni identificadores.
            throw new ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE",
                    "No fue posible registrar el aviso interno de convocatoria");
        }
    }
}
