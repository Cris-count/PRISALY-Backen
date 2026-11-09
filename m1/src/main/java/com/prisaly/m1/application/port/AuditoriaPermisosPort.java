package com.prisaly.m1.application.port;

import java.util.UUID;

/** Registro de asignaciones y revocaciones. Solo identificadores y códigos, nunca credenciales. */
public interface AuditoriaPermisosPort {
    String LOGGER = "prisaly.auditoria.permisos";

    void registrarRechazo(String accion, UUID usuario, String permiso, UUID actor, String codigoRechazo);

    /** El éxito se registra solo cuando la transacción se confirma. */
    void registrarExitoAlConfirmar(String accion, UUID usuario, String permiso, UUID actor);
}
