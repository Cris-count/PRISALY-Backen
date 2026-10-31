package com.prisaly.m4.domain.model;

import com.prisaly.m4.domain.rule.*;
import com.prisaly.m4.domain.state.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Agregado M4 conciliado con BD-005.2; State sin versión persistente inventada. */
public class SesionAcademica {
    private UUID id, casoId, docenteId;
    private String titulo, descripcion, lugarFisico, codigoAutorizacionHash;
    private Instant fechaInicio, fechaFin, fechaInicioReal, fechaFinReal, creadoEn;
    private int tiempoMaximoParticipacionSegundos;
    private EstadoSesion estado;
    private transient SesionState stateHandler;

    public SesionAcademica() {}

    public static SesionAcademica crear(
            UUID caso,
            UUID docente,
            String titulo,
            String mensaje,
            Instant inicio,
            Instant fin,
            int duracion) {
        validarFechas(inicio, fin);
        validarTitulo(titulo);
        if (duracion <= 0)
            throw new ReglaNegocioException(
                    "M4-DURACION-INVALIDA", "La duración máxima debe ser positiva");
        var s = new SesionAcademica();
        s.id = UUID.randomUUID();
        s.casoId = Objects.requireNonNull(caso);
        s.docenteId = Objects.requireNonNull(docente);
        s.titulo = titulo.strip();
        s.descripcion = mensaje;
        s.fechaInicio = inicio;
        s.fechaFin = fin;
        s.tiempoMaximoParticipacionSegundos = duracion;
        s.estado = EstadoSesion.SIN_INICIAR;
        s.creadoEn = Instant.now();
        return s;
    }

    public void abrir() {
        throw new SesionOperacionNoPermitidaException(
                estado.name(), "abrir: sin equivalencia oficial");
    }

    public void cancelar() {
        throw new SesionOperacionNoPermitidaException(
                estado.name(), "cancelar: sin equivalencia oficial");
    }

    public void iniciar() {
        resolverState().iniciar(this);
    }

    public void cerrar() {
        resolverState().cerrar(this);
    }

    public void cambiarEstado(SesionState nuevo) {
        estado = nuevo.getEstado();
        stateHandler = nuevo;
        if (estado == EstadoSesion.EN_CURSO && fechaInicioReal == null)
            fechaInicioReal = Instant.now();
        if (estado == EstadoSesion.FINALIZADO && fechaFinReal == null) fechaFinReal = Instant.now();
    }

    public void actualizarProgramacion(String titulo, String mensaje, Instant inicio, Instant fin) {
        if (!resolverState().permiteModificacion())
            throw new SesionOperacionNoPermitidaException(estado.name(), "actualizarProgramacion");
        validarFechas(inicio, fin);
        validarTitulo(titulo);
        this.titulo = titulo.strip();
        descripcion = mensaje;
        fechaInicio = inicio;
        fechaFin = fin;
    }

    public void cambiarCaso(UUID caso) {
        if (!resolverState().permiteModificacion())
            throw new SesionOperacionNoPermitidaException(estado.name(), "cambiarCaso");
        casoId = Objects.requireNonNull(caso);
    }

    public boolean permiteConvocatoria() {
        return resolverState().permiteConvocatoria();
    }

    private SesionState resolverState() {
        if (stateHandler == null) stateHandler = SesionStateFactory.of(estado);
        return stateHandler;
    }

    private static void validarTitulo(String v) {
        if (v == null || v.isBlank() || v.strip().length() > 150)
            throw new ReglaNegocioException(
                    "M4-VALIDACION", "Nombre obligatorio, máximo 150 caracteres");
    }

    private static void validarFechas(Instant a, Instant b) {
        if (a == null || b == null || !b.isAfter(a))
            throw new ReglaNegocioException(
                    "M4-FECHA-INVALIDA", "Fin previsto debe ser posterior a inicio previsto");
    }

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

    /** Mensaje personalizado; accessor Java legado. No migra datos antiguos automáticamente. */
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
        stateHandler = null;
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
}
