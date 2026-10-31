package com.prisaly.m4.application.service;

import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de aplicación que coordina los casos de uso de M4.
 *
 * <p>Orquesta la validación de permisos (M1), la verificación de casos publicados (M3), las reglas
 * de dominio y la persistencia.
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

    public SesionAcademicaService(
            SesionRepository sesionRepository,
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

    @Transactional(readOnly = true)
    public UUID resolverDocenteAutenticado(UUID identidadDeclarada) {
        UUID actual = identidadPort.docenteActual();
        if (identidadDeclarada != null && !actual.equals(identidadDeclarada)) {
            throw new ReglaNegocioException(
                    "M4-SIN-PERMISO", "La identidad declarada no coincide con la autenticada");
        }
        return actual;
    }

    @Transactional(readOnly = true)
    public SesionAcademica consultarSesionAutorizada(UUID sesionId) {
        UUID actual = resolverDocenteAutenticado(null);
        verificarPermiso(actual);
        var sesion = consultarSesion(sesionId);
        verificarPropiedad(sesion, actual);
        return sesion;
    }

    @Transactional(readOnly = true)
    public List<SesionAcademica> listarSesionesAutorizadas(UUID docenteDeclarado) {
        UUID actual = resolverDocenteAutenticado(docenteDeclarado);
        verificarPermiso(actual);
        return sesionRepository.listarPorDocente(actual);
    }

    @Transactional(readOnly = true)
    public List<Convocatoria> consultarConvocatoriasAutorizadas(UUID sesionId) {
        consultarSesionAutorizada(sesionId);
        return convocatoriaRepository.listarPorSesion(sesionId);
    }

    /**
     * Crea una sesión académica verificando permisos, publicación del caso, duplicación y
     * solicitando bloqueo F3-03 si es la primera sesión del caso.
     */
    public SesionAcademica crearSesion(
            UUID docenteId,
            UUID casoId,
            String titulo,
            String descripcion,
            Instant fechaInicio,
            Instant fechaFin,
            int tiempoMaximoParticipacionSegundos) {

        // 1. Verificar permiso del docente (M1)
        verificarPermiso(docenteId);

        sesionRepository.protegerProgramacion(casoId, docenteId);

        // 2. Metadatos persistidos; V009 revalida y serializa al insertar Sesion
        if (!casoAcademicoPort.estaPublicado(casoId)) {
            throw new ReglaNegocioException(
                    "M4-CASO-NO-PUBLICADO", "El caso no existe o no está en estado Publicado");
        }

        // 3. Evitar duplicación
        if (sesionRepository.existeDuplicada(casoId, docenteId, fechaInicio, fechaFin)) {
            throw new ReglaNegocioException(
                    "M4-SESION-DUPLICADA",
                    "Ya existe una sesión con el mismo caso, docente y rango de fechas");
        }

        // 4. Determinar si es la primera sesión del caso → bloqueo F3-03
        boolean primeraSesionDelCaso = !sesionRepository.existeSesionParaCaso(casoId);

        // 5. Crear la sesión (reglas de dominio)
        SesionAcademica sesion =
                SesionAcademica.crear(
                        casoId,
                        docenteId,
                        titulo,
                        descripcion,
                        fechaInicio,
                        fechaFin,
                        tiempoMaximoParticipacionSegundos);

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
        return sesionRepository
                .buscarPorId(sesionId)
                .orElseThrow(
                        () ->
                                new ReglaNegocioException(
                                        "M4-SESION-NO-ENCONTRADA",
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

    public SesionAcademica actualizarSesion(
            UUID docenteId,
            UUID sesionId,
            String nuevoTitulo,
            String nuevaDescripcion,
            Instant nuevaFechaInicio,
            Instant nuevaFechaFin) {
        verificarPermiso(docenteId);

        SesionAcademica sesion =
                sesionRepository
                        .buscarParaActualizar(sesionId)
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "M4-SESION-NO-ENCONTRADA", "Sesión no encontrada"));
        verificarPropiedad(sesion, docenteId);

        // Proteger sesiones con participaciones vinculadas
        if (sesionRepository.tieneParticipacionesVinculadas(sesionId)) {
            throw new ReglaNegocioException(
                    "M4-SESION-CON-PARTICIPACIONES",
                    "No se puede modificar una sesión que ya tiene participaciones vinculadas");
        }

        sesionRepository.protegerProgramacion(sesion.getCasoId(), docenteId);
        if (!casoAcademicoPort.estaPublicado(sesion.getCasoId()))
            throw new ReglaNegocioException(
                    "M4-CASO-NO-PUBLICADO", "El caso no está Publicado y activo");
        if (sesionRepository.existeDuplicadaExcluyendo(
                sesionId, sesion.getCasoId(), docenteId, nuevaFechaInicio, nuevaFechaFin))
            throw new ReglaNegocioException("M4-SESION-DUPLICADA", "Ya existe esa programación");
        sesion.actualizarProgramacion(
                nuevoTitulo, nuevaDescripcion, nuevaFechaInicio, nuevaFechaFin);

        return sesionRepository.guardar(sesion);
    }

    // ─── CambiarCaso ──────────────────────────────────────────

    public SesionAcademica cambiarCaso(UUID docenteId, UUID sesionId, UUID nuevoCasoId) {
        verificarPermiso(docenteId);

        SesionAcademica sesion =
                sesionRepository
                        .buscarParaActualizar(sesionId)
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "M4-SESION-NO-ENCONTRADA", "Sesión no encontrada"));
        verificarPropiedad(sesion, docenteId);

        if (sesionRepository.tieneParticipacionesVinculadas(sesionId)) {
            throw new ReglaNegocioException(
                    "M4-SESION-CON-PARTICIPACIONES",
                    "No se puede reasignar el caso de una sesión con historial académico");
        }
        // BD-005.2 V005/V006 protege el origen desde la primera convocatoria,
        // incluso si todavía no existe participación ni intento.
        if (!convocatoriaRepository.listarPorSesion(sesionId).isEmpty()) {
            throw new ReglaNegocioException(
                    "M4-SESION-CON-CONVOCATORIAS",
                    "No se puede reasignar el caso de una sesión que ya tiene convocatorias");
        }

        sesionRepository.protegerProgramacion(nuevoCasoId, docenteId);
        if (sesionRepository.existeDuplicadaExcluyendo(
                sesionId, nuevoCasoId, docenteId, sesion.getFechaInicio(), sesion.getFechaFin()))
            throw new ReglaNegocioException(
                    "M4-SESION-DUPLICADA", "Ya existe esa programación en el caso destino");
        // Verificar que el nuevo caso está Publicado
        if (!casoAcademicoPort.estaPublicado(nuevoCasoId)) {
            throw new ReglaNegocioException(
                    "M4-CASO-NO-PUBLICADO",
                    "El nuevo caso no existe o no está en estado Publicado");
        }

        boolean primeraSesionDelDestino = !sesionRepository.existeSesionParaCaso(nuevoCasoId);
        sesion.cambiarCaso(nuevoCasoId);
        sesion = sesionRepository.guardar(sesion);
        if (primeraSesionDelDestino) {
            casoAcademicoPort.solicitarBloqueoAcademico(nuevoCasoId);
        }
        return sesion;
    }

    // ─── CambiarEstadoSesion ──────────────────────────────────

    public SesionAcademica cambiarEstado(UUID docenteId, UUID sesionId, EstadoSesion nuevoEstado) {
        verificarPermiso(docenteId);

        SesionAcademica sesion =
                sesionRepository
                        .buscarParaActualizar(sesionId)
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "M4-SESION-NO-ENCONTRADA", "Sesión no encontrada"));
        verificarPropiedad(sesion, docenteId);

        switch (nuevoEstado) {
            case EN_CURSO -> sesion.iniciar();
            case FINALIZADO -> sesion.cerrar();
            default ->
                    throw new ReglaNegocioException(
                            "M4-ESTADO-INVALIDO",
                            "No se puede transicionar directamente a " + nuevoEstado);
        }

        return sesionRepository.guardar(sesion);
    }

    // ─── ConvocarEstudiantes ──────────────────────────────────

    public Convocatoria convocarEstudiante(UUID docenteId, UUID sesionId, UUID estudianteId) {
        verificarPermiso(docenteId);

        SesionAcademica sesion =
                sesionRepository
                        .buscarParaActualizar(sesionId)
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "M4-SESION-NO-ENCONTRADA", "Sesión no encontrada"));
        verificarPropiedad(sesion, docenteId);

        if (!sesion.permiteConvocatoria()) {
            throw new ReglaNegocioException(
                    "M4-CONVOCATORIA-NO-PERMITIDA",
                    "La sesión no acepta convocatorias en su estado actual: " + sesion.getEstado());
        }

        // Evitar convocatoria duplicada
        if (convocatoriaRepository.existeConvocatoria(sesionId, estudianteId)) {
            throw new ReglaNegocioException(
                    "M4-CONVOCATORIA-DUPLICADA", "El estudiante ya está convocado a esta sesión");
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
     * Operación heredada retirada. Nunca autoriza acceso o repetición de intentos.
     *
     * @deprecated Usar com.prisaly.m4.api.ProgramacionParticipacionPort para consultar hechos; la
     *     política académica de elegibilidad sigue pendiente.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public boolean verificarAccesoSesion(UUID sesionId, UUID estudianteId) {
        throw new ReglaNegocioException(
                "M4-ACCESO-LEGADO-RETIRADO",
                "Use los contratos informativos y una política aprobada");
    }

    // ─── Métodos privados ─────────────────────────────────────

    private void verificarPermiso(UUID docenteId) {
        if (!identidadPort.tienePermiso(docenteId, PERMISO_GESTIONAR_SESION)) {
            throw new ReglaNegocioException(
                    "M4-SIN-PERMISO",
                    "El docente no tiene permiso para gestionar sesiones académicas");
        }
    }

    private void verificarPropiedad(SesionAcademica sesion, UUID docenteId) {
        if (!sesion.getDocenteId().equals(docenteId)) {
            throw new ReglaNegocioException(
                    "M4-SESION-AJENA", "El docente no es propietario de esta sesión");
        }
    }
}
