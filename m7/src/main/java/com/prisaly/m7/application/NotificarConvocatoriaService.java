package com.prisaly.m7.application;

import com.prisaly.m7.domain.ConvocatoriaNotificable;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordina aviso + encolado de correo de forma atómica en la TX del llamador.
 * Retorno normal = solicitud registrada (Pendiente), no entrega SMTP.
 */
public class NotificarConvocatoriaService {

    private final ContextoConvocatoriaPort contexto;
    private final PublicadorConvocatoria publicador;

    public NotificarConvocatoriaService(
            ContextoConvocatoriaPort contexto,
            RegistroAvisoPort registroAviso,
            RegistroEnvioCorreoPort registroEnvio,
            String plantillaContenido,
            String plantillaAsunto) {
        this.contexto = Objects.requireNonNull(contexto, "contexto");
        if (plantillaContenido == null || plantillaContenido.isBlank()) {
            throw new IllegalArgumentException("plantillaContenido is required");
        }
        if (plantillaAsunto == null || plantillaAsunto.isBlank()) {
            throw new IllegalArgumentException("plantillaAsunto is required");
        }
        this.publicador = new PublicadorConvocatoria(
                new ObservadorRegistroAviso(Objects.requireNonNull(registroAviso, "registroAviso"), plantillaContenido),
                List.of(new ObservadorEncoladoCorreo(registroEnvio, plantillaAsunto, plantillaContenido)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ejecutar(NotificarConvocatoriaCommand command) {
        Objects.requireNonNull(command, "command");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Se requiere transacción de escritura");
        }
        var evento = contexto.resolver(command.sesionId(), command.estudianteId());
        if (!command.sesionId().equals(evento.sesionId())
                || !command.estudianteId().equals(evento.estudianteId())) {
            throw new IllegalStateException("Contexto de convocatoria inconsistente");
        }
        publicador.publicar(evento);
    }
}
