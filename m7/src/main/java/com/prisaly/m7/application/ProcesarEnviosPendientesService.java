package com.prisaly.m7.application;

import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.EnvioReclamado;
import com.prisaly.m7.domain.EstadoEnvioCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claims Pendiente rows, sends SMTP outside the claim transaction, updates only while lease is held.
 * Delivery is at-least-once; SMTP acceptance does not prove mailbox delivery.
 */
public class ProcesarEnviosPendientesService {

    private static final Logger log = LoggerFactory.getLogger(ProcesarEnviosPendientesService.class);

    private final ReclamoEnvioPort reclamo;
    private final ProveedorCorreoPort proveedor;
    private final PoliticaReintentos politica;
    private final String remitente;
    private final int lote;
    private final Duration lease;

    public ProcesarEnviosPendientesService(
            ReclamoEnvioPort reclamo,
            ProveedorCorreoPort proveedor,
            PoliticaReintentos politica,
            String remitente,
            int lote,
            Duration lease) {
        this.reclamo = Objects.requireNonNull(reclamo, "reclamo");
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
        this.politica = Objects.requireNonNull(politica, "politica");
        if (remitente == null || remitente.isBlank()) {
            throw new IllegalArgumentException("remitente is required");
        }
        if (lote < 1) {
            throw new IllegalArgumentException("lote must be >= 1");
        }
        this.remitente = remitente;
        this.lote = lote;
        this.lease = Objects.requireNonNull(lease, "lease");
        if (lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("lease must be positive");
        }
    }

    public int procesarLote() {
        List<EnvioReclamado> reclamados = reclamo.reclamar(lote, lease, politica.maxIntentos());
        int procesados = 0;
        for (EnvioReclamado envio : reclamados) {
            procesarUno(envio);
            procesados++;
        }
        return procesados;
    }

    private void procesarUno(EnvioReclamado envio) {
        var leaseHeld = new ReclamoEnvioPort.InstantLease(envio.leaseHasta(), envio.numeroIntentos());
        ResultadoEnvioSmtp resultado;
        try {
            resultado = proveedor.enviar(remitente, envio.correoDestino(), envio.asunto(), envio.contenido());
        } catch (RuntimeException error) {
            resultado = new ResultadoEnvioSmtp.Rechazado(
                    ClaseErrorCorreo.TRANSITORIO,
                    "EXCEPCION",
                    error.getClass().getSimpleName() + ": " + safe(error.getMessage()));
        }

        if (resultado instanceof ResultadoEnvioSmtp.Aceptado aceptado) {
            boolean guardado = reclamo.registrarAceptacion(envio.idEnvio(), leaseHeld, aceptado.messageId());
            log.atInfo()
                    .setMessage("SMTP accepted convocatoria email")
                    .addKeyValue("idEnvio", envio.idEnvio())
                    .addKeyValue("intento", envio.numeroIntentos())
                    .addKeyValue("messageId", aceptado.messageId())
                    .addKeyValue("leaseConservado", guardado)
                    .log();
            return;
        }

        ResultadoEnvioSmtp.Rechazado rechazo = (ResultadoEnvioSmtp.Rechazado) resultado;
        aplicarRechazo(envio, leaseHeld, rechazo);
    }

    private void aplicarRechazo(
            EnvioReclamado envio, ReclamoEnvioPort.InstantLease leaseHeld, ResultadoEnvioSmtp.Rechazado rechazo) {
        String error = rechazo.codigo() + ": " + rechazo.detalle();
        if (rechazo.clase() == ClaseErrorCorreo.CONFIGURACION) {
            Duration espera = politica.esperaTrasIntento(envio.numeroIntentos());
            boolean guardado = reclamo.registrarFallo(
                    envio.idEnvio(), leaseHeld, EstadoEnvioCorreo.PENDIENTE, error, espera);
            log.atError()
                    .setMessage("SMTP configuration failure; recipient not marked Fallido")
                    .addKeyValue("idEnvio", envio.idEnvio())
                    .addKeyValue("intento", envio.numeroIntentos())
                    .addKeyValue("codigo", rechazo.codigo())
                    .addKeyValue("leaseConservado", guardado)
                    .log();
            return;
        }

        if (rechazo.clase() == ClaseErrorCorreo.PERMANENTE_DESTINATARIO
                || politica.agotoIntentos(envio.numeroIntentos())) {
            boolean guardado = reclamo.registrarFallo(
                    envio.idEnvio(), leaseHeld, EstadoEnvioCorreo.FALLIDO, error, Duration.ZERO);
            log.atWarn()
                    .setMessage("Email marked Fallido")
                    .addKeyValue("idEnvio", envio.idEnvio())
                    .addKeyValue("intento", envio.numeroIntentos())
                    .addKeyValue("clase", rechazo.clase().name())
                    .addKeyValue("leaseConservado", guardado)
                    .log();
            return;
        }

        Duration espera = politica.esperaTrasIntento(envio.numeroIntentos());
        boolean guardado = reclamo.registrarFallo(
                envio.idEnvio(), leaseHeld, EstadoEnvioCorreo.PENDIENTE, error, espera);
        log.atWarn()
                .setMessage("Transient email failure; retry scheduled")
                .addKeyValue("idEnvio", envio.idEnvio())
                .addKeyValue("intento", envio.numeroIntentos())
                .addKeyValue("proximoEn", espera)
                .addKeyValue("leaseConservado", guardado)
                .log();
    }

    private static String safe(String mensaje) {
        return mensaje == null ? "" : mensaje;
    }
}
