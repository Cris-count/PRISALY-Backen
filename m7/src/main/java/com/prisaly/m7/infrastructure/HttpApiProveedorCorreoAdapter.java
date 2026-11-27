package com.prisaly.m7.infrastructure;

import com.prisaly.m7.application.ClasificacionErrorCorreo;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DC-DEPLOY-03: the same {@link ProveedorCorreoPort} delivered through a transactional e-mail HTTPS API instead of
 * SMTP, for hosts where outbound SMTP is blocked (Railway Free / Trial / Hobby). Request format of the Resend-compatible
 * {@code POST /emails} API: JSON {@code from, to[], subject, text}, {@code Authorization: Bearer <key>}; answer
 * {@code {"id": "..."}}.
 *
 * <p>HTTP outcomes are translated to the SMTP-style codes that {@link ClasificacionErrorCorreo} already classifies, so the
 * worker's retry policy and the recipient states behave exactly as with SMTP: 401/403 → configuration (535), 400/422 →
 * permanent recipient failure (550), 429 and 5xx → transient (451), network errors → transient. The API key is never
 * part of a result, log or exception message. Acceptance by the API is not mailbox delivery.
 */
public class HttpApiProveedorCorreoAdapter implements ProveedorCorreoPort {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"\\\\]{1,200})\"");
    private static final int MAX_DETALLE = 300;

    private final HttpClient cliente;
    private final URI endpoint;
    private final String clave;
    private final Duration espera;

    public HttpApiProveedorCorreoAdapter(HttpClient cliente, URI endpoint, String clave, Duration espera) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.clave = Objects.requireNonNull(clave, "clave");
        this.espera = Objects.requireNonNull(espera, "espera");
        if (clave.isBlank()) {
            throw new IllegalArgumentException("M7-CORREO-HTTP-CLAVE: the API key is mandatory");
        }
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !"localhost".equalsIgnoreCase(endpoint.getHost())
                && !"127.0.0.1".equals(endpoint.getHost())) {
            throw new IllegalArgumentException("M7-CORREO-HTTP-URL: the API endpoint must use HTTPS");
        }
    }

    @Override
    public ResultadoEnvioSmtp enviar(String remitente, String destinatario, String asunto, String contenido) {
        Objects.requireNonNull(remitente, "remitente");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(asunto, "asunto");
        Objects.requireNonNull(contenido, "contenido");
        String cuerpo = "{\"from\":" + json(remitente) + ",\"to\":[" + json(destinatario) + "],\"subject\":" + json(asunto)
                + ",\"text\":" + json(contenido) + "}";
        HttpRequest peticion = HttpRequest.newBuilder(endpoint)
                .timeout(espera)
                .header("Authorization", "Bearer " + clave)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException error) {
            return rechazado("451", "timeout de la API de correo");
        } catch (IOException error) {
            return rechazado("451", "connection error con la API de correo: " + error.getClass().getSimpleName());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return rechazado("451", "envio interrumpido, try again");
        }
        int estado = respuesta.statusCode();
        String texto = recortar(respuesta.body());
        if (estado >= 200 && estado < 300) {
            Matcher id = ID.matcher(respuesta.body() == null ? "" : respuesta.body());
            return new ResultadoEnvioSmtp.Aceptado(id.find() ? id.group(1) : "sin-message-id");
        }
        if (estado == 401 || estado == 403) {
            return rechazado("535", "HTTP " + estado + " authentication o remitente no autorizado: " + texto);
        }
        if (estado == 400 || estado == 422) {
            return rechazado("550", "HTTP " + estado + " recipient rejected: " + texto);
        }
        if (estado == 429 || estado >= 500) {
            return rechazado("451", "HTTP " + estado + " temporarily unavailable, try again: " + texto);
        }
        return rechazado("554", "HTTP " + estado + ": " + texto);
    }

    private static ResultadoEnvioSmtp.Rechazado rechazado(String codigo, String detalle) {
        return new ResultadoEnvioSmtp.Rechazado(ClasificacionErrorCorreo.clasificar(codigo, detalle), codigo, detalle);
    }

    private static String recortar(String cuerpo) {
        if (cuerpo == null) {
            return "";
        }
        String limpio = cuerpo.replaceAll("\\s+", " ").strip();
        return limpio.length() > MAX_DETALLE ? limpio.substring(0, MAX_DETALLE) + "…" : limpio;
    }

    /** JSON string literal (RFC 8259): quotes, backslash and control characters escaped. */
    static String json(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 2).append('"');
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
