package com.prisaly.m4.infrastructure.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.infrastructure.persistence.EntityMapper;
import com.prisaly.m4.infrastructure.persistence.JpaConvocatoriaRepository;

/**
 * Adaptador de persistencia para convocatorias.
 */
@Repository
public class ConvocatoriaRepositoryAdapter implements ConvocatoriaRepository {

    private final JpaConvocatoriaRepository jpa;

    public ConvocatoriaRepositoryAdapter(JpaConvocatoriaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Convocatoria guardar(Convocatoria convocatoria) {
        var entity = EntityMapper.toEntity(convocatoria);
        entity = jpa.save(entity);
        return EntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Convocatoria> buscarPorId(UUID id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public List<Convocatoria> listarPorSesion(UUID sesionId) {
        return jpa.findBySesionId(sesionId).stream()
                .map(EntityMapper::toDomain)
                .toList();
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
