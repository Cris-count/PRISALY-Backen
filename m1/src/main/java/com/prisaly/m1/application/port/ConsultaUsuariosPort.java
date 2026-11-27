package com.prisaly.m1.application.port;
import java.util.List;
import java.util.UUID;
/** Proyección administrativa mínima; jamás contiene hashes o tokens. */
public interface ConsultaUsuariosPort {
    record UsuarioResumen(UUID idUsuario, UUID idEstudiante, String nombre, String correo, String rol,
            String estado, boolean credencialPersonalConfigurada, String estadoInvitacion) {}
    List<UsuarioResumen> buscar(String texto, String rol, int limite, int offset);
}
