package com.prisaly.m1.application.port;

import com.prisaly.m1.domain.model.Usuario;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de cuentas requerida por los casos de uso de M1. */
public interface UsuarioRepositorioPort {
    Optional<Usuario> buscarPorId(UUID idUsuario);

    Optional<Usuario> buscarPorCorreo(String correo);

    boolean existePorId(UUID idUsuario);

    boolean existePorCorreo(String correo);

    Usuario registrar(Usuario usuario);

    void cambiarEstado(UUID idUsuario, String estado, OffsetDateTime fecha);

    boolean esCuentaEstudiante(UUID idUsuario);
}
