package com.prisaly.m7.application;

import com.prisaly.m7.domain.AvisoConvocatoria;
import com.prisaly.m7.domain.EnvioCorreoConvocatoria;
import com.prisaly.m7.domain.ConvocatoriaNotificable;
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
    private final RegistroAvisoPort registroAviso;
    private final RegistroEnvioCorreoPort registroEnvio;
    private final String plantillaContenido;
    private final String plantillaAsunto;

    public NotificarConvocatoriaService(
            ContextoConvocatoriaPort contexto,
            RegistroAvisoPort registroAviso,
            RegistroEnvioCorreoPort registroEnvio,
            String plantillaContenido,
            String plantillaAsunto) {
        this.contexto = Objects.requireNonNull(contexto, "contexto");
        this.registroAviso = Objects.requireNonNull(registroAviso, "registroAviso");
        this.registroEnvio = Objects.requireNonNull(registroEnvio, "registroEnvio");
        if (plantillaContenido == null || plantillaContenido.isBlank()) {
            throw new IllegalArgumentException("plantillaContenido is required");
        }
        if (plantillaAsunto == null || plantillaAsunto.isBlank()) {
            throw new IllegalArgumentException("plantillaAsunto is required");
        }
        this.plantillaContenido = plantillaContenido;
        this.plantillaAsunto = plantillaAsunto;
    }

    private static final int LIMITE_ASUNTO = 255;

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
        String contenido = componer(plantillaContenido, evento).strip();
        String asunto = limitar(componer(plantillaAsunto, evento).strip(), LIMITE_ASUNTO);
        registroAviso.registrar(new AvisoConvocatoria(evento, contenido));
        registroEnvio.encolar(new EnvioCorreoConvocatoria(evento, asunto, contenido));
    }

    private static String componer(String plantilla, ConvocatoriaNotificable evento) {
        return plantilla
                .replace("{sesionId}", evento.sesionId().toString())
                .replace("{nombreCaso}", texto(evento.nombreCaso()))
                .replace("{nombreSesion}", texto(evento.nombreSesion()))
                .replace("{inicioPrevisto}", texto(evento.inicioPrevisto()))
                .replace("{finPrevisto}", texto(evento.finPrevisto()))
                .replace("{mensajePersonalizado}", texto(evento.mensajePersonalizado()));
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private static String limitar(String valor, int maximo) {
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
