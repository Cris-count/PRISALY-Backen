package com.prisaly.m4.infrastructure.persistence;

import com.prisaly.m4.domain.model.EstadoSesion;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "\"Sesion\"", schema = "public")
public class SesionAcademicaEntity {
    @Id
    @Column(name = "\"idSesion\"")
    private UUID id;

    @Column(name = "\"idCaso\"", nullable = false)
    private UUID casoId;

    @Column(name = "\"idDocenteProgramador\"", nullable = false)
    private UUID docenteId;

    @Column(name = "\"nombreEspacioAcademico\"", nullable = false, length = 150)
    private String titulo;

    @Column(name = "\"mensajePersonalizado\"", columnDefinition = "text")
    private String descripcion;

    @Column(name = "\"inicioPrevisto\"", nullable = false)
    private Instant fechaInicio;

    @Column(name = "\"finPrevisto\"", nullable = false)
    private Instant fechaFin;

    @Column(name = "\"fechaInicioReal\"")
    private Instant fechaInicioReal;

    @Column(name = "\"fechaFinReal\"")
    private Instant fechaFinReal;

    @Column(name = "\"tiempoMaximoParticipacionSegundos\"", nullable = false)
    private int tiempoMaximoParticipacionSegundos;

    @Column(name = "\"lugarFisico\"", length = 200)
    private String lugarFisico;

    @Column(name = "\"codigoAutorizacionHash\"", length = 255)
    private String codigoAutorizacionHash;

    @Column(name = "\"fechaCreacion\"", nullable = false, updatable = false)
    private Instant creadoEn;

    @Convert(converter = EstadoSesionConverter.class)
    @Column(name = "\"estado\"", nullable = false, length = 20)
    private EstadoSesion estado;

    public UUID getId() {
        return id;
    }

    public void setId(UUID v) {
        id = v;
    }

    public UUID getCasoId() {
        return casoId;
    }

    public void setCasoId(UUID v) {
        casoId = v;
    }

    public UUID getDocenteId() {
        return docenteId;
    }

    public void setDocenteId(UUID v) {
        docenteId = v;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String v) {
        titulo = v;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String v) {
        descripcion = v;
    }

    public Instant getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(Instant v) {
        fechaInicio = v;
    }

    public Instant getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(Instant v) {
        fechaFin = v;
    }

    public Instant getFechaInicioReal() {
        return fechaInicioReal;
    }

    public void setFechaInicioReal(Instant v) {
        fechaInicioReal = v;
    }

    public Instant getFechaFinReal() {
        return fechaFinReal;
    }

    public void setFechaFinReal(Instant v) {
        fechaFinReal = v;
    }

    public int getTiempoMaximoParticipacionSegundos() {
        return tiempoMaximoParticipacionSegundos;
    }

    public void setTiempoMaximoParticipacionSegundos(int v) {
        tiempoMaximoParticipacionSegundos = v;
    }

    public String getLugarFisico() {
        return lugarFisico;
    }

    public void setLugarFisico(String v) {
        lugarFisico = v;
    }

    public String getCodigoAutorizacionHash() {
        return codigoAutorizacionHash;
    }

    public void setCodigoAutorizacionHash(String v) {
        codigoAutorizacionHash = v;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant v) {
        creadoEn = v;
    }

    public EstadoSesion getEstado() {
        return estado;
    }

    public void setEstado(EstadoSesion v) {
        estado = v;
    }
}
