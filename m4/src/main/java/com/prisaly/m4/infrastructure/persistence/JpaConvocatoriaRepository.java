package com.prisaly.m4.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository para convocatorias.
 */
public interface JpaConvocatoriaRepository extends JpaRepository<ConvocatoriaEntity, UUID> {

    List<ConvocatoriaEntity> findBySesionId(UUID sesionId);

    boolean existsBySesionIdAndEstudianteId(UUID sesionId, UUID estudianteId);
}
