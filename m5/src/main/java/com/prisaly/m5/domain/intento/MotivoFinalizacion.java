package com.prisaly.m5.domain.intento;

/**
 * Motivo por el que un intento pasó a Finalizado.
 *
 * <p>Contiene solo los motivos que necesitan las operaciones implementadas.
 * No es el catálogo académico definitivo: los motivos adicionales (por ejemplo,
 * un cierre de sesión por parte del docente) quedan pendientes de los
 * artefactos de diseño (BD-005.2 / ARQ-008.x).
 */
public enum MotivoFinalizacion {

    /**
     * El estudiante entrega el intento por decisión propia.
     *
     * <p>Provisional: el nombre definitivo debe conciliarse con BD-005.2.
     */
    ENTREGA_ESTUDIANTE,

    /** El tiempo disponible para el intento se agotó. No es un estado. */
    TIEMPO_VENCIDO
}
