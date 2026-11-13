package com.prisaly.m4.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Docente que programó (creó) la sesión de una convocatoria: {@code "Sesion"."idDocenteProgramador"}.
 *
 * <p>DC-CLAUDE-BE-EJECUCION-04, A4: permite que M5 verifique quién puede autorizar una repetición sin
 * leer tablas de M4. Es un dato persistido, no un permiso: la decisión la toma el consumidor.
 */
@FunctionalInterface
public interface ResponsableConvocatoriaPort {

    /**
     * @return el docente programador de la sesión, o vacío si la convocatoria no existe
     * @throws ConsultaProgramacionNoDisponibleException si la consulta no puede completarse
     */
    Optional<UUID> docenteProgramador(UUID convocatoriaId);
}
