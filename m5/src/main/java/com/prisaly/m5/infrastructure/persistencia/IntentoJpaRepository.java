package com.prisaly.m5.infrastructure.persistencia;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso Spring Data a {@code "Intento"}; uso exclusivo del adaptador de M5. */
public interface IntentoJpaRepository extends JpaRepository<IntentoEntity, UUID> {

    /** {@code SELECT ... FOR UPDATE}: bloquea la fila hasta el fin de la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IntentoEntity i where i.idIntento = :id")
    Optional<IntentoEntity> bloquearPorId(@Param("id") UUID id);
}
