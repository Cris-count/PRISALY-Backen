package com.prisaly.m4.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * Puerto de persistencia para sesiones académicas.
 */
public interface SesionRepository {

    SesionAcademica guardar(SesionAcademica sesion);

    Optional<SesionAcademica> buscarPorId(UUID id);

    List<SesionAcademica> listarPorDocente(UUID docenteId);

    List<SesionAcademica> listarTodas();

    /**
     * Verifica si ya existe una sesión para el mismo caso, docente
     * y rango de fechas para evitar duplicaciones.
     */
    boolean existeDuplicada(UUID casoId, UUID docenteId,
                            java.time.LocalDateTime fechaInicio,
                            java.time.LocalDateTime fechaFin);

    /**
     * Verifica si existe al menos una sesión programada para este caso.
     * Se usa para determinar si se debe solicitar el bloqueo F3-03.
     */
    boolean existeSesionParaCaso(UUID casoId);

    /**
     * Verifica si la sesión tiene intentos/participaciones vinculadas.
     * Se usa para proteger la integridad histórica.
     */
    boolean tieneParticipacionesVinculadas(UUID sesionId);
}
