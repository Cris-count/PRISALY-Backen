package com.prisaly.m4.infrastructure.persistence;

import com.prisaly.m4.domain.model.*;

public final class EntityMapper {
    private EntityMapper() {}

    public static SesionAcademicaEntity toEntity(SesionAcademica source) {
        var target = new SesionAcademicaEntity();
        target.setId(source.getId());
        target.setCasoId(source.getCasoId());
        target.setDocenteId(source.getDocenteId());
        target.setTitulo(source.getTitulo());
        target.setDescripcion(source.getDescripcion());
        target.setFechaInicio(source.getFechaInicio());
        target.setFechaFin(source.getFechaFin());
        target.setFechaInicioReal(source.getFechaInicioReal());
        target.setFechaFinReal(source.getFechaFinReal());
        target.setTiempoMaximoParticipacionSegundos(source.getTiempoMaximoParticipacionSegundos());
        target.setLugarFisico(source.getLugarFisico());
        target.setCodigoAutorizacionHash(source.getCodigoAutorizacionHash());
        target.setCreadoEn(source.getCreadoEn());
        target.setEstado(source.getEstado());
        return target;
    }

    public static SesionAcademica toDomain(SesionAcademicaEntity source) {
        var target = new SesionAcademica();
        target.setId(source.getId());
        target.setCasoId(source.getCasoId());
        target.setDocenteId(source.getDocenteId());
        target.setTitulo(source.getTitulo());
        target.setDescripcion(source.getDescripcion());
        target.setFechaInicio(source.getFechaInicio());
        target.setFechaFin(source.getFechaFin());
        target.setFechaInicioReal(source.getFechaInicioReal());
        target.setFechaFinReal(source.getFechaFinReal());
        target.setTiempoMaximoParticipacionSegundos(source.getTiempoMaximoParticipacionSegundos());
        target.setLugarFisico(source.getLugarFisico());
        target.setCodigoAutorizacionHash(source.getCodigoAutorizacionHash());
        target.setCreadoEn(source.getCreadoEn());
        target.setEstado(source.getEstado());
        return target;
    }

    public static ConvocatoriaEntity toEntity(Convocatoria source) {
        var target = new ConvocatoriaEntity();
        target.setId(source.getId());
        target.setSesionId(source.getSesionId());
        target.setEstudianteId(source.getEstudianteId());
        target.setConvocadoEn(source.getConvocadoEn());
        target.setEstadoAutorizacion(source.getEstadoAutorizacion());
        target.setFechaRetiro(source.getFechaRetiro());
        return target;
    }

    public static Convocatoria toDomain(ConvocatoriaEntity source) {
        var target = new Convocatoria();
        target.setId(source.getId());
        target.setSesionId(source.getSesionId());
        target.setEstudianteId(source.getEstudianteId());
        target.setConvocadoEn(source.getConvocadoEn());
        target.setEstadoAutorizacion(source.getEstadoAutorizacion());
        target.setFechaRetiro(source.getFechaRetiro());
        return target;
    }
}
