package com.prisaly.m4.infrastructure.adapter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.prisaly.m4.application.port.IdentidadPort;

/**
 * Stub temporal de M1 – Identidad.
 *
 * <p>BLOQUEO DE INTEGRACIÓN: M1 aún no tiene contratos implementados.
 * Este stub concede todos los permisos para permitir el desarrollo de M4.
 * Debe ser reemplazado cuando M1 exponga su contrato real.</p>
 */
@Component
public class IdentidadStubAdapter implements IdentidadPort {

    private static final Logger log = LoggerFactory.getLogger(IdentidadStubAdapter.class);

    private final java.util.Map<String, Boolean> permisos = new java.util.concurrent.ConcurrentHashMap<>();

    public void configurarPermiso(UUID docenteId, String permiso, boolean tiene) {
        permisos.put(docenteId + ":" + permiso, tiene);
    }

    public void limpiar() {
        permisos.clear();
    }

    @Override
    public boolean tienePermiso(UUID docenteId, String permiso) {
        boolean tiene = permisos.getOrDefault(docenteId + ":" + permiso, true);
        log.info("[M1-STUB] Verificación de permiso '{}' para docente {}: {}", permiso, docenteId, tiene);
        return tiene;
    }
}
