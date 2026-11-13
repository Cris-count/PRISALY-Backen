package com.prisaly.m3.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Docente creador de un caso: {@code "Caso"."idDocente"} (DC-CLAUDE-BE-CIERRE-07, RF-25).
 *
 * <p>Contrato explícito del módulo propietario de los casos para que otros módulos (M5: autorización de
 * repetición) verifiquen la autoría sin leer tablas de M3. Devuelve un dato persistido, no un permiso: la
 * decisión la toma el consumidor. No expone contenido académico.
 */
@FunctionalInterface
public interface AutoriaCasoPort {

    /**
     * @return el docente creador del caso, o vacío si el caso no existe
     * @throws AutoriaCasoNoDisponibleException si la consulta no puede completarse
     */
    Optional<UUID> docenteCreador(UUID idCaso);
}
