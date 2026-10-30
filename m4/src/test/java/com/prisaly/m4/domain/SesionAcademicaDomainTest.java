package com.prisaly.m4.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.domain.rule.SesionOperacionNoPermitidaException;

/**
 * Pruebas unitarias del dominio M4.
 * Cubren los escenarios M4-T01 a M4-T16 a nivel de reglas de dominio,
 * sin dependencias de Spring ni persistencia.
 */
class SesionAcademicaDomainTest {

    private static final UUID CASO_ID = UUID.randomUUID();
    private static final UUID DOCENTE_ID = UUID.randomUUID();
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 5, 9, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 11, 5, 11, 0);

    private SesionAcademica crearSesionBase() {
        return SesionAcademica.crear(CASO_ID, DOCENTE_ID,
                "Sesión de prueba", "Descripción", INICIO, FIN);
    }

    // ═══════════════════════════════════════════════════════════
    // M4-T01: Crear sesión (validaciones de dominio)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Creación de sesiones")
    class CreacionTest {

        @Test
        @DisplayName("M4-T01: Crear sesión con datos válidos produce estado PROGRAMADA")
        void crearSesionValida() {
            SesionAcademica sesion = crearSesionBase();

            assertNotNull(sesion.getId());
            assertEquals(CASO_ID, sesion.getCasoId());
            assertEquals(DOCENTE_ID, sesion.getDocenteId());
            assertEquals("Sesión de prueba", sesion.getTitulo());
            assertEquals(EstadoSesion.PROGRAMADA, sesion.getEstado());
            assertNotNull(sesion.getCreadoEn());
        }

        @Test
        @DisplayName("M4-T16: Rechazar fechas inconsistentes — fin antes de inicio")
        void rechazarFechasInvalidas() {
            LocalDateTime finAnterior = INICIO.minusHours(1);

            ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                    () -> SesionAcademica.crear(CASO_ID, DOCENTE_ID,
                            "Test", "Desc", INICIO, finAnterior));

            assertEquals("M4-FECHA-INVALIDA", ex.getCodigo());
        }

        @Test
        @DisplayName("M4-T16: Rechazar fechas iguales")
        void rechazarFechasIguales() {
            assertThrows(ReglaNegocioException.class,
                    () -> SesionAcademica.crear(CASO_ID, DOCENTE_ID,
                            "Test", "Desc", INICIO, INICIO));
        }

        @Test
        @DisplayName("Rechazar casoId nulo")
        void rechazarCasoIdNulo() {
            assertThrows(NullPointerException.class,
                    () -> SesionAcademica.crear(null, DOCENTE_ID,
                            "Test", "Desc", INICIO, FIN));
        }

        @Test
        @DisplayName("Rechazar docenteId nulo")
        void rechazarDocenteIdNulo() {
            assertThrows(NullPointerException.class,
                    () -> SesionAcademica.crear(CASO_ID, null,
                            "Test", "Desc", INICIO, FIN));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Patrón State — transiciones de estado
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transiciones de estado (patrón State)")
    class TransicionesStateTest {

        @Test
        @DisplayName("PROGRAMADA → ABIERTA")
        void programadaAAbierta() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            assertEquals(EstadoSesion.ABIERTA, sesion.getEstado());
        }

        @Test
        @DisplayName("PROGRAMADA → CANCELADA")
        void programadaACancelada() {
            SesionAcademica sesion = crearSesionBase();
            sesion.cancelar();
            assertEquals(EstadoSesion.CANCELADA, sesion.getEstado());
        }

        @Test
        @DisplayName("PROGRAMADA no puede iniciar directamente")
        void programadaNoIniciar() {
            SesionAcademica sesion = crearSesionBase();
            assertThrows(SesionOperacionNoPermitidaException.class, sesion::iniciar);
        }

        @Test
        @DisplayName("PROGRAMADA no puede cerrar directamente")
        void programadaNoCerrar() {
            SesionAcademica sesion = crearSesionBase();
            assertThrows(SesionOperacionNoPermitidaException.class, sesion::cerrar);
        }

        @Test
        @DisplayName("ABIERTA → EN_CURSO")
        void abiertaAEnCurso() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();
            assertEquals(EstadoSesion.EN_CURSO, sesion.getEstado());
        }

        @Test
        @DisplayName("ABIERTA → CANCELADA")
        void abiertaACancelada() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.cancelar();
            assertEquals(EstadoSesion.CANCELADA, sesion.getEstado());
        }

        @Test
        @DisplayName("ABIERTA no puede abrir de nuevo")
        void abiertaNoAbrir() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            assertThrows(SesionOperacionNoPermitidaException.class, sesion::abrir);
        }

        @Test
        @DisplayName("EN_CURSO → CERRADA")
        void enCursoACerrada() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();
            sesion.cerrar();
            assertEquals(EstadoSesion.CERRADA, sesion.getEstado());
        }

        @Test
        @DisplayName("EN_CURSO → CANCELADA")
        void enCursoACancelada() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();
            sesion.cancelar();
            assertEquals(EstadoSesion.CANCELADA, sesion.getEstado());
        }

        @Test
        @DisplayName("CERRADA no acepta ninguna transición")
        void cerradaEsTerminal() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();
            sesion.cerrar();

            assertAll(
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::abrir),
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::iniciar),
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::cerrar),
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::cancelar)
            );
        }

        @Test
        @DisplayName("CANCELADA no acepta ninguna transición")
        void canceladaEsTerminal() {
            SesionAcademica sesion = crearSesionBase();
            sesion.cancelar();

            assertAll(
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::abrir),
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::iniciar),
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::cerrar),
                    () -> assertThrows(SesionOperacionNoPermitidaException.class, sesion::cancelar)
            );
        }

        @Test
        @DisplayName("Flujo completo: PROGRAMADA → ABIERTA → EN_CURSO → CERRADA")
        void flujoCompleto() {
            SesionAcademica sesion = crearSesionBase();
            assertEquals(EstadoSesion.PROGRAMADA, sesion.getEstado());

            sesion.abrir();
            assertEquals(EstadoSesion.ABIERTA, sesion.getEstado());

            sesion.iniciar();
            assertEquals(EstadoSesion.EN_CURSO, sesion.getEstado());

            sesion.cerrar();
            assertEquals(EstadoSesion.CERRADA, sesion.getEstado());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Modificación y reglas de protección
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reglas de modificación")
    class ModificacionTest {

        @Test
        @DisplayName("Permitir modificación en estado PROGRAMADA")
        void modificarEnProgramada() {
            SesionAcademica sesion = crearSesionBase();
            LocalDateTime nuevoInicio = INICIO.plusDays(1);
            LocalDateTime nuevoFin = FIN.plusDays(1);

            assertDoesNotThrow(() -> sesion.actualizarProgramacion(
                    "Nuevo título", "Nueva desc", nuevoInicio, nuevoFin));

            assertEquals("Nuevo título", sesion.getTitulo());
        }

        @Test
        @DisplayName("Permitir modificación en estado ABIERTA")
        void modificarEnAbierta() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();

            assertDoesNotThrow(() -> sesion.actualizarProgramacion(
                    "Actualizado", "Desc", INICIO.plusDays(2), FIN.plusDays(2)));
        }

        @Test
        @DisplayName("M4-T09: Rechazar modificación en estado EN_CURSO")
        void noModificarEnCurso() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();

            assertThrows(SesionOperacionNoPermitidaException.class,
                    () -> sesion.actualizarProgramacion(
                            "No debería", "cambiar", INICIO, FIN));
        }

        @Test
        @DisplayName("Rechazar modificación en estado CERRADA")
        void noModificarCerrada() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();
            sesion.cerrar();

            assertThrows(SesionOperacionNoPermitidaException.class,
                    () -> sesion.actualizarProgramacion(
                            "No debería", "cambiar", INICIO, FIN));
        }

        @Test
        @DisplayName("Rechazar modificación en estado CANCELADA")
        void noModificarCancelada() {
            SesionAcademica sesion = crearSesionBase();
            sesion.cancelar();

            assertThrows(SesionOperacionNoPermitidaException.class,
                    () -> sesion.actualizarProgramacion(
                            "No debería", "cambiar", INICIO, FIN));
        }

        @Test
        @DisplayName("M4-T10: Cambiar caso en estado PROGRAMADA (permitido)")
        void cambiarCasoEnProgramada() {
            SesionAcademica sesion = crearSesionBase();
            UUID nuevoCasoId = UUID.randomUUID();

            sesion.cambiarCaso(nuevoCasoId);
            assertEquals(nuevoCasoId, sesion.getCasoId());
        }

        @Test
        @DisplayName("M4-T11: Rechazar cambio de caso en EN_CURSO")
        void noCambiarCasoEnCurso() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();

            assertThrows(SesionOperacionNoPermitidaException.class,
                    () -> sesion.cambiarCaso(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Convocatorias — reglas de dominio
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reglas de convocatoria")
    class ConvocatoriaTest {

        @Test
        @DisplayName("PROGRAMADA no permite convocatorias")
        void programadaNoConvoca() {
            SesionAcademica sesion = crearSesionBase();
            assertFalse(sesion.permiteConvocatoria());
        }

        @Test
        @DisplayName("ABIERTA permite convocatorias")
        void abiertaSiConvoca() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            assertTrue(sesion.permiteConvocatoria());
        }

        @Test
        @DisplayName("EN_CURSO no permite convocatorias")
        void enCursoNoConvoca() {
            SesionAcademica sesion = crearSesionBase();
            sesion.abrir();
            sesion.iniciar();
            assertFalse(sesion.permiteConvocatoria());
        }

        @Test
        @DisplayName("M4-T06: Crear convocatoria válida")
        void crearConvocatoriaValida() {
            UUID sesionId = UUID.randomUUID();
            UUID estudianteId = UUID.randomUUID();

            Convocatoria c = Convocatoria.crear(sesionId, estudianteId);

            assertNotNull(c.getId());
            assertEquals(sesionId, c.getSesionId());
            assertEquals(estudianteId, c.getEstudianteId());
            assertNotNull(c.getConvocadoEn());
            assertFalse(c.isConfirmada());
        }

        @Test
        @DisplayName("Confirmar convocatoria")
        void confirmarConvocatoria() {
            Convocatoria c = Convocatoria.crear(UUID.randomUUID(), UUID.randomUUID());
            c.confirmar();
            assertTrue(c.isConfirmada());
        }

        @Test
        @DisplayName("Rechazar convocatoria con sesionId nulo")
        void rechazarSesionIdNulo() {
            assertThrows(NullPointerException.class,
                    () -> Convocatoria.crear(null, UUID.randomUUID()));
        }

        @Test
        @DisplayName("Rechazar convocatoria con estudianteId nulo")
        void rechazarEstudianteIdNulo() {
            assertThrows(NullPointerException.class,
                    () -> Convocatoria.crear(UUID.randomUUID(), null));
        }
    }
}
