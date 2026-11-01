package com.prisaly.m4.application.port;

import java.util.UUID;

/**
 * Puerto hacia M3 – Casos Académicos.
 *
 * <p>M4 usa este contrato para verificar que un caso está Publicado
 * antes de permitir la programación de una sesión, y para solicitar
 * el bloqueo académico F3-03 cuando corresponda.</p>
 */
public interface CasoAcademicoPort {

    /**
     * Verifica si el caso identificado existe y tiene estado "Publicado".
     *
     * @param casoId  identificador del caso académico
     * @return true si el caso existe y está publicado
     */
    boolean estaPublicado(UUID casoId);

    /**
     * Solicita a M3 el bloqueo académico del contenido evaluable del caso
     * (regla F3-03). Se invoca al primer evento de programación.
     *
     * @param casoId  identificador del caso a bloquear
     */
    void solicitarBloqueoAcademico(UUID casoId);
}
