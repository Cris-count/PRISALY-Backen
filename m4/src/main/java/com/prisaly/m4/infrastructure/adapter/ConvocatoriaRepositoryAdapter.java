package com.prisaly.m4.infrastructure.adapter;

import com.prisaly.m4.api.ConvocatoriasSesionPort;
import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.infrastructure.persistence.EntityMapper;
import com.prisaly.m4.infrastructure.persistence.JpaConvocatoriaRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrón Adapter. Implementa a la vez el repositorio interno de M4 y el puerto
 * {@link ConvocatoriasSesionPort} que otros módulos usan para saber si un estudiante fue citado.
 */
@Repository
public class ConvocatoriaRepositoryAdapter implements ConvocatoriaRepository, ConvocatoriasSesionPort {

    private final JpaConvocatoriaRepository jpa;

    public ConvocatoriaRepositoryAdapter(JpaConvocatoriaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Convocatoria guardar(Convocatoria convocatoria) {
        var entity = EntityMapper.toEntity(convocatoria);
        // Validar FK/unicidad antes de invocar M7; todo permanece en la misma transacción.
        entity = jpa.saveAndFlush(entity);
        return EntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Convocatoria> buscarPorId(UUID id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public List<UUID> idsPorSesion(UUID sesionId) {
        return listarPorSesion(sesionId).stream().map(Convocatoria::getId).toList();
    }

    @Override
    public List<Convocatoria> listarPorSesion(UUID sesionId) {
        return jpa.findBySesionId(sesionId).stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public boolean existeConvocatoria(UUID sesionId, UUID estudianteId) {
        return jpa.existsBySesionIdAndEstudianteId(sesionId, estudianteId);
    }

    @Override
    public boolean estaConvocado(UUID sesionId, UUID estudianteId) {
        return jpa.existsBySesionIdAndEstudianteId(sesionId, estudianteId);
    }
}
