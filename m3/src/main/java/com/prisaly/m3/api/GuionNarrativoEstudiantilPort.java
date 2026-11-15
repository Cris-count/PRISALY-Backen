package com.prisaly.m3.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de M3 hacia M5 (DC-BE-SIM-INCREMENTO-A-15, para el Incremento B): lectura del guion narrativo
 * <b>publicado</b>. Nunca devuelve borradores. El documento no contiene datos académicos (validado al guardarse).
 *
 * <p>Exige una transacción existente ({@code MANDATORY}): M5 la abre y comparte su snapshot con la titularidad, como
 * {@link ContenidoCasoEstudiantilPort}. La integridad se verifica en cada lectura: si la huella persistida no coincide
 * con el documento, falla cerrado con {@link GuionNarrativoNoDisponibleException}. Registrado solo con
 * {@code prisaly.m3.narrativa.publicacion.enabled=true}.
 */
public interface GuionNarrativoEstudiantilPort {

    /** Versión concreta (la que referencia un intento); vacío si no existe o es un borrador. */
    Optional<GuionPublicado> publicado(UUID idGuion);

    /** Versión publicada vigente del caso; vacío si el caso no tiene guion publicado. */
    Optional<GuionPublicado> publicadoDelCaso(UUID idCaso);

    record GuionPublicado(UUID idGuion, UUID idCaso, int version, String hashDefinicion, String definicionJson) {
        public GuionPublicado {
            Objects.requireNonNull(idGuion, "idGuion");
            Objects.requireNonNull(idCaso, "idCaso");
            Objects.requireNonNull(hashDefinicion, "hashDefinicion");
            Objects.requireNonNull(definicionJson, "definicionJson");
            if (version < 1) { throw new IllegalArgumentException("version"); }
        }
    }
}
