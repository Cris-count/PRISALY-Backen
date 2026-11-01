package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ClasificacionErrorCorreo;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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
            return rechazado("535", error);
        } catch (MailSendException error) {
            return rechazado(codigoDe(error), error);
        } catch (MailException | MessagingException error) {
            return rechazado("SMTP", error);
        }
    }

    private static ResultadoEnvioSmtp.Rechazado rechazado(String codigo, Exception error) {
        String detalle = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new ResultadoEnvioSmtp.Rechazado(
                ClasificacionErrorCorreo.clasificar(codigo, detalle), codigo, detalle);
    }

    private static String codigoDe(MailSendException error) {
        Throwable causa = error.getCause();
        if (causa instanceof MessagingException messaging) {
            Exception next = messaging.getNextException();
            if (next != null && next.getMessage() != null && !next.getMessage().isBlank()) {
                String mensaje = next.getMessage();
                if (mensaje.length() >= 3 && Character.isDigit(mensaje.charAt(0))) {
                    return mensaje.substring(0, 3);
                }
            }
        }
        return "SMTP";
    }
}
