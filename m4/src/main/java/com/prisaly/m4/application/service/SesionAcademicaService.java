package com.prisaly.m4.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

/**
 * Servicio de aplicación que coordina los casos de uso de M4.
 *
 * <p>Orquesta la validación de permisos (M1), la verificación de casos
 * publicados (M3), las reglas de dominio y la persistencia.</p>
 */
@Service
@Transactional
public class SesionAcademicaService {

    private static final String PERMISO_GESTIONAR_SESION = "GESTIONAR_SESION";

    private final SesionRepository sesionRepository;
    private final ConvocatoriaRepository convocatoriaRepository;
    private final IdentidadPort identidadPort;
    private final CasoAcademicoPort casoAcademicoPort;
    private final NotificacionPort notificacionPort;

    public SesionAcademicaService(SesionRepository sesionRepository,
                                   ConvocatoriaRepository convocatoriaRepository,
                                   IdentidadPort identidadPort,
                                   CasoAcademicoPort casoAcademicoPort,
                                   NotificacionPort notificacionPort) {
        this.sesionRepository = sesionRepository;
        this.convocatoriaRepository = convocatoriaRepository;
        this.identidadPort = identidadPort;
        this.casoAcademicoPort = casoAcademicoPort;
        this.notificacionPort = notificacionPort;
    }

    // ─── CrearSesion ──────────────────────────────────────────

    /**
     * Crea una sesión académica verificando permisos, publicación del caso,
     * duplicación y solicitando bloqueo F3-03 si es la primera sesión del caso.
     */
    public SesionAcademica crearSesion(UUID docenteId,
                                        UUID casoId,
                                        String titulo,
                                        String descripcion,
                                        LocalDateTime fechaInicio,
                                        LocalDateTime fechaFin) {

        // 1. Verificar permiso del docente (M1)
        verificarPermiso(docenteId);

        // 2. Verificar que el caso existe y está Publicado (M3)
        if (!casoAcademicoPort.estaPublicado(casoId)) {
            throw new ReglaNegocioException("M4-CASO-NO-PUBLICADO",
                    "El caso no existe o no está en estado Publicado");
        }

        // 3. Evitar duplicación
        if (sesionRepository.existeDuplicada(casoId, docenteId, fechaInicio, fechaFin)) {
            throw new ReglaNegocioException("M4-SESION-DUPLICADA",
                    "Ya existe una sesión con el mismo caso, docente y rango de fechas");
        }

        // 4. Determinar si es la primera sesión del caso → bloqueo F3-03
        boolean primeraSesionDelCaso = !sesionRepository.existeSesionParaCaso(casoId);

        // 5. Crear la sesión (reglas de dominio)
        SesionAcademica sesion = SesionAcademica.crear(
                casoId, docenteId, titulo, descripcion, fechaInicio, fechaFin);

        // 6. Persistir
        sesion = sesionRepository.guardar(sesion);

        // 7. Si es la primera sesión, solicitar bloqueo académico a M3
        if (primeraSesionDelCaso) {
            casoAcademicoPort.solicitarBloqueoAcademico(casoId);
        }

        return sesion;
    }

    // ─── ConsultarSesion ──────────────────────────────────────

    @Transactional(readOnly = true)
    public SesionAcademica consultarSesion(UUID sesionId) {
        return sesionRepository.buscarPorId(sesionId)
                .orElseThrow(() -> new ReglaNegocioException("M4-SESION-NO-ENCONTRADA",
                        "Sesión académica no encontrada: " + sesionId));
    }

