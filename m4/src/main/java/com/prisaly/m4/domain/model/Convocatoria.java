package com.prisaly.m4.domain.model;

import com.prisaly.m4.domain.rule.ReglaNegocioException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Asignación oficial. Autorizado procede de V001, no autoriza iniciar un intento. */
public class Convocatoria {
    private UUID id, sesionId, estudianteId;
    private Instant convocadoEn, fechaRetiro;
    private String estadoAutorizacion;

    public Convocatoria() {}

    public static Convocatoria crear(UUID sesion, UUID estudiante) {
        var c = new Convocatoria();
        c.id = UUID.randomUUID();
        c.sesionId = Objects.requireNonNull(sesion);
        c.estudianteId = Objects.requireNonNull(estudiante);
        c.convocadoEn = Instant.now();
        c.estadoAutorizacion = "Autorizado";
        return c;
    }

    public void confirmar() {
        throw new ReglaNegocioException(
                "M4-OPERACION-NO-DEFINIDA",
                "Confirmación no equivale al estado de autorización oficial");
    }

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

    public Instant getFechaRetiro() {
        return fechaRetiro;
    }

    public void setFechaRetiro(Instant v) {
        fechaRetiro = v;
    }

    public String getEstadoAutorizacion() {
        return estadoAutorizacion;
    }

    public void setEstadoAutorizacion(String v) {
        estadoAutorizacion = v;
    }
}
