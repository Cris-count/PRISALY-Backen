package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort.Decision;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.application.port.CasoUpdatePort;
import com.prisaly.m3.application.query.CasoDocenteFicha;
import com.prisaly.m3.application.query.CasoDocenteFichaVersionada;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Protected teacher query; versioned reads reuse the existing consistent aggregate reader. */
public final class CasoDocenteConsultaService {
    public static final String ACCESO_DENEGADO = "CASE_READ_DENIED";
    public static final String CASO_INEXISTENTE = "CASE_READ_NOT_FOUND";
    private final CasoCurrentUserPort identidad;
    private final AutorizacionConsultaCasoPort autorizacion;
    private final CasoRepositoryPort repository;
    private final CasoUpdatePort versionedReader;

    public CasoDocenteConsultaService(CasoCurrentUserPort identidad,
            AutorizacionConsultaCasoPort autorizacion, CasoRepositoryPort repository) {
        this(identidad, autorizacion, repository, null);
    }

    public CasoDocenteConsultaService(CasoCurrentUserPort identidad,
            AutorizacionConsultaCasoPort autorizacion, CasoRepositoryPort repository, CasoUpdatePort versionedReader) {
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.versionedReader = versionedReader;
    }

    public CasoDocenteFicha consultar(String idCaso) {
        Caso caso = authorized(idCaso, id -> repository.findById(id.toString()), java.util.function.Function.identity());
        return project(caso);
    }

    public CasoDocenteFichaVersionada consultarConVersion(String idCaso) {
        var snapshot = authorized(idCaso, id -> {
            if (versionedReader == null) { throw new IllegalStateException("Versioned reader unavailable"); }
            return versionedReader.findById(id);
        }, com.prisaly.m3.application.update.CasoUpdateResult::caso);
        return new CasoDocenteFichaVersionada(project(snapshot.caso()), snapshot.fingerprint());
    }

    /** Complete aggregate for its owner, under the same read policy as the summary. */
    public com.prisaly.m3.application.update.CasoUpdateResult consultarContenido(String idCaso) {
        return authorized(idCaso, id -> {
            if (versionedReader == null) { throw new IllegalStateException("Versioned reader unavailable"); }
            return versionedReader.findById(id);
        }, com.prisaly.m3.application.update.CasoUpdateResult::caso);
    }

    private static CasoDocenteFicha project(Caso caso) {
        return new CasoDocenteFicha(UUID.fromString(caso.id()), caso.titulo(), caso.tiempoEstimadoSegundos(), caso.estado());
    }

    private <T> T authorized(String idCaso,
            java.util.function.Function<UUID, java.util.Optional<T>> load,
            java.util.function.Function<T, Caso> aggregate) {
        UUID id = canonicalId(idCaso);
        UUID usuario;
        try { usuario = identidad == null ? null : identidad.currentUserId(); }
        catch (CasoAuthenticationException | CasoDomainException classified) { throw classified; }
        catch (RuntimeException technicalFailure) { throw unavailable(); }
        if (usuario == null) { throw new CasoAuthenticationException(); }
        if (autorizacion == null) { throw denied(); }
        requireAuthorized(() -> autorizacion.verificarAcceso(usuario, id));
        // Technical repository failures must remain distinct from absence and denial.
        T snapshot = load.apply(id)
                .orElseThrow(() -> new CasoDomainException(CASO_INEXISTENTE, "Case not found"));
        Caso caso = aggregate.apply(snapshot);
        if (!id.equals(canonicalId(caso.id()))) {
            throw new IllegalStateException("Repository returned a different case");
        }
        if (!usuario.equals(canonicalId(caso.docenteId()))) { throw denied(); }
        requireAuthorized(() -> autorizacion.verificarVisibilidad(usuario, id, caso.estado(), caso.activo()));
        return snapshot;
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
        catch (CasoDomainException classified) {
            if ("FORBIDDEN".equals(classified.code()) || ACCESO_DENEGADO.equals(classified.code())) { throw denied(); }
            throw unavailable();
        }
        catch (RuntimeException technicalFailure) { throw unavailable(); }
        if (decision == Decision.DENEGADO) { throw denied(); }
        if (decision != Decision.AUTORIZADO) { throw unavailable(); }
    }

    private static CasoDomainException unavailable() {
        return new CasoDomainException("CASE_AUTHORIZATION_UNAVAILABLE", "Institutional verification unavailable");
    }

    private static CasoDomainException denied() {
        return new CasoDomainException(ACCESO_DENEGADO, "Case read denied");
    }
}
