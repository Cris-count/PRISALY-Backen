package com.prisaly.m1.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioPermisoRepository extends JpaRepository<UsuarioPermisoEntity, UsuarioPermisoId> {

    @Query("""
            select up from UsuarioPermisoEntity up
            join fetch up.permiso
            where up.idUsuario = :idUsuario and up.habilitado = true
            """)
    List<UsuarioPermisoEntity> findByIdUsuarioAndHabilitadoTrue(@Param("idUsuario") UUID idUsuario);
}
