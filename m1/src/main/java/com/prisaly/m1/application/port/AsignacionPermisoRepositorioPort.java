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

    /** @return false si no existe una asignación habilitada (DC-M1-04: revocar dos veces no es un éxito) */
    boolean deshabilitar(UUID idUsuario, Integer idPermiso);

    /** DC-M1-04: true solo si la asignación existe y está habilitada. */
    boolean existeHabilitada(UUID idUsuario, Integer idPermiso);

    /** DC-M1-04: vuelve a habilitar una asignación revocada, registrando el nuevo asignador y la fecha. */
    AsignacionPermiso reactivar(UUID idUsuario, Integer idPermiso, UUID idAsignador, OffsetDateTime fecha);
}
