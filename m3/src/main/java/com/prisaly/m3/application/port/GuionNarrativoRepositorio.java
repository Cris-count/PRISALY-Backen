package com.prisaly.m3.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia del guion narrativo del caso (candidata V017, {@code db/sim-propuesta}). Todas las operaciones exigen
 * la transacción del caso de uso. La huella {@code hash} es SHA-256 de la forma canónica tipada del documento, estable
 * frente a espacios, orden de claves y reformateo de jsonb.
 */
public interface GuionNarrativoRepositorio {

    /** Estado del caso leído con {@code SELECT … FOR UPDATE}: serializa con la programación (bloqueo V007). */
    record CasoNarrativa(UUID idCaso, UUID idDocente, String estado, boolean activo, boolean bloqueado) {
        public CasoNarrativa {
            Objects.requireNonNull(idCaso);
            Objects.requireNonNull(idDocente);
            Objects.requireNonNull(estado);
        }
    }

    record GuionGuardado(UUID idGuion, UUID idCaso, int version, String estado, String definicionJson, String hash,
            UUID idDocenteAutor, Instant fechaActualizacion, Optional<Instant> fechaPublicacion) {
        public GuionGuardado {
            Objects.requireNonNull(idGuion);
            Objects.requireNonNull(idCaso);
            Objects.requireNonNull(estado);
            Objects.requireNonNull(definicionJson);
            Objects.requireNonNull(hash);
            Objects.requireNonNull(fechaPublicacion);
        }
    }

    Optional<CasoNarrativa> bloquearCaso(UUID idCaso);

    Optional<GuionGuardado> borrador(UUID idCaso);

    /** Versión publicada vigente del caso (vista docente). */
    Optional<GuionGuardado> publicadoVigente(UUID idCaso);

    /** Crea el borrador (versión siguiente) o reemplaza el existente. {@code documentoJson} ya está validado. */
    GuionGuardado guardarBorrador(UUID idCaso, UUID autor, String documentoJson);

    /** El publicado anterior pasa a {@code Archivado} y el borrador a {@code Publicado}, en la misma transacción. */
    GuionGuardado publicarBorrador(UUID idCaso);
}
