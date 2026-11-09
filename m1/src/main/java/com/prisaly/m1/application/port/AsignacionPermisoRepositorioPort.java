package com.prisaly.m1.application.port;

import com.prisaly.m1.domain.model.AsignacionPermiso;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/** Persistencia de las asignaciones individuales de permisos. */
public interface AsignacionPermisoRepositorioPort {
    Set<String> codigosHabilitados(UUID idUsuario);

    boolean existe(UUID idUsuario, Integer idPermiso);

    AsignacionPermiso registrar(UUID idUsuario, Integer idPermiso, UUID idAsignador, OffsetDateTime fecha);

    /** @return false si la asignación no existe */
    boolean deshabilitar(UUID idUsuario, Integer idPermiso);
}
