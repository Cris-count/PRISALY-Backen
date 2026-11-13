package com.prisaly.m4.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Programación persistida de una convocatoria: su sesión, el caso programado y el docente que programó la sesión.
 *
 * <p>DC-CLAUDE-BE-EJECUCION-04 (A4) y DC-CLAUDE-BE-CIERRE-07 (RF-25): permite que M5 llegue al caso de una
 * participación sin leer tablas de M4, para verificar después la autoría con el contrato de M3
 * ({@code AutoriaCasoPort}). Son datos persistidos, no permisos: la decisión la toma el consumidor.
 */
@FunctionalInterface
public interface ResponsableConvocatoriaPort {

    /**
     * @return la programación de la convocatoria, o vacío si no existe
     * @throws ConsultaProgramacionNoDisponibleException si la consulta no puede completarse
     */
    Optional<ProgramacionConvocatoria> consultar(UUID convocatoriaId);

    /** {@code "Sesion"."idSesion"}, {@code "Sesion"."idCaso"} y {@code "Sesion"."idDocenteProgramador"}. */
    record ProgramacionConvocatoria(UUID idSesion, UUID idCaso, UUID idDocenteProgramador) {
        public ProgramacionConvocatoria {
            Objects.requireNonNull(idSesion, "idSesion");
            Objects.requireNonNull(idCaso, "idCaso");
            Objects.requireNonNull(idDocenteProgramador, "idDocenteProgramador");
        }
    }
}
