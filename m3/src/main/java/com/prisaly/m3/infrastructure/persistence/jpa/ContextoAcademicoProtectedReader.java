package com.prisaly.m3.infrastructure.persistence.jpa;

import com.prisaly.m3.api.AccesoContextoAcademicoDenegadoException;
import com.prisaly.m3.api.ContextoAcademicoConsultaPort;
import com.prisaly.m3.api.ContextoAcademicoPersistido;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Decision;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Operacion;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** No automatic bean: app must explicitly wire a trusted verifier. Missing verifier denies all. */
public final class ContextoAcademicoProtectedReader implements ContextoAcademicoConsultaPort {
    private final AutorizacionContextoAcademicoPort autorizacion;
    private final CasoJpaAggregateReader reader;

    public ContextoAcademicoProtectedReader(AutorizacionContextoAcademicoPort autorizacion,
            CasoJpaAggregateReader reader) {
        this.autorizacion = autorizacion;
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public Optional<ContextoAcademicoPersistido> consultar(UUID idCaso, String capacidad) {
        Objects.requireNonNull(idCaso, "idCaso");
        if (capacidad == null || capacidad.isBlank() || autorizacion == null) {
            throw new AccesoContextoAcademicoDenegadoException();
        }
        Decision decision;
        try {
            decision = autorizacion.verificar(capacidad, idCaso, Operacion.CONSULTAR_CONTEXTO_EVALUABLE);
        } catch (RuntimeException failure) {
            // Do not retain untrusted verifier diagnostics: they may contain the raw capability.
            throw new AccesoContextoAcademicoDenegadoException();
        }
        if (decision != Decision.AUTORIZADO) {
            throw new AccesoContextoAcademicoDenegadoException();
        }
        // Outside the security catch: database/integrity failures remain technical failures, never absence.
        return reader.findById(idCaso).map(ContextoAcademicoProducerMapper::map);
    }
}
