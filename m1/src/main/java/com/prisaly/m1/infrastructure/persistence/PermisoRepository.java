package com.prisaly.m1.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermisoRepository extends JpaRepository<PermisoEntity, Integer> {

    Optional<PermisoEntity> findByCodigo(String codigo);
}
