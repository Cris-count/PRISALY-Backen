package com.prisaly.m5.application.port;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.fase.FaseAcademica;
import com.prisaly.m5.domain.intento.EstadoIntento;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida de la fase académica del intento (DC-BE-SIM-B1-B2A-24, Incremento B, candidato), sobre la tabla
 * candidata {@code "FaseIntento"} (V018, {@code db/sim-propuesta-b}). Solo lee y escribe tablas propias de M5
 * ({@code "Intento"} y {@code "FaseIntento"}). Todas las operaciones exigen una transacción existente.
 */
public interface RepositorioFases {

    /** Fase persistida de un intento con fases: nunca {@link FaseAcademica#SIN_FASES}. */
    record Fase(UUID idGuion, FaseAcademica fase, Instant fechaInicioExploracion, Optional<Instant> fechaInicioCuestionario) {
        public Fase {
            Objects.requireNonNull(idGuion, "idGuion");
            Objects.requireNonNull(fase, "fase");
            Objects.requireNonNull(fechaInicioExploracion, "fechaInicioExploracion");
            Objects.requireNonNull(fechaInicioCuestionario, "fechaInicioCuestionario");
            if (fase == FaseAcademica.SIN_FASES || (fase == FaseAcademica.CUESTIONARIO) != fechaInicioCuestionario.isPresent()) {
                throw new IllegalArgumentException("Fase persistida incoherente: " + fase);
            }
        }
    }

    /**
     * Estado del intento y su fase, si la tiene. {@code ahora} es el {@code clock_timestamp()} de PostgreSQL de la
     * misma lectura, el reloj que ya usa M5 para decidir el vencimiento de las respuestas (A1).
     */
    record IntentoConFase(EstadoIntento estado, Instant limiteTemporal, Instant ahora, Optional<Fase> fase) {
        public IntentoConFase {
            Objects.requireNonNull(estado, "estado");
            Objects.requireNonNull(limiteTemporal, "limiteTemporal");
            Objects.requireNonNull(ahora, "ahora");
            Objects.requireNonNull(fase, "fase");
        }
    }

    /** Lectura en la instantánea de la transacción en curso; vacío si el intento no existe. */
    Optional<IntentoConFase> leer(ReferenciaIntento intento);

    /**
     * Igual que {@link #leer}, pero bloquea la fila {@code "Intento"} ({@code FOR UPDATE}) hasta el final de la
     * transacción. Así se serializan la entrada al cuestionario, las respuestas y la finalización del mismo intento.
     */
    Optional<IntentoConFase> bloquear(ReferenciaIntento intento);

    /** Crea la fase en {@code Exploracion} para el guion publicado indicado (la base valida caso y estado). */
    void crear(ReferenciaIntento intento, UUID idGuion);

    /**
     * Pasa de {@code Exploracion} a {@code Cuestionario} en una sola sentencia, con <b>un único</b> instante de PostgreSQL:
     * ese instante es a la vez la condición ({@code instante <= limiteTemporal}) y la fecha persistida, que se devuelve.
     * Vacío si en ese instante el límite ya había vencido: no se escribe nada y la fase sigue en exploración
     * (DC-BE-SIM-B2B-B3-CORRECCION-34, QA33-03). Requiere la fila {@code "Intento"} ya bloqueada por {@link #bloquear} en la
     * misma transacción.
     */
    Optional<Instant> marcarCuestionario(ReferenciaIntento intento);
}
