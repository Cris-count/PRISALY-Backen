package com.prisaly.m5.application.repeticion;

import com.prisaly.m3.api.AutoriaCasoNoDisponibleException;
import com.prisaly.m3.api.AutoriaCasoPort;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ResponsableConvocatoriaPort;
import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.RepeticionNoHabilitadaException;
import com.prisaly.m5.application.repeticion.AutorizacionRepeticionPort.AutorizacionRegistrada;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: un docente autoriza una repetición excepcional sobre una participación
 * (DC-CLAUDE-BE-EJECUCION-04, A4). Infraestructura técnica, <b>no habilitada</b>:
 *
 * <ol>
 *   <li>Con {@link RepeticionNoRatificada} (PF-08 pendiente) falla con
 *       {@link RepeticionNoHabilitadaException} antes de leer o escribir.</li>
 *   <li>Bloquea la participación, de modo que dos autorizaciones simultáneas se serializan.</li>
 *   <li>RF-25 (DC-CLAUDE-BE-CIERRE-07): solo autoriza el <b>docente creador del caso</b>. M5 obtiene el caso
 *       programado por {@link ResponsableConvocatoriaPort} (M4) y su autor por {@link AutoriaCasoPort} (M3), sin
 *       leer tablas de otros módulos. Programar la sesión no basta: un docente programador distinto del creador,
 *       o cualquier otro, recibe {@link AutorizacionRepeticionDenegadaException} sin escritura.</li>
 *   <li>Si ya hay una autorización vigente, no crea otra
 *       ({@link AutorizacionRepeticionVigenteException}).</li>
 *   <li>El consumo único no se reimplementa: lo aplica la base al insertar el intento repetido.</li>
 * </ol>
 * No decide cuántas repeticiones caben, ni plazos, ni qué intento cuenta para la calificación.
 * No es un bean ni tiene endpoint; requiere la transacción de escritura del invocador.
 */
public final class AutorizarRepeticionServicio {

    private final PoliticaRepeticion politica;
    private final AutorizacionRepeticionPort autorizaciones;
    private final ResponsableConvocatoriaPort responsables;
    private final AutoriaCasoPort autoria;

    public AutorizarRepeticionServicio(PoliticaRepeticion politica, AutorizacionRepeticionPort autorizaciones,
            ResponsableConvocatoriaPort responsables, AutoriaCasoPort autoria) {
        this.politica = Objects.requireNonNull(politica, "politica");
        this.autorizaciones = Objects.requireNonNull(autorizaciones, "autorizaciones");
        this.responsables = Objects.requireNonNull(responsables, "responsables");
        this.autoria = Objects.requireNonNull(autoria, "autoria");
    }

    /**
     * @param vigencia duración positiva hasta el vencimiento, o {@code null} si no vence
     */
    public AutorizacionRegistrada autorizar(UUID docente, UUID idParticipacion, String motivo, Duration vigencia) {
        if (!politica.autorizacionHabilitada()) {
            throw new RepeticionNoHabilitadaException();
        }
        if (docente == null || idParticipacion == null) {
            throw new DatoAplicacionInvalidoException("El docente y la participación son obligatorios");
        }
        if (vigencia != null && (vigencia.isZero() || vigencia.isNegative())) {
            throw new DatoAplicacionInvalidoException("La vigencia debe ser positiva");
        }
        UUID convocatoria = autorizaciones.bloquearParticipacion(idParticipacion)
                .orElseThrow(ParticipacionNoEncontradaException::new);
        UUID caso = responsables.consultar(convocatoria)
                .orElseThrow(ConsultaProgramacionNoDisponibleException::new)
                .idCaso();
        // Falla cerrado: sin autor verificable no se autoriza.
        UUID creador = autoria.docenteCreador(caso).orElseThrow(AutoriaCasoNoDisponibleException::new);
        if (!creador.equals(docente)) {
            throw new AutorizacionRepeticionDenegadaException();
        }
        if (autorizaciones.existeVigente(idParticipacion)) {
            throw new AutorizacionRepeticionVigenteException();
        }
        return autorizaciones.registrar(idParticipacion, docente, motivo, vigencia);
    }
}