    // ─── ListarSesiones ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SesionAcademica> listarSesiones(UUID docenteId) {
        if (docenteId != null) {
            return sesionRepository.listarPorDocente(docenteId);
        }
        return sesionRepository.listarTodas();
    }

    // ─── ActualizarSesion ─────────────────────────────────────

    public SesionAcademica actualizarSesion(UUID docenteId,
                                             UUID sesionId,
                                             String nuevoTitulo,
                                             String nuevaDescripcion,
                                             LocalDateTime nuevaFechaInicio,
                                             LocalDateTime nuevaFechaFin) {
        verificarPermiso(docenteId);

        SesionAcademica sesion = consultarSesion(sesionId);
        verificarPropiedad(sesion, docenteId);

        // Proteger sesiones con participaciones vinculadas
        if (sesionRepository.tieneParticipacionesVinculadas(sesionId)) {
            throw new ReglaNegocioException("M4-SESION-CON-PARTICIPACIONES",
                    "No se puede modificar una sesión que ya tiene participaciones vinculadas");
        }

        sesion.actualizarProgramacion(nuevoTitulo, nuevaDescripcion,
                nuevaFechaInicio, nuevaFechaFin);

        return sesionRepository.guardar(sesion);
    }

    // ─── CambiarCaso ──────────────────────────────────────────

    public SesionAcademica cambiarCaso(UUID docenteId,
                                        UUID sesionId,
                                        UUID nuevoCasoId) {
        verificarPermiso(docenteId);

        SesionAcademica sesion = consultarSesion(sesionId);
        verificarPropiedad(sesion, docenteId);

        // Verificar que el nuevo caso está Publicado
        if (!casoAcademicoPort.estaPublicado(nuevoCasoId)) {
            throw new ReglaNegocioException("M4-CASO-NO-PUBLICADO",
                    "El nuevo caso no existe o no está en estado Publicado");
        }

        sesion.cambiarCaso(nuevoCasoId);
        return sesionRepository.guardar(sesion);
    }

    // ─── CambiarEstadoSesion ──────────────────────────────────

    public SesionAcademica cambiarEstado(UUID docenteId,
                                          UUID sesionId,
                                          EstadoSesion nuevoEstado) {
        verificarPermiso(docenteId);

        SesionAcademica sesion = consultarSesion(sesionId);
        verificarPropiedad(sesion, docenteId);

        switch (nuevoEstado) {
            case ABIERTA    -> sesion.abrir();
            case EN_CURSO   -> sesion.iniciar();
            case CERRADA    -> sesion.cerrar();
            case CANCELADA  -> sesion.cancelar();
            default -> throw new ReglaNegocioException("M4-ESTADO-INVALIDO",
                    "No se puede transicionar directamente a " + nuevoEstado);
        }

        return sesionRepository.guardar(sesion);
    }

    // ─── ConvocarEstudiantes ──────────────────────────────────

    public Convocatoria convocarEstudiante(UUID docenteId,
                                            UUID sesionId,
                                            UUID estudianteId) {
        verificarPermiso(docenteId);

        SesionAcademica sesion = consultarSesion(sesionId);
        verificarPropiedad(sesion, docenteId);

        if (!sesion.permiteConvocatoria()) {
            throw new ReglaNegocioException("M4-CONVOCATORIA-NO-PERMITIDA",
                    "La sesión no acepta convocatorias en su estado actual: "
                            + sesion.getEstado());
        }

        // Evitar convocatoria duplicada
        if (convocatoriaRepository.existeConvocatoria(sesionId, estudianteId)) {
            throw new ReglaNegocioException("M4-CONVOCATORIA-DUPLICADA",
                    "El estudiante ya está convocado a esta sesión");
        }

        Convocatoria convocatoria = Convocatoria.crear(sesionId, estudianteId);
        convocatoria = convocatoriaRepository.guardar(convocatoria);

        // Notificar a M7
        notificacionPort.notificarConvocatoria(sesionId, estudianteId);

        return convocatoria;
    }

    // ─── ConsultarConvocatorias ───────────────────────────────

    @Transactional(readOnly = true)
    public List<Convocatoria> consultarConvocatorias(UUID sesionId) {
        // Verificar que la sesión existe
        consultarSesion(sesionId);
        return convocatoriaRepository.listarPorSesion(sesionId);
    }

    // ─── VerificarAccesoSesion (expuesto para M5) ─────────────

    /**
     * Verifica si un estudiante puede acceder a una sesión.
     * Condiciones: sesión existe, está ABIERTA o EN_CURSO, y el estudiante
     * está convocado. M4 no autoriza repeticiones de intentos (eso es de M5).
     */
    @Transactional(readOnly = true)
    public boolean verificarAccesoSesion(UUID sesionId, UUID estudianteId) {
        var sesionOpt = sesionRepository.buscarPorId(sesionId);
        if (sesionOpt.isEmpty()) {
            return false;
        }

        SesionAcademica sesion = sesionOpt.get();
        EstadoSesion estado = sesion.getEstado();

        // Solo se puede acceder a sesiones abiertas o en curso
        if (estado != EstadoSesion.ABIERTA && estado != EstadoSesion.EN_CURSO) {
            return false;
        }

        // Verificar que el estudiante está convocado
        return convocatoriaRepository.estaConvocado(sesionId, estudianteId);
    }

    // ─── Métodos privados ─────────────────────────────────────

    private void verificarPermiso(UUID docenteId) {
        if (!identidadPort.tienePermiso(docenteId, PERMISO_GESTIONAR_SESION)) {
            throw new ReglaNegocioException("M4-SIN-PERMISO",
                    "El docente no tiene permiso para gestionar sesiones académicas");
        }
    }

    private void verificarPropiedad(SesionAcademica sesion, UUID docenteId) {
        if (!sesion.getDocenteId().equals(docenteId)) {
            throw new ReglaNegocioException("M4-SESION-AJENA",
                    "El docente no es propietario de esta sesión");
        }
    }
}
