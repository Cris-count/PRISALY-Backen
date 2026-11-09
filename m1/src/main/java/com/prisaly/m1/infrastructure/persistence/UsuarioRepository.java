package com.prisaly.m1.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {

    Optional<UsuarioEntity> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM \"Estudiante\" e WHERE e.\"idUsuario\" = :id)", nativeQuery = true)
    boolean esCuentaEstudiante(@Param("id") UUID id);
}
