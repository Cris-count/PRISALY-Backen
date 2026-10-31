package com.prisaly.m4.infrastructure.persistence;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface JpaSesionRepository extends JpaRepository<SesionAcademicaEntity, UUID> {
    List<SesionAcademicaEntity> findByDocenteId(UUID docenteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionAcademicaEntity s where s.id=:id")
    Optional<SesionAcademicaEntity> bloquearPorId(@Param("id") UUID id);

    @Query(
            "select count(s)>0 from SesionAcademicaEntity s where s.casoId=:caso and"
                + " s.docenteId=:docente and s.fechaInicio=:inicio and s.fechaFin=:fin")
    boolean existsDuplicada(
            @Param("caso") UUID caso,
            @Param("docente") UUID docente,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin);

    @Query(
            "select count(s)>0 from SesionAcademicaEntity s where s.id<>:id and s.casoId=:caso and"
                + " s.docenteId=:docente and s.fechaInicio=:inicio and s.fechaFin=:fin")
    boolean existeDuplicadaExcluyendo(
            @Param("id") UUID id,
            @Param("caso") UUID caso,
            @Param("docente") UUID docente,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin);

    boolean existsByCasoId(UUID caso);
}
