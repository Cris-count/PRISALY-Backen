package com.prisaly.m4.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;

/**
 * Pruebas unitarias del servicio de aplicación M4.
 * Usa mocks para aislar la lógica de orquestación.
 */
@ExtendWith(MockitoExtension.class)
class SesionAcademicaServiceTest {

    @Mock private SesionRepository sesionRepository;
    @Mock private ConvocatoriaRepository convocatoriaRepository;
    @Mock private IdentidadPort identidadPort;
    @Mock private CasoAcademicoPort casoAcademicoPort;
    @Mock private NotificacionPort notificacionPort;

    @InjectMocks
    private SesionAcademicaService service;

    private static final UUID DOCENTE_ID = UUID.randomUUID();
    private static final UUID CASO_ID = UUID.randomUUID();
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 5, 9, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 11, 5, 11, 0);

    @BeforeEach
    void setUp() {
        // Por defecto: docente con permiso, caso publicado
        lenient().when(identidadPort.tienePermiso(any(), any())).thenReturn(true);
        lenient().when(casoAcademicoPort.estaPublicado(any())).thenReturn(true);
        lenient().when(sesionRepository.existeDuplicada(any(), any(), any(), any())).thenReturn(false);
        lenient().when(sesionRepository.existeSesionParaCaso(any())).thenReturn(false);
        lenient().when(sesionRepository.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(convocatoriaRepository.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CrearSesion")
    class CrearSesionTest {

        @Test
        @DisplayName("M4-T01: Crear sesión con caso Publicado — éxito")
        void crearSesionExito() {
            SesionAcademica resultado = service.crearSesion(
                    DOCENTE_ID, CASO_ID, "Sesión 1", "Desc", INICIO, FIN);

            assertNotNull(resultado);
            assertEquals(EstadoSesion.PROGRAMADA, resultado.getEstado());
            verify(sesionRepository).guardar(any());
            verify(casoAcademicoPort).solicitarBloqueoAcademico(CASO_ID);
        }

        @Test
        @DisplayName("M4-T01: Primera sesión del caso invoca bloqueo F3-03")
        void primeraSesionInvocaBloqueo() {
            when(sesionRepository.existeSesionParaCaso(CASO_ID)).thenReturn(false);

            service.crearSesion(DOCENTE_ID, CASO_ID, "Sesión 1", "Desc", INICIO, FIN);

            verify(casoAcademicoPort).solicitarBloqueoAcademico(CASO_ID);
        }

        @Test
        @DisplayName("Segunda sesión del caso NO invoca bloqueo F3-03")
        void segundaSesionNoBloquea() {
            when(sesionRepository.existeSesionParaCaso(CASO_ID)).thenReturn(true);

            service.crearSesion(DOCENTE_ID, CASO_ID, "Sesión 2", "Desc", INICIO, FIN);

            verify(casoAcademicoPort, never()).solicitarBloqueoAcademico(any());
        }

        @Test
        @DisplayName("M4-T02: Rechazar caso en Borrador (no publicado)")
        void rechazarCasoBorrador() {
            when(casoAcademicoPort.estaPublicado(CASO_ID)).thenReturn(false);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> service.crearSesion(DOCENTE_ID, CASO_ID, "Test", "Desc", INICIO, FIN));

            assertEquals("M4-CASO-NO-PUBLICADO", ex.getCodigo());
            verify(sesionRepository, never()).guardar(any());
        }

        @Test
        @DisplayName("M4-T03: Rechazar caso inexistente (mismo efecto que no publicado)")
        void rechazarCasoInexistente() {
            UUID casoInexistente = UUID.randomUUID();
            when(casoAcademicoPort.estaPublicado(casoInexistente)).thenReturn(false);

            assertThrows(ReglaNegocioException.class,
                    () -> service.crearSesion(DOCENTE_ID, casoInexistente, "Test", "Desc", INICIO, FIN));
        }

        @Test
        @DisplayName("M4-T04: Rechazar docente sin autorización")
        void rechazarDocenteSinPermiso() {
            when(identidadPort.tienePermiso(DOCENTE_ID, "GESTIONAR_SESION")).thenReturn(false);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> service.crearSesion(DOCENTE_ID, CASO_ID, "Test", "Desc", INICIO, FIN));

            assertEquals("M4-SIN-PERMISO", ex.getCodigo());
        }

        @Test
        @DisplayName("M4-T07: Rechazar duplicación")
        void rechazarDuplicacion() {
            when(sesionRepository.existeDuplicada(CASO_ID, DOCENTE_ID, INICIO, FIN)).thenReturn(true);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> service.crearSesion(DOCENTE_ID, CASO_ID, "Test", "Desc", INICIO, FIN));

            assertEquals("M4-SESION-DUPLICADA", ex.getCodigo());
        }
    }

    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Convocatorias")
    class ConvocatoriasTest {

        @Test
        @DisplayName("M4-T06: Registrar convocatoria válida")
        void convocarEstudianteExito() {
            UUID sesionId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionAbierta(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));
            when(convocatoriaRepository.existeConvocatoria(sesionId, estudianteId)).thenReturn(false);

            Convocatoria resultado = service.convocarEstudiante(DOCENTE_ID, sesionId, estudianteId);

            assertNotNull(resultado);
            verify(convocatoriaRepository).guardar(any());
            verify(notificacionPort).notificarConvocatoria(sesionId, estudianteId);
        }

        @Test
        @DisplayName("M4-T07: Rechazar convocatoria duplicada")
        void rechazarConvocatoriaDuplicada() {
            UUID sesionId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionAbierta(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));
            when(convocatoriaRepository.existeConvocatoria(sesionId, estudianteId)).thenReturn(true);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> service.convocarEstudiante(DOCENTE_ID, sesionId, estudianteId));

            assertEquals("M4-CONVOCATORIA-DUPLICADA", ex.getCodigo());
        }

        @Test
        @DisplayName("Rechazar convocatoria en sesión PROGRAMADA (no abierta)")
        void rechazarConvocatoriaEnProgramada() {
            UUID sesionId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionProgramada(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> service.convocarEstudiante(DOCENTE_ID, sesionId, UUID.randomUUID()));

            assertEquals("M4-CONVOCATORIA-NO-PERMITIDA", ex.getCodigo());
        }

        @Test
        @DisplayName("M4-T15: Notificación M7 se invoca al convocar")
        void notificacionM7SeInvoca() {
            UUID sesionId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionAbierta(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));
            when(convocatoriaRepository.existeConvocatoria(sesionId, estudianteId)).thenReturn(false);

            service.convocarEstudiante(DOCENTE_ID, sesionId, estudianteId);

            verify(notificacionPort).notificarConvocatoria(sesionId, estudianteId);
        }
    }

    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VerificarAcceso (para M5)")
    class VerificarAccesoTest {

        @Test
        @DisplayName("M4-T13: Rechazar acceso de estudiante no convocado")
        void rechazarEstudianteNoConvocado() {
            UUID sesionId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionAbierta(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));
            when(convocatoriaRepository.estaConvocado(sesionId, estudianteId)).thenReturn(false);

            assertFalse(service.verificarAccesoSesion(sesionId, estudianteId));
        }

        @Test
        @DisplayName("Permitir acceso de estudiante convocado en sesión ABIERTA")
        void permitirAccesoConvocado() {
            UUID sesionId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionAbierta(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));
            when(convocatoriaRepository.estaConvocado(sesionId, estudianteId)).thenReturn(true);

            assertTrue(service.verificarAccesoSesion(sesionId, estudianteId));
        }

        @Test
        @DisplayName("Rechazar acceso en sesión CERRADA")
        void rechazarAccesoSesionCerrada() {
            UUID sesionId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionCerrada(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));

            assertFalse(service.verificarAccesoSesion(sesionId, UUID.randomUUID()));
        }

        @Test
        @DisplayName("M4-T14: Sesión inexistente retorna false")
        void sesionInexistenteRetornaFalse() {
            UUID sesionId = UUID.randomUUID();
            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.empty());

            assertFalse(service.verificarAccesoSesion(sesionId, UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CambiarEstado")
    class CambiarEstadoTest {

        @Test
        @DisplayName("Cambiar de PROGRAMADA a ABIERTA")
        void cambiarAbierta() {
            UUID sesionId = UUID.randomUUID();
            SesionAcademica sesion = crearSesionProgramada(sesionId);
            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));

            SesionAcademica resultado = service.cambiarEstado(DOCENTE_ID, sesionId, EstadoSesion.ABIERTA);

            assertEquals(EstadoSesion.ABIERTA, resultado.getEstado());
        }

        @Test
        @DisplayName("M4-T11: Rechazar cambio a caso Borrador en actualizarCaso")
        void rechazarCambioCasoBorrador() {
            UUID sesionId = UUID.randomUUID();
            UUID nuevoCaso = UUID.randomUUID();
            SesionAcademica sesion = crearSesionProgramada(sesionId);

            when(sesionRepository.buscarPorId(sesionId)).thenReturn(Optional.of(sesion));
            when(casoAcademicoPort.estaPublicado(nuevoCaso)).thenReturn(false);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> service.cambiarCaso(DOCENTE_ID, sesionId, nuevoCaso));

            assertEquals("M4-CASO-NO-PUBLICADO", ex.getCodigo());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private SesionAcademica crearSesionProgramada(UUID sesionId) {
        SesionAcademica sesion = SesionAcademica.crear(CASO_ID, DOCENTE_ID, "Test", "Desc", INICIO, FIN);
        sesion.setId(sesionId);
        return sesion;
    }

    private SesionAcademica crearSesionAbierta(UUID sesionId) {
        SesionAcademica sesion = crearSesionProgramada(sesionId);
        sesion.abrir();
        return sesion;
    }

    private SesionAcademica crearSesionCerrada(UUID sesionId) {
        SesionAcademica sesion = crearSesionAbierta(sesionId);
        sesion.iniciar();
        sesion.cerrar();
        return sesion;
    }
}
