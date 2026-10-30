package com.prisaly.m4.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.infrastructure.persistence.EntityMapper;
import com.prisaly.m4.infrastructure.persistence.JpaSesionRepository;

/**
 * Adaptador de persistencia que implementa {@link SesionRepository}
 * delegando en Spring Data JPA.
 */
@Repository
public class SesionRepositoryAdapter implements SesionRepository {

    private final JpaSesionRepository jpa;

    public SesionRepositoryAdapter(JpaSesionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public SesionAcademica guardar(SesionAcademica sesion) {
        var entity = EntityMapper.toEntity(sesion);
        entity = jpa.save(entity);
        return EntityMapper.toDomain(entity);
    }

    @Override
    public Optional<SesionAcademica> buscarPorId(UUID id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public List<SesionAcademica> listarPorDocente(UUID docenteId) {
        return jpa.findByDocenteId(docenteId).stream()
                .map(EntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<SesionAcademica> listarTodas() {
        return jpa.findAll().stream()
                .map(EntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existeDuplicada(UUID casoId, UUID docenteId,
                                    LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        return jpa.existsDuplicada(casoId, docenteId, fechaInicio, fechaFin);
    }

    @Override
    public boolean existeSesionParaCaso(UUID casoId) {
        return jpa.existsByCasoId(casoId);
    }

    @Override
    public boolean tieneParticipacionesVinculadas(UUID sesionId) {
        // TODO: Implementar cuando M5 provea el contrato de participaciones.
        // Por ahora, se retorna false para no bloquear el flujo de desarrollo.
        return false;
    }
}
