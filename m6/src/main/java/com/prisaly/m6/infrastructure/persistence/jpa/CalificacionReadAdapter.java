package com.prisaly.m6.infrastructure.persistence.jpa;

import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.domain.model.Calificacion;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Internal persistence reader, not an authorized user-facing entry point. */
@Component
@Transactional(readOnly = true)
public class CalificacionReadAdapter implements ConsultaCalificacionPort {
    private final CalificacionJpaRepository repository;

    public CalificacionReadAdapter(CalificacionJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<Calificacion> findById(UUID idCalificacion) {
        return repository.findById(Objects.requireNonNull(idCalificacion, "idCalificacion"))
                .map(CalificacionReadAdapter::toDomain);
    }

    @Override
    public Optional<Calificacion> findByIntentoId(UUID idIntento) {
        return repository.findByIdIntento(Objects.requireNonNull(idIntento, "idIntento"))
                .map(CalificacionReadAdapter::toDomain);
    }

    private static Calificacion toDomain(CalificacionJpaEntity row) {
        return new Calificacion(row.getIdCalificacion(), row.getIdIntento(), row.getIdRubrica(),
                row.getValor(), row.getFechaCalculo(), row.getDetalleEvaluacion(), row.getVersionPolitica());
    }
}
