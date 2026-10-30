package com.prisaly.m1.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Usuario")
public class UsuarioEntity {

    @Id
    @Column(name = "idUsuario", nullable = false)
    private UUID idUsuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "idRol", nullable = false)
    private RolEntity rol;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "correo", nullable = false, unique = true, length = 255)
    private String correo;

    @Column(name = "passwordHash", length = 255)
    private String passwordHash;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "fechaCreacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fechaActualizacion")
    private OffsetDateTime fechaActualizacion;

    protected UsuarioEntity() {
    }

    public UsuarioEntity(
            UUID idUsuario,
            RolEntity rol,
            String nombre,
            String correo,
            String passwordHash,
            String estado,
            OffsetDateTime fechaCreacion) {
        this.idUsuario = idUsuario;
        this.rol = rol;
        this.nombre = nombre;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public RolEntity getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEstado() {
        return estado;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public boolean isActive() {
        return "Activo".equalsIgnoreCase(estado);
    }
}
