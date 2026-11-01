package com.prisaly.m4.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "\"Convocatoria\"", schema = "public")
public class ConvocatoriaEntity {
    @Id
    @Column(name = "\"idConvocatoria\"")
    private UUID id;

    @Column(name = "\"idSesion\"", nullable = false)
    private UUID sesionId;

    @Column(name = "\"idEstudiante\"", nullable = false)
    private UUID estudianteId;

    @Column(name = "\"fechaConvocatoria\"", nullable = false, updatable = false)
    private Instant convocadoEn;

    @Column(name = "\"estadoAutorizacion\"", nullable = false, length = 25)
    private String estadoAutorizacion;

    @Column(name = "\"fechaRetiro\"")
    private Instant fechaRetiro;

    public UUID getId() {
        return id;
    }

    public void setId(UUID v) {
        id = v;
    }

    public UUID getSesionId() {
        return sesionId;
    }

    public void setSesionId(UUID v) {
        sesionId = v;
    }

    public UUID getEstudianteId() {
        return estudianteId;
    }

    public void setEstudianteId(UUID v) {
        estudianteId = v;
    }

    public Instant getConvocadoEn() {
        return convocadoEn;
    }

    public void setConvocadoEn(Instant v) {
        convocadoEn = v;
    }

    public String getEstadoAutorizacion() {
        return estadoAutorizacion;
    }

    public void setEstadoAutorizacion(String v) {
        estadoAutorizacion = v;
    }

    public Instant getFechaRetiro() {
        return fechaRetiro;
    }

    public void setFechaRetiro(Instant v) {
        fechaRetiro = v;
    }
}
