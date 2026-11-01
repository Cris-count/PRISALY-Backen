package com.prisaly.m6.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Intentionally excludes save, delete, flush and bulk mutations. */
@Transactional(readOnly = true)
public interface CalificacionJpaRepository extends Repository<CalificacionJpaEntity, UUID> {
    Optional<CalificacionJpaEntity> findById(UUID idCalificacion);
    Optional<CalificacionJpaEntity> findByIdIntento(UUID idIntento);
}
