package com.prisaly.m5.application.repeticion;

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
 *   <li>Solo autoriza el docente que programó la sesión de la convocatoria (dato de M4 por
 *       {@link ResponsableConvocatoriaPort}); cualquier otro recibe
 *       {@link AutorizacionRepeticionDenegadaException} sin escritura.</li>
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

    public AutorizarRepeticionServicio(PoliticaRepeticion politica, AutorizacionRepeticionPort autorizaciones,
            ResponsableConvocatoriaPort responsables) {
        this.politica = Objects.requireNonNull(politica, "politica");
        this.autorizaciones = Objects.requireNonNull(autorizaciones, "autorizaciones");
        this.responsables = Objects.requireNonNull(responsables, "responsables");
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
        UUID creador = responsables.docenteProgramador(convocatoria)
                .orElseThrow(ConsultaProgramacionNoDisponibleException::new);
        if (!creador.equals(docente)) {
            throw new AutorizacionRepeticionDenegadaException();
        }
        if (autorizaciones.existeVigente(idParticipacion)) {
            throw new AutorizacionRepeticionVigenteException();
        }
        return autorizaciones.registrar(idParticipacion, docente, motivo, vigencia);
    }
}
