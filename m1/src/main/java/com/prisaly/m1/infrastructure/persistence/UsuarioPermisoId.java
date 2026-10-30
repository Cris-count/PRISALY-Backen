package com.prisaly.m1.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class UsuarioPermisoId implements Serializable {

    private UUID idUsuario;
    private Integer idPermiso;

    public UsuarioPermisoId() {
    }

    public UsuarioPermisoId(UUID idUsuario, Integer idPermiso) {
        this.idUsuario = idUsuario;
        this.idPermiso = idPermiso;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public Integer getIdPermiso() {
        return idPermiso;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UsuarioPermisoId that)) {
            return false;
        }
        return Objects.equals(idUsuario, that.idUsuario) && Objects.equals(idPermiso, that.idPermiso);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUsuario, idPermiso);
    }
}
