package com.prisaly.m5.application.acceso;

import com.prisaly.m5.application.ReferenciaIntento;

/**
 * Puerto de M5 (lado consumidor): ¿pertenece un intento al estudiante
 * autenticado?
 *
 * <p>En BD-005.2 el titular se llega por Intento."idParticipacion" →
 * Participacion."idConvocatoria" (tablas de M5) → Convocatoria."idEstudiante"
 * (tabla de M4). El último paso lo resuelve el contrato público de M4
 * {@code com.prisaly.m4.api.ProgramacionParticipacionPort}, que recibe la
 * convocatoria y el estudiante y no revela al titular: por eso el puerto
 * pregunta por un estudiante concreto en lugar de devolver el titular.
 *
 * <p>El estudiante llega siempre de {@link IdentidadEstudianteActual} (M1),
 * nunca de un dato del cliente. La titularidad no es elegibilidad: no autoriza
 * iniciar, reanudar ni repetir.
 */
public interface TitularidadIntentos {

    /**
     * @return la relación del estudiante con el intento; nunca {@code null}
     * @throws RuntimeException si el proveedor no está disponible: nunca se
     *     convierte en acceso
     */
    Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante);
}
