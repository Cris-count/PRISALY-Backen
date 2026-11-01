package com.prisaly.m4.infrastructure.adapter;

import com.prisaly.m4.application.port.ParticipacionesPort;
import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.infrastructure.persistence.EntityMapper;
import com.prisaly.m4.infrastructure.persistence.JpaSesionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia que implementa {@link SesionRepository} delegando en Spring Data JPA.
 */
@Repository
public class SesionRepositoryAdapter implements SesionRepository {

    private final JpaSesionRepository jpa;
    private final JdbcTemplate jdbc;
    private final ParticipacionesPort participaciones;

    /** Constructor de compatibilidad para pruebas sin operaciones de programación. */
    public SesionRepositoryAdapter(JpaSesionRepository jpa, ParticipacionesPort participaciones) {
        this(jpa, participaciones, null);
    }

    @Autowired
    public SesionRepositoryAdapter(
            JpaSesionRepository jpa, ParticipacionesPort participaciones, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.participaciones = participaciones;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SesionAcademica> buscarParaActualizar(UUID id) {
        return jpa.bloquearPorId(id).map(EntityMapper::toDomain);
    }

    @Override
    public void protegerProgramacion(UUID caso, UUID docente) {
        if (jdbc == null
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Programación requiere una transacción de escritura");
        }
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                rs -> null,
                caso + "/" + docente);
    }

    @Override
    public boolean existeDuplicadaExcluyendo(
            UUID id, UUID caso, UUID docente, Instant inicio, Instant fin) {
        return jpa.existeDuplicadaExcluyendo(id, caso, docente, inicio, fin);
    }

    @Override
    public SesionAcademica guardar(SesionAcademica sesion) {
        var entity = EntityMapper.toEntity(sesion);
        entity = jpa.saveAndFlush(entity);
        return EntityMapper.toDomain(entity);
    }

    @Override
    public Optional<SesionAcademica> buscarPorId(UUID id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public List<SesionAcademica> listarPorDocente(UUID docenteId) {
        return jpa.findByDocenteId(docenteId).stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public List<SesionAcademica> listarTodas() {
        return jpa.findAll().stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public boolean existeDuplicada(
            UUID casoId, UUID docenteId, Instant fechaInicio, Instant fechaFin) {
        return jpa.existsDuplicada(casoId, docenteId, fechaInicio, fechaFin);
    }

    @Override
    public boolean existeSesionParaCaso(UUID casoId) {
        return jpa.existsByCasoId(casoId);
    }

    @Override
    public boolean tieneParticipacionesVinculadas(UUID sesionId) {
        return participaciones.tieneParticipacionesOIntentos(sesionId);
    }
}
