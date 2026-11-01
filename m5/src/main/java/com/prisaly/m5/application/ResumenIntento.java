package com.prisaly.m5.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
 *
 * <p><b>Orden de las respuestas:</b> siempre por la referencia de la pregunta
 * ({@link #ORDEN_RESPUESTAS}), el mismo orden en que las devuelve la
 * persistencia ({@code ORDER BY "idPregunta"}). Así una consulta devuelve lo
 * mismo con el agregado recién modificado o recién cargado de la base. BD-005.2
 * no guarda el orden en que se respondió, y el orden pedagógico del caso
 * (escenario y pregunta) requiere un contrato de M3: no es un orden académico.
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
        if (respuestas.stream().anyMatch(Objects::isNull)) {
            throw new DatoAplicacionInvalidoException("El resumen del intento no admite respuestas nulas");
        }
        respuestas = respuestas.stream().sorted(ORDEN_RESPUESTAS).toList();
    }

    /**
     * Orden determinista por pregunta. Las referencias uuid se comparan en su
     * forma canónica en minúsculas, que coincide con el orden de {@code uuid}
     * en PostgreSQL (bytes sin signo); {@link UUID#compareTo} no sirve porque
     * compara con signo.
     */
    public static final Comparator<Respuesta> ORDEN_RESPUESTAS =
            Comparator.comparing(respuesta -> clave(respuesta.pregunta().valor()));

    private static String clave(String referencia) {
        try {
            return UUID.fromString(referencia).toString();
        } catch (IllegalArgumentException noEsUuid) {
            return referencia;
        }
    }

    /** Toma una instantánea del estado actual del intento. */
    public static ResumenIntento de(ReferenciaIntento referencia, Intento intento) {
        if (intento == null) {
            throw new DatoAplicacionInvalidoException("El intento es obligatorio");
        }
        return new ResumenIntento(referencia, intento.estado(), intento.motivoFinalizacion(), intento.respuestas());
    }
}
