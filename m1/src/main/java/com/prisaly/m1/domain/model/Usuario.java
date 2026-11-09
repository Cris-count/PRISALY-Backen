package com.prisaly.m1.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cuenta institucional. Sin toString para no exponer el hash de contraseña. */
public final class Usuario {
    private static final String ESTADO_ACTIVO = "Activo";

    private final UUID idUsuario;
    private final Rol rol;
    private final String nombre;
    private final String correo;
    private final String passwordHash;
    private final String estado;
    private final OffsetDateTime fechaCreacion;
    private final OffsetDateTime fechaActualizacion;

    public Usuario(UUID idUsuario, Rol rol, String nombre, String correo, String passwordHash, String estado,
            OffsetDateTime fechaCreacion, OffsetDateTime fechaActualizacion) {
        this.idUsuario = idUsuario;
        this.rol = rol;
        this.nombre = nombre;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaActualizacion;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public Rol getRol() {
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
        return ESTADO_ACTIVO.equalsIgnoreCase(estado);
    }
}
