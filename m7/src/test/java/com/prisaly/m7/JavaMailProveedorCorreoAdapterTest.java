package com.prisaly.m7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import com.prisaly.m7.infrastructure.JavaMailProveedorCorreoAdapter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * A-04 (DC-AUD-01) / DC-CIERRE-NOCTURNO: translation of SMTP outcomes by {@link JavaMailProveedorCorreoAdapter}.
 * Covers {@code rechazado} and {@code codigoDe}, which had 0 % coverage. The protocol tests use a minimal local SMTP
 * server so that the exceptions are the ones the real {@link JavaMailSenderImpl} produces (no mocks of Spring Mail).
 */
class JavaMailProveedorCorreoAdapterTest {

    private static final String REMITENTE = "no-reply@prisaly.test";
    private static final String DESTINATARIO = "estudiante@prisaly.test";

    // ---------------------------------------------------------------- real SMTP dialogue

    @Test
    void servidorQueAceptaDevuelveAceptadoConMessageId() throws Exception {
        try (ServidorSmtpFalso smtp = ServidorSmtpFalso.iniciar(rcpt -> "250 2.1.5 OK")) {
            ResultadoEnvioSmtp r = adaptador(smtp.puerto()).enviar(REMITENTE, DESTINATARIO, "Asunto", "Cuerpo");
            ResultadoEnvioSmtp.Aceptado aceptado = assertInstanceOf(ResultadoEnvioSmtp.Aceptado.class, r);
            assertTrue(aceptado.messageId().startsWith("<"), aceptado.messageId());
        }
    }

    @Test
    void destinatarioInexistenteConservaElCodigoSmtp550YEsPermanente() throws Exception {
        try (ServidorSmtpFalso smtp = ServidorSmtpFalso.iniciar(rcpt -> "550 5.1.1 User unknown")) {
            ResultadoEnvioSmtp.Rechazado r = assertInstanceOf(ResultadoEnvioSmtp.Rechazado.class,
                    adaptador(smtp.puerto()).enviar(REMITENTE, DESTINATARIO, "Asunto", "Cuerpo"));
            assertEquals("550", r.codigo(), r.detalle());
            assertEquals(ClaseErrorCorreo.PERMANENTE_DESTINATARIO, r.clase());
        }
    }

    @Test
    void buzonTemporalmenteNoDisponibleConservaElCodigo451YEsTransitorio() throws Exception {
        try (ServidorSmtpFalso smtp = ServidorSmtpFalso.iniciar(rcpt -> "451 4.3.0 Try again later")) {
            ResultadoEnvioSmtp.Rechazado r = assertInstanceOf(ResultadoEnvioSmtp.Rechazado.class,
                    adaptador(smtp.puerto()).enviar(REMITENTE, DESTINATARIO, "Asunto", "Cuerpo"));
            assertEquals("451", r.codigo(), r.detalle());
            assertEquals(ClaseErrorCorreo.TRANSITORIO, r.clase());
        }
    }

    @Test
    void rechazoDelMensajeTrasDataConservaElCodigo554() throws Exception {
        try (ServidorSmtpFalso smtp = ServidorSmtpFalso.iniciar(rcpt -> "250 2.1.5 OK", "554 5.7.1 Message rejected")) {
            ResultadoEnvioSmtp.Rechazado r = assertInstanceOf(ResultadoEnvioSmtp.Rechazado.class,
                    adaptador(smtp.puerto()).enviar(REMITENTE, DESTINATARIO, "Asunto", "Cuerpo"));
            assertEquals("554", r.codigo(), r.detalle());
        }
    }

    @Test
    void servidorInalcanzableEsTransitorio() throws Exception {
        int puertoCerrado;
        try (ServerSocket s = new ServerSocket(0)) {
            puertoCerrado = s.getLocalPort();
        }
        ResultadoEnvioSmtp.Rechazado r = assertInstanceOf(ResultadoEnvioSmtp.Rechazado.class,
                adaptador(puertoCerrado).enviar(REMITENTE, DESTINATARIO, "Asunto", "Cuerpo"));
        assertEquals("SMTP", r.codigo());
        assertEquals(ClaseErrorCorreo.TRANSITORIO, r.clase());
    }

    // ---------------------------------------------------------------- exception translation branches

    @Test
    void autenticacionFallidaEsConfiguracionCon535() {
        ResultadoEnvioSmtp.Rechazado r = rechazoCon(new MailAuthenticationException("bad credentials"));
        assertEquals("535", r.codigo());
        assertEquals(ClaseErrorCorreo.CONFIGURACION, r.clase());
    }

