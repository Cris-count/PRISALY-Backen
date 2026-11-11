package com.prisaly.m1.application.port;

import com.prisaly.m1.domain.model.Permiso;
import java.util.List;
import java.util.Optional;

/** Persistencia del catálogo de permisos. */
public interface PermisoRepositorioPort {
    Optional<Permiso> buscarPorId(Integer idPermiso);

    Optional<Permiso> buscarPorCodigo(String codigo);

    boolean existePorId(Integer idPermiso);

    Permiso registrar(String codigo, String nombre, String descripcion);

    /** DM1-04: catálogo completo, ordenado por identificador. */
    List<Permiso> listarPermisos();
}
