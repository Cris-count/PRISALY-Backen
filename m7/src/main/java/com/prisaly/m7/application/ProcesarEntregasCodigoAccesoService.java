package com.prisaly.m7.application;

import com.prisaly.m1.application.port.RevocacionCodigoAccesoPort;
import com.prisaly.m7.application.CifradorCodigoAcceso.Contenido;
import com.prisaly.m7.application.EntregaCodigoAccesoRepositorio.EntregaReclamada;
import com.prisaly.m7.application.EntregaCodigoAccesoRepositorio.EstadoFinal;
import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * D-B2.5 (DC-DD02-B2-02) access-code delivery, independent from the convocation worker and its 1/2/4/8-minute policy
 * (useless for a 5-minute code):
 * <ul>
 *   <li>attempts at creation, +30 s and +90 s; never sent when less than 60 s of validity remain;</li>
 *   <li>a transient (or configuration) failure reschedules within validity; the third failure exhausts the delivery;</li>
 *   <li>a permanent recipient failure, an exhausted delivery or an undecryptable payload revoke the code in M1;</li>
 *   <li>every final state erases the encrypted payload.</li>
 * </ul>
 * The code never appears in logs; only ids, states and SMTP classes are recorded by the caller.
 */
public final class ProcesarEntregasCodigoAccesoService {

    public static final String ASUNTO = "PRISALY — Código de acceso";
    public static final Duration TIEMPO_MINIMO_RESTANTE = Duration.ofSeconds(60);
    static final List<Duration> PLAN = List.of(Duration.ZERO, Duration.ofSeconds(30), Duration.ofSeconds(90));

    private final EntregaCodigoAccesoRepositorio repositorio;
    private final CifradorCodigoAcceso cifrador;
    private final ProveedorCorreoPort proveedor;
    private final RevocacionCodigoAccesoPort revocacion;
    private final String remitente;
    private final int lote;
    private final Clock reloj;

    public ProcesarEntregasCodigoAccesoService(EntregaCodigoAccesoRepositorio repositorio, CifradorCodigoAcceso cifrador,
            ProveedorCorreoPort proveedor, RevocacionCodigoAccesoPort revocacion, String remitente, int lote, Clock reloj) {
        if (remitente == null || remitente.isBlank() || lote < 1) {
            throw new IllegalArgumentException("Remitente y lote son obligatorios");
        }
        this.repositorio = repositorio;
        this.cifrador = cifrador;
        this.proveedor = proveedor;
        this.revocacion = revocacion;
        this.remitente = remitente;
        this.lote = lote;
        this.reloj = reloj;
    }

    /** One worker cycle; returns how many deliveries were accepted by SMTP. */
    public int procesar() {
        Instant ahora = reloj.instant();
        repositorio.cancelarSinTiempo(ahora.plus(TIEMPO_MINIMO_RESTANTE), ahora);
        int enviadas = 0;
        for (EntregaReclamada e : repositorio.reclamar(lote, Duration.ofSeconds(30), ahora)) {
            if (Duration.between(ahora, e.expira()).compareTo(TIEMPO_MINIMO_RESTANTE) < 0) {
                repositorio.finalizarSinEnvio(e.idEntrega(), EstadoFinal.CANCELADA_VENCIDA, "SIN_TIEMPO", ahora);
                continue;
            }
            Optional<Contenido> contenido = cifrador.descifrar(e.challengeId(), e.carga(), e.nonce(), e.kid());
            if (contenido.isEmpty()) {
                cerrarConRevocacion(e, EstadoFinal.FALLIDA_PERMANENTE, "DESCIFRADO", ahora);
                continue;
            }
            ResultadoEnvioSmtp resultado = proveedor.enviar(remitente, contenido.get().destinatario(), ASUNTO,
                    cuerpo(contenido.get().codigo()));
            if (resultado instanceof ResultadoEnvioSmtp.Aceptado) {
                repositorio.marcarEnviada(e.idEntrega(), ahora);
                enviadas++;
            } else if (resultado instanceof ResultadoEnvioSmtp.Rechazado r) {
                rechazo(e, r, ahora);
            }
        }
        return enviadas;
    }

    private void rechazo(EntregaReclamada e, ResultadoEnvioSmtp.Rechazado r, Instant ahora) {
        String codigoError = "SMTP_" + (r.codigo() == null ? "SMTP" : r.codigo());
        if (r.clase() == ClaseErrorCorreo.PERMANENTE_DESTINATARIO) {
            cerrarConRevocacion(e, EstadoFinal.FALLIDA_PERMANENTE, codigoError, ahora);
            return;
        }
        int intentos = e.intentos() + 1;
        if (intentos >= PLAN.size()) {
            cerrarConRevocacion(e, EstadoFinal.AGOTADA, codigoError, ahora);
            return;
        }
        Instant proximo = e.creada().plus(PLAN.get(intentos));
        if (proximo.isBefore(ahora)) {
            proximo = ahora;
        }
        if (Duration.between(proximo, e.expira()).compareTo(TIEMPO_MINIMO_RESTANTE) < 0) {
            cerrarConRevocacion(e, EstadoFinal.CANCELADA_VENCIDA, codigoError, ahora);
            return;
        }
        repositorio.reprogramar(e.idEntrega(), intentos, proximo, codigoError);
    }

    private void cerrarConRevocacion(EntregaReclamada e, EstadoFinal estado, String codigoError, Instant ahora) {
        repositorio.finalizarSinEnvio(e.idEntrega(), estado, codigoError, ahora);
        revocacion.revocarPorFalloDeEntrega(e.challengeId());
    }

    /** D-B4 §3: the code only in the body; no link, no challenge id, no convocation data. */
    static String cuerpo(String codigo) {
        return "Tu código de acceso a PRISALY es: " + codigo + "\n\n"
                + "Es válido durante 5 minutos y solo puede usarse una vez.\n"
                + "Si no lo solicitaste, puedes ignorar este mensaje.";
    }
}
