package com.prisaly.m4.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa la convocatoria de un estudiante a una sesión académica.
 * Es un value-object del agregado SesionAcademica.
 */
public class Convocatoria {

    private UUID id;
    private UUID sesionId;
    private UUID estudianteId;
    private LocalDateTime convocadoEn;
    private boolean confirmada;

    public Convocatoria() {
    }

    /**
     * Fábrica para crear una nueva convocatoria.
     */
    public static Convocatoria crear(UUID sesionId, UUID estudianteId) {
        Objects.requireNonNull(sesionId, "sesionId no puede ser nulo");
        Objects.requireNonNull(estudianteId, "estudianteId no puede ser nulo");

        var c = new Convocatoria();
        c.id = UUID.randomUUID();
        c.sesionId = sesionId;
        c.estudianteId = estudianteId;
        c.convocadoEn = LocalDateTime.now();
        c.confirmada = false;
        return c;
    }

    public void confirmar() {
        this.confirmada = true;
    }

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
