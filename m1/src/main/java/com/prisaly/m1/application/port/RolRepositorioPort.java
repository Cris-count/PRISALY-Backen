package com.prisaly.m1.application.port;

import com.prisaly.m1.domain.model.Rol;
import java.util.Optional;

/** Persistencia del catálogo de roles. */
public interface RolRepositorioPort {
    Optional<Rol> buscarPorNombre(String nombre);

    Rol registrar(String nombre, String descripcion);
}
