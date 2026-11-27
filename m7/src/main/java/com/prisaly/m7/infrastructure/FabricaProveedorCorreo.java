package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ProveedorCorreoPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * DC-DEPLOY-03: chooses how M7 delivers mail, without changing who sends what. One switch for the three existing
 * channels (convocation worker, teacher activation, student access codes):
 * <ul>
 *   <li>{@code prisaly.m7.correo.transporte=smtp} (default): {@link JavaMailProveedorCorreoAdapter} with
 *       {@code spring.mail.*}, exactly as before;</li>
 *   <li>{@code prisaly.m7.correo.transporte=http}: {@link HttpApiProveedorCorreoAdapter}, with
 *       {@code prisaly.m7.correo.http.clave} (mandatory, secret), {@code prisaly.m7.correo.http.url}
 *       (default {@value #URL_POR_DEFECTO}) and {@code prisaly.m7.correo.http.timeout-ms} (default 10000).</li>
 * </ul>
 * Each caller gets its own instance: the adapters stay private to each channel, as before.
 */
public final class FabricaProveedorCorreo {

    public static final String URL_POR_DEFECTO = "https://api.resend.com/emails";

    private FabricaProveedorCorreo() {}

    public static ProveedorCorreoPort crear(Environment env, ObjectProvider<JavaMailSender> mailSender) {
        String transporte = env.getProperty("prisaly.m7.correo.transporte", "smtp").trim().toLowerCase(Locale.ROOT);
        switch (transporte) {
            case "smtp" -> {
                JavaMailSender sender = mailSender.getIfAvailable();
                if (sender == null) {
                    throw new IllegalStateException(
                            "M7-CORREO-SMTP: prisaly.m7.correo.transporte=smtp requires spring.mail.host");
                }
                return new JavaMailProveedorCorreoAdapter(sender);
            }
            case "http" -> {
                String clave = env.getProperty("prisaly.m7.correo.http.clave", "").trim();
                if (clave.isEmpty()) {
                    throw new IllegalStateException(
                            "M7-CORREO-HTTP-CLAVE: prisaly.m7.correo.transporte=http requires prisaly.m7.correo.http.clave");
                }
                URI url = URI.create(env.getProperty("prisaly.m7.correo.http.url", URL_POR_DEFECTO).trim());
                long ms = env.getProperty("prisaly.m7.correo.http.timeout-ms", Long.class, 10_000L);
                if (ms < 1_000 || ms > 60_000) {
                    throw new IllegalStateException("M7-CORREO-HTTP-TIMEOUT: timeout-ms must be between 1000 and 60000");
                }
                HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(ms))
                        .followRedirects(HttpClient.Redirect.NEVER).build();
                return new HttpApiProveedorCorreoAdapter(cliente, url, clave, Duration.ofMillis(ms));
            }
            default -> throw new IllegalStateException(
                    "M7-CORREO-TRANSPORTE: prisaly.m7.correo.transporte must be smtp or http");
        }
    }
}
