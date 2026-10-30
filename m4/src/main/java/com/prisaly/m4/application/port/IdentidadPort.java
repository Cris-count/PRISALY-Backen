package com.prisaly.m4.application.port;

import java.util.UUID;

/**
 * Puerto hacia M1 – Identidad y Permisos.
 *
 * <p>M4 usa este contrato para verificar que un docente tiene permisos
 * suficientes para gestionar sesiones académicas. La implementación
 * real la proveerá M1; mientras tanto se usa un stub.</p>
 */
public interface IdentidadPort {

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
