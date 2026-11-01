package com.prisaly.m6.infrastructure.persistence.jpa;

import com.prisaly.m6.application.port.ConsultaRetroalimentacionPort;
import com.prisaly.m6.domain.model.RetroalimentacionGeneral;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Internal persistence reader, not an institutionally authorized user-facing entry point. */
@Component
@Transactional(readOnly = true)
public class RetroalimentacionGeneralReadAdapter implements ConsultaRetroalimentacionPort {
    private final RetroalimentacionGeneralJpaRepository repository;

    public RetroalimentacionGeneralReadAdapter(RetroalimentacionGeneralJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<RetroalimentacionGeneral> findById(UUID idRetroalimentacion) {
        return repository.findById(Objects.requireNonNull(idRetroalimentacion, "idRetroalimentacion"))
                .map(RetroalimentacionGeneralReadAdapter::toDomain);
    }

    @Override
    public Optional<RetroalimentacionGeneral> findByParticipacionId(UUID idParticipacion) {
        return repository.findByIdParticipacion(Objects.requireNonNull(idParticipacion, "idParticipacion"))
                .map(RetroalimentacionGeneralReadAdapter::toDomain);
    }

    private static RetroalimentacionGeneral toDomain(RetroalimentacionGeneralJpaEntity row) {
        return new RetroalimentacionGeneral(row.getIdRetroalimentacion(), row.getIdParticipacion(),
                row.getIdDocente(), row.getContenido(), row.getFechaRegistro());
    }
}
