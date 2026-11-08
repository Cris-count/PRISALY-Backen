package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort.Decision;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.application.query.CasoDocenteFicha;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Explicit internal composition only: no bean or HTTP endpoint until read policy is supplied. */
public final class CasoDocenteConsultaService {
    public static final String ACCESO_DENEGADO = "CASE_READ_DENIED";
    public static final String CASO_INEXISTENTE = "CASE_READ_NOT_FOUND";
    private final CasoCurrentUserPort identidad;
    private final AutorizacionConsultaCasoPort autorizacion;
    private final CasoRepositoryPort repository;

    public CasoDocenteConsultaService(CasoCurrentUserPort identidad,
            AutorizacionConsultaCasoPort autorizacion, CasoRepositoryPort repository) {
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public CasoDocenteFicha consultar(String idCaso) {
        UUID id = canonicalId(idCaso);
        UUID usuario;
        try {
            usuario = identidad == null ? null : identidad.currentUserId();
        } catch (RuntimeException failure) {
            throw new CasoAuthenticationException();
        }
        if (usuario == null) { throw new CasoAuthenticationException(); }
        if (autorizacion == null) { throw denied(); }
        requireAuthorized(() -> autorizacion.verificarAcceso(usuario, id));
        // Technical repository failures must remain distinct from absence and denial.
        var caso = repository.findById(id.toString())
                .orElseThrow(() -> new CasoDomainException(CASO_INEXISTENTE, "Case not found"));
        if (!id.equals(canonicalId(caso.id()))) {
            throw new IllegalStateException("Repository returned a different case");
        }
        if (!usuario.equals(canonicalId(caso.docenteId()))) { throw denied(); }
        requireAuthorized(() -> autorizacion.verificarVisibilidad(usuario, id, caso.estado(), caso.activo()));
        return new CasoDocenteFicha(id, caso.titulo(), caso.tiempoEstimadoSegundos(), caso.estado());
    }

    private static UUID canonicalId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) { throw new IllegalArgumentException(); }
            return id;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new IllegalArgumentException("A complete case/owner UUID is required");
        }
    }

    private static void requireAuthorized(Supplier<Decision> verification) {
        Decision decision;
        try { decision = verification.get(); }
        catch (RuntimeException failure) { throw denied(); }
        if (decision != Decision.AUTORIZADO) { throw denied(); }
    }

    private static CasoDomainException denied() {
        return new CasoDomainException(ACCESO_DENEGADO, "Case read denied");
    }
}
