package com.prisaly.m4.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA para la tabla de convocatorias estudiantiles.
 */
@Entity
@Table(name = "convocatorias")
public class ConvocatoriaEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sesionId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "convocado_en", nullable = false, updatable = false)
    private LocalDateTime convocadoEn;

    @Column(name = "confirmada", nullable = false)
    private boolean confirmada;

    // ─── Accessors ────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSesionId() { return sesionId; }
    public void setSesionId(UUID sesionId) { this.sesionId = sesionId; }

    public UUID getEstudianteId() { return estudianteId; }
    public void setEstudianteId(UUID estudianteId) { this.estudianteId = estudianteId; }

    public LocalDateTime getConvocadoEn() { return convocadoEn; }
    public void setConvocadoEn(LocalDateTime convocadoEn) { this.convocadoEn = convocadoEn; }

    public boolean isConfirmada() { return confirmada; }
    public void setConfirmada(boolean confirmada) { this.confirmada = confirmada; }
}
