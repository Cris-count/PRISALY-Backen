package com.prisaly.m4.infrastructure.persistence;

import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.domain.model.SesionAcademica;

/**
 * Mapper entre modelos de dominio y entidades JPA.
 * Evita que la capa de dominio conozca las anotaciones de persistencia.
 */
public final class EntityMapper {

    private EntityMapper() {
    }

    // ─── SesionAcademica ↔ Entity ─────────────────────────────

    public static SesionAcademicaEntity toEntity(SesionAcademica model) {
        var entity = new SesionAcademicaEntity();
        entity.setId(model.getId());
        entity.setCasoId(model.getCasoId());
        entity.setDocenteId(model.getDocenteId());
        entity.setTitulo(model.getTitulo());
        entity.setDescripcion(model.getDescripcion());
        entity.setFechaInicio(model.getFechaInicio());
        entity.setFechaFin(model.getFechaFin());
        entity.setEstado(model.getEstado());
        entity.setCreadoEn(model.getCreadoEn());
        entity.setActualizadoEn(model.getActualizadoEn());
        entity.setVersion(model.getVersion());
        return entity;
    }

    public static SesionAcademica toDomain(SesionAcademicaEntity entity) {
        var model = new SesionAcademica();
        model.setId(entity.getId());
        model.setCasoId(entity.getCasoId());
        model.setDocenteId(entity.getDocenteId());
        model.setTitulo(entity.getTitulo());
        model.setDescripcion(entity.getDescripcion());
        model.setFechaInicio(entity.getFechaInicio());
        model.setFechaFin(entity.getFechaFin());
        model.setEstado(entity.getEstado());
        model.setCreadoEn(entity.getCreadoEn());
        model.setActualizadoEn(entity.getActualizadoEn());
        model.setVersion(entity.getVersion());
        return model;
    }

    // ─── Convocatoria ↔ Entity ────────────────────────────────

    public static ConvocatoriaEntity toEntity(Convocatoria model) {
        var entity = new ConvocatoriaEntity();
        entity.setId(model.getId());
        entity.setSesionId(model.getSesionId());
        entity.setEstudianteId(model.getEstudianteId());
        entity.setConvocadoEn(model.getConvocadoEn());
        entity.setConfirmada(model.isConfirmada());
        return entity;
    }

    public static Convocatoria toDomain(ConvocatoriaEntity entity) {
        var model = new Convocatoria();
        model.setId(entity.getId());
        model.setSesionId(entity.getSesionId());
        model.setEstudianteId(entity.getEstudianteId());
        model.setConvocadoEn(entity.getConvocadoEn());
        model.setConfirmada(entity.isConfirmada());
        return model;
    }
}
