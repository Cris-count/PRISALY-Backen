package com.prisaly.m5.application.port;

import java.time.Instant;
import java.util.Optional;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Puerto de salida para el inicio y la reanudación: {@code "Participacion"} y
 * la creación de {@code "Intento"}. M5 es el único escritor de
 * {@code "Participacion"}.
 *
 * <p>Todas las operaciones exigen la transacción del caso de uso. Protocolo de
 * concurrencia (V003/V005): bloquear la fila de {@code "Participacion"} antes
 * de leer sus intentos y antes de insertar uno. Así dos solicitudes
 * simultáneas sobre la misma convocatoria se serializan y la segunda ve lo
 * que confirmó la primera.
 */
public interface RepositorioParticipaciones {

    /** Referencia a {@code "Participacion"."idParticipacion"}. */
    record ReferenciaParticipacion(String valor) {

        public ReferenciaParticipacion {
            if (valor == null || valor.isBlank()) {
                throw new DatoAplicacionInvalidoException("La referencia de la participación es obligatoria");
            }
        }
    }

    /** Intento {@code EnCurso} con sus tiempos persistidos. */
    record IntentoEnCurso(ReferenciaIntento referencia, Instant inicio, Instant limiteTemporal) {

        public IntentoEnCurso {
            if (referencia == null || inicio == null || limiteTemporal == null) {
                throw new DatoAplicacionInvalidoException("El intento en curso requiere su referencia y sus tiempos");
            }
        }
    }

    /** Participación de la convocatoria, bloqueada hasta el fin de la transacción; vacío si no existe. */
    Optional<ReferenciaParticipacion> bloquear(ReferenciaConvocatoria convocatoria);

    /**
     * Crea la participación de la convocatoria si no existe y la bloquea. Si
     * otra transacción la crea a la vez, espera a que termine y bloquea la
     * suya ({@code UNIQUE ("idConvocatoria")}): nunca hay dos.
     */
    ReferenciaParticipacion crearOBloquear(ReferenciaConvocatoria convocatoria);

    /** El intento {@code EnCurso} de la participación; como mucho hay uno ({@code ux_intento_unico_encurso}). */
    Optional<IntentoEnCurso> intentoEnCurso(ReferenciaParticipacion participacion);

    /** Si la participación tiene algún intento, en cualquier estado. */
    boolean tieneIntentos(ReferenciaParticipacion participacion);

    /** Hora del servidor PostgreSQL ({@code clock_timestamp()}) en la transacción en curso. */
    Instant ahora();

    /**
     * Inserta el primer intento ({@code numeroIntento = 1}, {@code EnCurso}, sin
     * autorización de repetición).
     *
     * @throws com.prisaly.m5.application.RepeticionNoHabilitadaException si V005
     *     exige una autorización de repetición (intento previo sobre el mismo caso)
     */
    ReferenciaIntento crearPrimerIntento(ReferenciaParticipacion participacion, Instant inicio, Instant limiteTemporal);
}
