package com.prisaly.m5.application.acceso;

import java.util.Optional;

import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ReferenciaSesion;

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

    /**
     * Relación del estudiante con una convocatoria de M4. Una convocatoria
     * inexistente y una ajena pueden ser indistinguibles: M4 no revela
     * convocatorias de otros estudiantes.
     *
     * <p>Por defecto deniega ({@link Titularidad#AJENO}): una implementación
     * que no consulta M4 nunca da acceso para iniciar o reanudar.
     *
     * @return la relación; nunca {@code null}
     * @throws RuntimeException si el proveedor no está disponible: nunca se
     *     convierte en acceso
     */
    default Titularidad deConvocatoria(ReferenciaConvocatoria convocatoria, IdentificadorEstudiante estudiante) {
        return Titularidad.AJENO;
    }

    /**
     * Convocatoria del estudiante en una sesión de M4 (D01). M4 resuelve una única convocatoria por
     * sesión y estudiante; si encuentra varias responde indisponibilidad, que se propaga.
     *
     * <p>Por defecto deniega (vacío): una implementación que no consulta M4 nunca resuelve una
     * convocatoria para iniciar o reanudar.
     *
     * @return la convocatoria propia; vacío si no existe o no es del estudiante
     * @throws RuntimeException si el proveedor no está disponible: nunca se convierte en acceso
     */
    default Optional<ReferenciaConvocatoria> convocatoriaEnSesion(ReferenciaSesion sesion, IdentificadorEstudiante estudiante) {
        return Optional.empty();
    }
}
