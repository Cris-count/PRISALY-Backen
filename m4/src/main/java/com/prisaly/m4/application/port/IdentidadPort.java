package com.prisaly.m4.application.port;

import java.util.UUID;

/**
 * Puerto hacia M1 – Identidad y Permisos.
 *
 * <p>M4 usa este contrato para verificar que un docente tiene permisos
 * suficientes para gestionar sesiones académicas. La implementación
 * se adapta en M4 al contrato público IdentityAccessPort de M1.
 * Sin proveedor real se deniega el acceso.</p>
 */
public interface IdentidadPort {

    /** Identidad confiable de la petición; nunca se deduce de una cabecera del cliente. */
    default UUID docenteActual() {
        throw new com.prisaly.m4.domain.rule.ReglaNegocioException(
                "M4-NO-AUTENTICADO", "No existe una identidad autenticada disponible");
    }

    /**
     * Verifica si el usuario identificado por {@code docenteId}
     * tiene el permiso indicado.
     *
     * @param docenteId  identificador del docente autenticado
     * @param permiso    nombre del permiso requerido (e.g. "GESTIONAR_SESION")
     * @return true si el docente tiene el permiso
     */
    boolean tienePermiso(UUID docenteId, String permiso);
}
