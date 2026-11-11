package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ClasificacionErrorCorreo;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class JavaMailProveedorCorreoAdapter implements ProveedorCorreoPort {

    private final JavaMailSender mailSender;

    public JavaMailProveedorCorreoAdapter(JavaMailSender mailSender) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
    }

    @Override
    public ResultadoEnvioSmtp enviar(String remitente, String destinatario, String asunto, String contenido) {
        Objects.requireNonNull(remitente, "remitente");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(asunto, "asunto");
        Objects.requireNonNull(contenido, "contenido");
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(contenido, false);
            mailSender.send(mensaje);
            String messageId = mensaje.getMessageID();
            if (messageId == null || messageId.isBlank()) {
                messageId = "sin-message-id";
            }
            return new ResultadoEnvioSmtp.Aceptado(messageId);
        } catch (MailAuthenticationException error) {
            return rechazado("535", detalleDe(error), detalleDe(error));
        } catch (MailSendException error) {
            String respuesta = respuestaSmtpDe(error);
            String codigo = respuesta == null ? "SMTP" : respuesta.substring(0, 3);
            // Classify on the server reply when there is one: the wrapper text ("Invalid Addresses") would otherwise
            // turn a transient 4xx rejection into a permanent recipient failure (DEF-M7-01).
            return rechazado(codigo, respuesta == null ? detalleDe(error) : respuesta, detalleDe(error));
        } catch (MailException | MessagingException error) {
            return rechazado("SMTP", detalleDe(error), detalleDe(error));
        }
    }

    private static ResultadoEnvioSmtp.Rechazado rechazado(String codigo, String textoClasificacion, String detalle) {
        return new ResultadoEnvioSmtp.Rechazado(
                ClasificacionErrorCorreo.clasificar(codigo, textoClasificacion), codigo, detalle);
    }

    private static String detalleDe(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /**
     * First SMTP reply line ("550 5.1.1 ...") found in the failure. {@code JavaMailSenderImpl} reports a recipient
     * rejection through {@link MailSendException#getFailedMessages()} (cause {@code null}) and a connection failure
     * through {@link MailSendException#getCause()}; both are inspected (DEF-M7-01).
     */
    private static String respuestaSmtpDe(MailSendException error) {
        List<Throwable> candidatos = new ArrayList<>();
        if (error.getCause() != null) {
            candidatos.add(error.getCause());
        }
        candidatos.addAll(error.getFailedMessages().values());
        for (Throwable candidato : candidatos) {
            String respuesta = respuestaEnCadena(candidato);
            if (respuesta != null) {
                return respuesta;
            }
        }
        return null;
    }

    private static String respuestaEnCadena(Throwable inicio) {
        Throwable actual = inicio;
        for (int profundidad = 0; actual != null && profundidad < 10; profundidad++) {
            if (esRespuestaSmtp(actual.getMessage())) {
                return actual.getMessage().strip();
            }
            Throwable siguiente = actual instanceof MessagingException messaging ? messaging.getNextException() : null;
            actual = siguiente != null ? siguiente : actual.getCause();
        }
        return null;
    }

    private static boolean esRespuestaSmtp(String mensaje) {
        return mensaje != null && mensaje.length() >= 3 && Character.isDigit(mensaje.charAt(0))
                && Character.isDigit(mensaje.charAt(1)) && Character.isDigit(mensaje.charAt(2));
    }
}
