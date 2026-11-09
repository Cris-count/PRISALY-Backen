package com.prisaly.m1.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Permiso asignado individualmente a una cuenta. */
public final class AsignacionPermiso {
    private final UUID idUsuario;
    private final Integer idPermiso;
    private final boolean habilitado;
    private final UUID idAdministradorAsignador;
    private final OffsetDateTime fechaAsignacion;

    public AsignacionPermiso(UUID idUsuario, Integer idPermiso, boolean habilitado,
            UUID idAdministradorAsignador, OffsetDateTime fechaAsignacion) {
        this.idUsuario = idUsuario;
        this.idPermiso = idPermiso;
        this.habilitado = habilitado;
        this.idAdministradorAsignador = idAdministradorAsignador;
        this.fechaAsignacion = fechaAsignacion;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public Integer getIdPermiso() {
        return idPermiso;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public UUID getIdAdministradorAsignador() {
        return idAdministradorAsignador;
    }

    public OffsetDateTime getFechaAsignacion() {
        return fechaAsignacion;
    }
}
