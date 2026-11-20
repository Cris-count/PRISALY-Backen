package com.prisaly.m5.application.port;

import java.util.Optional;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.Intento;

/**
 * Puerto de salida para cargar y guardar intentos.
 *
 * <p>Las respuestas pertenecen al agregado {@link Intento}: se cargan y se
 * guardan con él, por lo que no se define un puerto separado para ellas.
 *
 * <p>Requisitos para el futuro adaptador PostgreSQL (pendientes de BD-005.2):
 * <ul>
 *   <li>{@link #guardar} debe persistir de forma atómica el estado, el motivo
 *       de finalización y todas las respuestas del intento, dentro de la
 *       transacción del caso de uso que lo invoca.</li>
 *   <li>Debe impedir la pérdida de actualizaciones entre solicitudes
 *       concurrentes sobre el mismo intento (por ejemplo, bloqueo pesimista al
 *       cargar o control optimista por versión). La estrategia se decidirá con
 *       el modelo físico; hoy {@code "Intento"} no tiene columna de versión.</li>
 *   <li>No debe convertir en éxito un intento inexistente.</li>
 * </ul>
 * No existe todavía ninguna implementación de producción.
 */
public interface RepositorioIntentos {

    /** Own-table relation only; academic case is resolved through M4, never by cross-module SQL. */
    record VinculoParticipacion(java.util.UUID idParticipacion, java.util.UUID idConvocatoria) {
        public VinculoParticipacion {
            java.util.Objects.requireNonNull(idParticipacion);
            java.util.Objects.requireNonNull(idConvocatoria);
        }
    }

    /** Existing implementations without this read fail closed. Requires the caller's transaction. */
    default Optional<VinculoParticipacion> vinculo(ReferenciaIntento referencia) {
        throw new UnsupportedOperationException("Lectura de participacion no disponible");
    }

    /** Busca un intento por su referencia; vacío si no existe. */
    Optional<Intento> buscar(ReferenciaIntento referencia);

    default ResumenIntento resumir(ReferenciaIntento referencia, Intento intento) {
        return ResumenIntento.de(referencia, intento);
    }

    /** Persiste el estado actual del intento identificado por la referencia. */
    void guardar(ReferenciaIntento referencia, Intento intento);

    /**
     * V1-c (DC-BE-M5-CIERRE-IMPLEMENTACION-45): instante del servidor PostgreSQL leído con la fila del intento ya
     * bloqueada, junto con su límite persistido. El vencimiento se decide con este instante y el cierre se graba con
     * él mismo ({@link #guardar(ReferenciaIntento, Intento, java.time.Instant)}), así que un cierre persistido como
     * entrega nunca es posterior al límite. Implementaciones sin esta lectura: no disponible (falla cerrado).
     */
    default InstanteDeCierre instanteDeCierre(ReferenciaIntento referencia) {
        throw new UnsupportedOperationException("Lectura del reloj de cierre no disponible");
    }

    /** Persiste como {@link #guardar(ReferenciaIntento, Intento)}, usando {@code instante} para las marcas. */
    default void guardar(ReferenciaIntento referencia, Intento intento, java.time.Instant instante) {
        throw new UnsupportedOperationException("Guardado con instante de cierre no disponible");
    }

    /** Instante de PostgreSQL y límite persistido del intento bloqueado. */
    record InstanteDeCierre(java.time.Instant ahora, java.time.Instant limiteTemporal) {
        public InstanteDeCierre {
            java.util.Objects.requireNonNull(ahora, "ahora");
            java.util.Objects.requireNonNull(limiteTemporal, "limiteTemporal");
        }

        /** Mismo criterio que la regla A1 de las respuestas: vencido si el instante es posterior al límite. */
        public boolean vencido() {
            return ahora.isAfter(limiteTemporal);
        }
    }
}
