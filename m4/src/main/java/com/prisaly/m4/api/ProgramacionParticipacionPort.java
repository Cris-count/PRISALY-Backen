package com.prisaly.m4.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta interna M4 para M5, sin entidades JPA ni llamadas HTTP. El estudiante debe provenir del
 * contexto autenticado M1 (idEstudiante, no idUsuario ni un identificador aceptado sin validación
 * desde el cliente). Una consulta exitosa demuestra titularidad de la convocatoria, NO autoriza
 * iniciar/reanudar un intento ni valida autorizaciones de repetición.
 *
 * <p>M5 resuelve Participacion.idConvocatoria mediante sus propios contratos y llama
 * consultarConvocatoria. M4 no consulta Participacion ni Intento. El resultado es una instantánea,
 * no una reserva ni un bloqueo concurrente. Para inicio coordinado se utiliza {@link
 * ProgramacionInicioCoordinadoPort}.
 */
public interface ProgramacionParticipacionPort {
    Optional<ContextoProgramacion> consultarSesion(UUID sesionId, UUID estudianteAutenticadoId);

    /** Vacío tanto para convocatoria inexistente como para titular distinto. */
    Optional<ContextoProgramacion> consultarConvocatoria(
            UUID convocatoriaId, UUID estudianteAutenticadoId);

    /**
     * DC-BE-CIERRE-FUNCIONAL-09, fase 2: todas las convocatorias cuyo titular es el estudiante autenticado, en
     * orden estable ({@code inicioPrevisto}, {@code idConvocatoria}). Solo las propias: el filtro por titular lo
     * aplica la consulta, nunca el cliente. Las implementaciones que no lo soportan fallan cerradas.
     */
    default List<ContextoProgramacion> listarConvocatoriasDelEstudiante(UUID estudianteAutenticadoId) {
        throw new ConsultaProgramacionNoDisponibleException();
    }
}
