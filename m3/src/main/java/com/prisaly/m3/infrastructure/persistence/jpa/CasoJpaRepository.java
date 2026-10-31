package com.prisaly.m3.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Deliberately exposes no save, delete or bulk mutation operations. */
@Transactional(readOnly = true)
public interface CasoJpaRepository extends Repository<CasoJpaEntity, UUID> {

    Optional<CasoJpaEntity> findById(UUID idCaso);
}
