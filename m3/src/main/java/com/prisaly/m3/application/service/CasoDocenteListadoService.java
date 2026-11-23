package com.prisaly.m3.application.service;

import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort.Decision;
import com.prisaly.m3.application.port.CasoAuthenticationException;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoListadoDocentePort;
import com.prisaly.m3.application.query.CasoDocenteFicha;
import com.prisaly.m3.domain.rule.CasoDomainException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * DC-BE-CIERRE-FUNCIONAL-NOCTURNO-67 — CANDIDATE listing of the teacher's own cases (OBS-53-02), behind
 * {@code prisaly.m3.listado.enabled=false}. It introduces no policy: it applies the ratified M3 read policy of
 * {@link CasoDocenteConsultaService} (DC-M3-RES-02) to a set: the M1 read permission (checked once, before any
 * repository access), ownership by construction (only {@code Caso.idDocente} = current user is queried) and per-case
 * state visibility ({@link AutorizacionConsultaCasoPort#verificarVisibilidad}); a case that is not visible is omitted.
 * Only the summary fields of {@code GET /v1/casos/{id}} are returned.
 */
public final class CasoDocenteListadoService {
    private final CasoCurrentUserPort identidad;
    private final AutorizacionConsultaCasoPort autorizacion;
    private final CasoListadoDocentePort listado;

    public CasoDocenteListadoService(CasoCurrentUserPort identidad, AutorizacionConsultaCasoPort autorizacion,
            CasoListadoDocentePort listado) {
        this.identidad = identidad;
        this.autorizacion = autorizacion;
        this.listado = Objects.requireNonNull(listado, "listado");
    }

    public List<CasoDocenteFicha> listarPropios() {
        UUID usuario;
        try { usuario = identidad == null ? null : identidad.currentUserId(); }
        catch (CasoAuthenticationException | CasoDomainException classified) { throw classified; }
        catch (RuntimeException technicalFailure) { throw unavailable(); }
        if (usuario == null) { throw new CasoAuthenticationException(); }
        if (autorizacion == null) { throw denied(); }
        if (decidir(() -> autorizacion.verificarListado(usuario)) != Decision.AUTORIZADO) { throw denied(); }
        return listado.listarPorDocente(usuario).stream()
                .filter(f -> decidir(() -> autorizacion.verificarVisibilidad(usuario, f.idCaso(), f.estado(), f.activo()))
                        == Decision.AUTORIZADO)
                .map(f -> new CasoDocenteFicha(f.idCaso(), f.titulo(), f.tiempoEstimadoSegundos(), f.estado()))
                .toList();
    }

    private static Decision decidir(Supplier<Decision> verificacion) {
        Decision d;
        try { d = verificacion.get(); }
        catch (RuntimeException technicalFailure) { throw unavailable(); }
        if (d == null || d == Decision.NO_DISPONIBLE) { throw unavailable(); }
        return d;
    }

    private static CasoDomainException unavailable() {
        return new CasoDomainException("CASE_AUTHORIZATION_UNAVAILABLE", "Institutional verification unavailable");
    }

    private static CasoDomainException denied() {
        return new CasoDomainException(CasoDocenteConsultaService.ACCESO_DENEGADO, "Case listing denied");
    }
}
