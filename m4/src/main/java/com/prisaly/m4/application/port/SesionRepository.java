package com.prisaly.m4.application.port;

import com.prisaly.m4.domain.model.SesionAcademica;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia para sesiones académicas. */
public interface SesionRepository {

    default Optional<SesionAcademica> buscarParaActualizar(UUID id) {
        throw noDisponible();
    }

    default void protegerProgramacion(UUID caso, UUID docente) {
        throw noDisponible();
    }

    default boolean existeDuplicadaExcluyendo(
            UUID id, UUID caso, UUID docente, java.time.Instant inicio, java.time.Instant fin) {
        throw noDisponible();
    }

    private static RuntimeException noDisponible() {
        return new com.prisaly.m4.domain.rule.ReglaNegocioException(
                "M4-PROVEEDOR-NO-DISPONIBLE",
                "El repositorio no implementa el protocolo de bloqueo requerido");
    }

    SesionAcademica guardar(SesionAcademica sesion);

    Optional<SesionAcademica> buscarPorId(UUID id);

    List<SesionAcademica> listarPorDocente(UUID docenteId);

    List<SesionAcademica> listarTodas();

    /**
     * Verifica si ya existe una sesión para el mismo caso, docente y rango de fechas para evitar
     * duplicaciones.
     */
    boolean existeDuplicada(
            UUID casoId, UUID docenteId, java.time.Instant fechaInicio, java.time.Instant fechaFin);

    /**
     * Verifica si existe al menos una sesión programada para este caso. Se usa para determinar si
     * se debe solicitar el bloqueo F3-03.
     */
    boolean existeSesionParaCaso(UUID casoId);

    /**
     * Verifica si la sesión tiene intentos/participaciones vinculadas. Se usa para proteger la
     * integridad histórica.
     */
    boolean tieneParticipacionesVinculadas(UUID sesionId);
}
