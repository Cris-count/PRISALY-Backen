package com.prisaly.m1.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al catálogo de permisos. El alta y la consulta de administración lo usan. */
public interface PermisoRepository extends JpaRepository<PermisoEntity, Integer> {

    Optional<PermisoEntity> findByCodigo(String codigo);
}
