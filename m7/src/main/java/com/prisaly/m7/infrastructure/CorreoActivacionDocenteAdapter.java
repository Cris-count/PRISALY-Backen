package com.prisaly.m7.infrastructure;

import com.prisaly.m1.application.port.EntregaActivacionDocentePort;
import com.prisaly.m7.application.ProveedorCorreoPort;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * D-05 (DC-BE-D05-AUTORIZACION-QA-77): synchronous invitation mail (Mailpit in QA). The message carries the one-time
 * activation code and its expiry, <b>never a password</b>. It runs inside the issue transaction: a rejected message
 * raises {@link EntregaFallidaException} and the new token is rolled back. Logs carry the user id and the SMTP
 * outcome only, never the code or the address.
 */
public class CorreoActivacionDocenteAdapter implements EntregaActivacionDocentePort {
    private static final Logger log = LoggerFactory.getLogger(CorreoActivacionDocenteAdapter.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    static final String ASUNTO = "PRISALY: activa tu cuenta docente";

    private final ProveedorCorreoPort proveedor;
    private final String remitente;
    private final String enlace;

    /** @param enlace optional base URL of the activation screen; the code is appended as a URL fragment */
    public CorreoActivacionDocenteAdapter(ProveedorCorreoPort proveedor, String remitente, String enlace) {
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
        this.remitente = Objects.requireNonNull(remitente, "remitente");
        this.enlace = enlace == null ? "" : enlace.trim();
    }

    @Override
    public void entregar(UUID idUsuario, String correo, String token, Instant expiraEn) {
        ResultadoEnvioSmtp resultado = proveedor.enviar(remitente, correo, ASUNTO, contenido(token, expiraEn));
        if (resultado instanceof ResultadoEnvioSmtp.Rechazado rechazo) {
            log.warn("ACTIVACION_DOCENTE_CORREO usuario={} resultado=RECHAZADO codigo={} clase={}", idUsuario,
                    rechazo.codigo(), rechazo.clase());
            throw new EntregaFallidaException("SMTP " + rechazo.codigo());
        }
        log.info("ACTIVACION_DOCENTE_CORREO usuario={} resultado=ACEPTADO", idUsuario);
    }

    String contenido(String token, Instant expiraEn) {
        StringBuilder texto = new StringBuilder()
                .append("Se creó tu cuenta docente en PRISALY.\n\n")
                .append("Para definir tu contraseña usa este código de activación de un solo uso:\n\n")
                .append(token).append("\n\n")
                .append("Vence: ").append(FECHA.format(expiraEn)).append(".\n");
        if (!enlace.isEmpty()) {
            texto.append("\nPantalla de activación: ").append(enlace).append("#token=").append(token).append('\n');
        }
        return texto.append("\nPRISALY nunca te enviará una contraseña por correo. Si no esperabas este mensaje, ")
                .append("ignóralo: la cuenta no se activa sin el código.\n").toString();
    }
}
