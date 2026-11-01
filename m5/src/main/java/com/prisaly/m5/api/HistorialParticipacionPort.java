package com.prisaly.m5.api;

import java.util.Collection;
import java.util.UUID;

/**
 * Contrato público de M5 para M4: ¿hay historial de participación en unas
 * convocatorias?
 *
 * <p>Responde a la necesidad declarada por M4 en
 * {@code com.prisaly.m4.application.port.ParticipacionesPort}
 * ("pendiente de enlazar con un contrato público de M5"): antes de editar una
 * sesión, M4 debe saber si hay participaciones o intentos. Un Intento exige una
 * Participacion (FK de BD-005.2), así que basta con las participaciones.
 *
 * <p>Recibe convocatorias y no una sesión: M4 conoce las convocatorias de sus
 * sesiones y M5 no lee la tabla {@code "Convocatoria"} de M4.
 *
 * <p><b>Pendiente de Arquitectura:</b> M5 depende de M4. Si M4 dependiera de
 * {@code m5} para consumir este contrato, habría un ciclo Maven. Las opciones
 * (M5 implementa el puerto de M4 con un contrato de convocatorias por sesión,
 * contrato compartido o cableado en {@code app}) están en
 * {@code m5/docs/M5-DOCUMENTO-TECNICO.md}, sección 6.
 *
 * <p>Debe invocarse dentro de la transacción de M4 que modifica la sesión. Hoy
 * M5 no crea participaciones; cuando exista el inicio de intentos, deberá
 * serializarse con esa modificación (por ejemplo, bloqueando la fila de la
 * sesión), según el protocolo que acuerden M4 y M5.
 */
public interface HistorialParticipacionPort {

    /**
     * @param convocatorias identificadores {@code Convocatoria.idConvocatoria}; vacío → {@code false}
     * @return {@code true} si alguna tiene al menos una participación
     * @throws IllegalArgumentException si la colección o algún elemento es {@code null}
     */
    boolean existeParticipacion(Collection<UUID> convocatorias);
}
