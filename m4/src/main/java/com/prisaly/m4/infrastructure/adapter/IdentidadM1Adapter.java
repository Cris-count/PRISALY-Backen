package com.prisaly.m4.infrastructure.adapter;

import java.util.Objects;
import java.util.UUID;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

/** Consume exclusivamente el contrato público de M1; no lee sus repositorios. */
public final class IdentidadM1Adapter implements IdentidadPort {
    private final IdentityAccessPort identidad;

    public IdentidadM1Adapter(IdentityAccessPort identidad) {
        this.identidad = Objects.requireNonNull(identidad);
    }

    @Override
    public UUID docenteActual() {
        try {
            var usuario = identidad.currentUser();
            if (usuario == null || usuario.userId() == null) {
                throw new ReglaNegocioException("M4-NO-AUTENTICADO", "No existe una identidad autenticada disponible");
            }
            return usuario.userId();
        } catch (IdentityDomainException ex) {
            throw traducir(ex);
        }
    }

    @Override
    public boolean tienePermiso(UUID docenteId, String permiso) {
        UUID actual = docenteActual();
        if (!actual.equals(docenteId)) {
            return false;
        }
        try {
            // No traduce ni inventa permisos: M1 debe haber asignado el código solicitado.
            return identidad.hasPermission(actual, permiso);
        } catch (IdentityDomainException ex) {
            throw traducir(ex);
        }
    }

    private ReglaNegocioException traducir(IdentityDomainException ex) {
        return switch (ex.code()) {
            case "UNAUTHENTICATED" -> new ReglaNegocioException("M4-NO-AUTENTICADO", "Se requiere autenticación");
            case "USER_NOT_FOUND", "USER_INACTIVE" ->
                    new ReglaNegocioException("M4-SIN-PERMISO", "La identidad no está habilitada");
            default -> new ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE", "No se pudo validar la identidad mediante M1");
        };
    }
}
