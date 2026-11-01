package com.prisaly.m6.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Query-only API; no save, delete, flush or bulk mutations. */
@Transactional(readOnly = true)
public interface RetroalimentacionGeneralJpaRepository extends Repository<RetroalimentacionGeneralJpaEntity, UUID> {
    Optional<RetroalimentacionGeneralJpaEntity> findById(UUID idRetroalimentacion);
    Optional<RetroalimentacionGeneralJpaEntity> findByIdParticipacion(UUID idParticipacion);
}
