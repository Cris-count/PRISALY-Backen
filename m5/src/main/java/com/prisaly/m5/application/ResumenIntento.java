package com.prisaly.m5.application;

import java.util.List;
import java.util.Optional;

import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.Respuesta;

/**
 * Vista inmutable de un intento que la capa de aplicación entrega a sus
 * consumidores, para que el agregado mutable no salga de los casos de uso.
 *
 * <p>No es un DTO REST: el contrato público de M5 sigue pendiente. No incluye
 * calificación, rúbrica ni retroalimentación (M6).
 */
public record ResumenIntento(
        ReferenciaIntento referencia,
        EstadoIntento estado,
        Optional<MotivoFinalizacion> motivoFinalizacion,
        List<Respuesta> respuestas) {

    public ResumenIntento {
        if (referencia == null || estado == null || motivoFinalizacion == null || respuestas == null) {
            throw new DatoAplicacionInvalidoException("El resumen del intento requiere todos sus datos");
        }
        respuestas = List.copyOf(respuestas);
    }

    /** Toma una instantánea del estado actual del intento. */
    public static ResumenIntento de(ReferenciaIntento referencia, Intento intento) {
        if (intento == null) {
            throw new DatoAplicacionInvalidoException("El intento es obligatorio");
        }
        return new ResumenIntento(referencia, intento.estado(), intento.motivoFinalizacion(), intento.respuestas());
    }
}
