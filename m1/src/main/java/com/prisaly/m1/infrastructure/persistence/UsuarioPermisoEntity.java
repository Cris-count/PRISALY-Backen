package com.prisaly.m1.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "UsuarioPermiso")
@IdClass(UsuarioPermisoId.class)
public class UsuarioPermisoEntity {

    @Id
    @Column(name = "idUsuario", nullable = false)
    private UUID idUsuario;

    @Id
    @Column(name = "idPermiso", nullable = false)
    private Integer idPermiso;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "idPermiso", insertable = false, updatable = false)
    private PermisoEntity permiso;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado;

    @Column(name = "idAdministradorAsignador")
    private UUID idAdministradorAsignador;

    @Column(name = "fechaAsignacion", nullable = false)
    private OffsetDateTime fechaAsignacion;

    protected UsuarioPermisoEntity() {
    }

    public UsuarioPermisoEntity(
            UUID idUsuario,
            Integer idPermiso,
            boolean habilitado,
            UUID idAdministradorAsignador,
            OffsetDateTime fechaAsignacion) {
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

    public PermisoEntity getPermiso() {
        return permiso;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public UUID getIdAdministradorAsignador() {
        return idAdministradorAsignador;
    }

    public OffsetDateTime getFechaAsignacion() {
        return fechaAsignacion;
    }
}
