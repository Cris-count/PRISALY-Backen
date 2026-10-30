package com.prisaly.m4.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.prisaly.m4.domain.model.EstadoSesion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidad JPA para la tabla de sesiones académicas.
 *
 * <p>PROPUESTO — el nombre de tabla {@code sesiones_academicas} se propone
 * ante la ausencia de BD-005.2. Si la especificación aprobada difiere,
 * debe ajustarse la migración y esta entidad.</p>
 */
@Entity
@Table(name = "sesiones_academicas")
public class SesionAcademicaEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "caso_id", nullable = false)
    private UUID casoId;

    @Column(name = "docente_id", nullable = false)
    private UUID docenteId;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoSesion estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Version
    @Column(name = "version")
    private int version;

    // ─── Accessors ────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCasoId() { return casoId; }
    public void setCasoId(UUID casoId) { this.casoId = casoId; }

    public UUID getDocenteId() { return docenteId; }
    public void setDocenteId(UUID docenteId) { this.docenteId = docenteId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public EstadoSesion getEstado() { return estado; }
    public void setEstado(EstadoSesion estado) { this.estado = estado; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
