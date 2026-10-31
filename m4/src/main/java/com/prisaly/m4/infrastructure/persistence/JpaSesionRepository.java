package com.prisaly.m4.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository para sesiones académicas.
 */
public interface JpaSesionRepository extends JpaRepository<SesionAcademicaEntity, UUID> {

    List<SesionAcademicaEntity> findByDocenteId(UUID docenteId);

    @Query("SELECT COUNT(s) > 0 FROM SesionAcademicaEntity s " +
           "WHERE s.casoId = :casoId AND s.docenteId = :docenteId " +
           "AND s.fechaInicio = :fechaInicio AND s.fechaFin = :fechaFin")
    boolean existsDuplicada(@Param("casoId") UUID casoId,
                            @Param("docenteId") UUID docenteId,
                            @Param("fechaInicio") LocalDateTime fechaInicio,
                            @Param("fechaFin") LocalDateTime fechaFin);

    boolean existsByCasoId(UUID casoId);
}
