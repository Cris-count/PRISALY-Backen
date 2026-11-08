package com.prisaly.config;

import com.prisaly.m3.domain.model.CasoEstado;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Institutional read policy for M3 cases, supplied by configuration once Dirección Central ratifies
 * it (permission code, visible states, active-only). While unratified it is {@link #pendiente()}
 * and every read is denied. A partial definition is a configuration error, never a silent default.
 */
public record PoliticaLecturaCaso(Optional<String> permiso, Set<CasoEstado> estadosVisibles, boolean requiereActivo) {

    public PoliticaLecturaCaso {
        Objects.requireNonNull(permiso, "permiso");
        estadosVisibles = Set.copyOf(Objects.requireNonNull(estadosVisibles, "estadosVisibles"));
        if (permiso.isPresent() != !estadosVisibles.isEmpty()) {
            throw new IllegalStateException("The M3 read policy needs both a permission code and visible states");
        }
    }

    public static PoliticaLecturaCaso pendiente() {
        return new PoliticaLecturaCaso(Optional.empty(), Set.of(), true);
    }

    /** Builds the policy from {@code prisaly.m3.lectura.*}; both values blank means "not ratified". */
    public static PoliticaLecturaCaso desdeConfiguracion(String permiso, String estadosVisibles, boolean requiereActivo) {
        String codigo = Objects.requireNonNull(permiso, "prisaly.m3.lectura.permiso").trim();
        String estados = Objects.requireNonNull(estadosVisibles, "prisaly.m3.lectura.estados-visibles").trim();
        if (codigo.isEmpty() && estados.isEmpty()) {
            return pendiente();
        }
        if (!codigo.matches("[A-Z][A-Z0-9_]{2,59}")) {
            throw new IllegalStateException("Invalid M3 read permission code: " + codigo);
        }
        Set<CasoEstado> visibles = estados.isEmpty()
                ? Set.of()
                : Arrays.stream(estados.split(","))
                        .map(String::trim)
                        .map(CasoEstado::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(CasoEstado.class)));
        return new PoliticaLecturaCaso(Optional.of(codigo), visibles, requiereActivo);
    }

    public boolean ratificada() {
        return permiso.isPresent();
    }

    public boolean visible(CasoEstado estado, boolean activo) {
        return ratificada() && estado != null && estadosVisibles.contains(estado) && (activo || !requiereActivo);
    }
}