    @Test
    void causaConSiguienteExcepcionNumericaUsaSusTresPrimerosDigitos() {
        MessagingException causa = new MessagingException("Invalid Addresses", new Exception("553 5.1.3 bad address"));
        ResultadoEnvioSmtp.Rechazado r = rechazoCon(new MailSendException("connection", causa));
        assertEquals("553", r.codigo());
        assertEquals(ClaseErrorCorreo.PERMANENTE_DESTINATARIO, r.clase());
    }

    @Test
    void siguienteExcepcionNoNumericaOVaciaDaCodigoGenerico() {
        assertEquals("SMTP", rechazoCon(new MailSendException("x",
                new MessagingException("m", new Exception("not a code")))).codigo());
        assertEquals("SMTP", rechazoCon(new MailSendException("x",
                new MessagingException("m", new Exception(" ")))).codigo());
        assertEquals("SMTP", rechazoCon(new MailSendException("x",
                new MessagingException("m", new Exception((String) null)))).codigo());
        assertEquals("SMTP", rechazoCon(new MailSendException("x", new MessagingException("m"))).codigo());
        assertEquals("SMTP", rechazoCon(new MailSendException("x", new IllegalStateException("y"))).codigo());
        assertEquals("SMTP", rechazoCon(new MailSendException("12")).codigo());
    }

    @Test
    void otraMailExceptionEsRechazoGenericoYSinMensajeUsaElNombreDeLaClase() {
        ResultadoEnvioSmtp.Rechazado r = rechazoCon(new MailPreparationException((String) null));
        assertEquals("SMTP", r.codigo());
        assertEquals("MailPreparationException", r.detalle());
    }

    // ---------------------------------------------------------------- helpers

    private static JavaMailProveedorCorreoAdapter adaptador(int puerto) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(puerto);
        Properties p = new Properties();
        p.put("mail.smtp.connectiontimeout", "3000");
        p.put("mail.smtp.timeout", "3000");
        sender.setJavaMailProperties(p);
        return new JavaMailProveedorCorreoAdapter(sender);
    }

    private static ResultadoEnvioSmtp.Rechazado rechazoCon(MailException error) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl() {
            @Override
            public void send(MimeMessage mimeMessage) {
                throw error;
            }
        };
        return assertInstanceOf(ResultadoEnvioSmtp.Rechazado.class,
                new JavaMailProveedorCorreoAdapter(sender).enviar(REMITENTE, DESTINATARIO, "A", "C"));
    }

    /** Single-connection SMTP server that answers RCPT TO with a configurable reply. */
    private static final class ServidorSmtpFalso implements AutoCloseable {
        private final ServerSocket servidor;
        private final Thread hilo;

        private ServidorSmtpFalso(ServerSocket servidor, Function<String, String> respuestaRcpt, String respuestaDatos) {
            this.servidor = servidor;
            this.hilo = new Thread(() -> atender(respuestaRcpt, respuestaDatos), "smtp-falso");
            this.hilo.setDaemon(true);
            this.hilo.start();
        }

        static ServidorSmtpFalso iniciar(Function<String, String> respuestaRcpt) throws Exception {
            return iniciar(respuestaRcpt, "250 2.0.0 queued");
        }

        static ServidorSmtpFalso iniciar(Function<String, String> respuestaRcpt, String respuestaDatos)
                throws Exception {
            return new ServidorSmtpFalso(new ServerSocket(0), respuestaRcpt, respuestaDatos);
        }

        int puerto() {
            return servidor.getLocalPort();
        }

        private void atender(Function<String, String> respuestaRcpt, String respuestaDatos) {
            try (Socket s = servidor.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                    OutputStream out = s.getOutputStream()) {
                responder(out, "220 smtp-falso ESMTP");
                String linea;
                boolean enDatos = false;
                while ((linea = in.readLine()) != null) {
                    if (enDatos) {
                        if (linea.equals(".")) {
                            enDatos = false;
                            responder(out, respuestaDatos);
                        }
                        continue;
                    }
                    String cmd = linea.toUpperCase(java.util.Locale.ROOT);
                    if (cmd.startsWith("RCPT")) {
                        responder(out, respuestaRcpt.apply(linea));
                    } else if (cmd.startsWith("DATA")) {
                        enDatos = true;
                        responder(out, "354 End data with <CR><LF>.<CR><LF>");
                    } else if (cmd.startsWith("QUIT")) {
                        responder(out, "221 2.0.0 Bye");
                        return;
                    } else {
                        responder(out, "250 OK");
                    }
                }
            } catch (Exception ignored) {
                // the client may close abruptly after a rejection
            }
        }

        private static void responder(OutputStream out, String linea) throws java.io.IOException {
            out.write((linea + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        @Override
        public void close() throws Exception {
            servidor.close();
            hilo.join(2000);
        }
    }
}
