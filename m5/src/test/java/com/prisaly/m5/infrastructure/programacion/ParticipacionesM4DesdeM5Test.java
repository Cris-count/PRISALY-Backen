package com.prisaly.m5.infrastructure.programacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m4.api.ConvocatoriasSesionPort;
import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m5.api.HistorialParticipacionPort;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica del proveedor con dobles de M4 y del historial. La integración real con PostgreSQL y el
 * adaptador JPA de M4 está en {@code ParticipacionesM4DesdeM5IT}.
 */
@DisplayName("ParticipacionesM4DesdeM5 (proveedor de ParticipacionesPort de M4)")
class ParticipacionesM4DesdeM5Test {

    // Solo simula el contexto transaccional para la prueba unitaria; las IT usan PostgreSQL.
    @BeforeEach
    void transaccionDePrueba() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void limpiarTransaccionDePrueba() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void sinTransaccionOSoloLecturaNoConsultaHistorial() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThrows(
                IllegalStateException.class, () -> proveedor.tieneParticipacionesOIntentos(SESION));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(
                IllegalStateException.class, () -> proveedor.tieneParticipacionesOIntentos(SESION));
        assertTrue(consultadas.isEmpty());
    }

    private static final UUID SESION = UUID.randomUUID();
    private static final UUID CONVOCATORIA_A = UUID.randomUUID();
    private static final UUID CONVOCATORIA_B = UUID.randomUUID();

    private List<UUID> deLaSesion = List.of(CONVOCATORIA_A, CONVOCATORIA_B);
    private final List<Collection<UUID>> consultadas = new ArrayList<>();
    private boolean hayHistorial = true;
    private RuntimeException falloHistorial;

    private final ConvocatoriasSesionPort m4 =
            sesionId -> SESION.equals(sesionId) ? deLaSesion : List.of();

    private final HistorialParticipacionPort historial =
            convocatorias -> {
                consultadas.add(List.copyOf(convocatorias));
                if (falloHistorial != null) {
                    throw falloHistorial;
                }
                return hayHistorial && !convocatorias.isEmpty();
            };

    private final ParticipacionesM4DesdeM5 proveedor = new ParticipacionesM4DesdeM5(m4, historial);

    @Test
    @DisplayName("consulta el historial con exactamente las convocatorias de la sesión")
    void consultaConLasConvocatoriasDeLaSesion() {
        assertTrue(proveedor.tieneParticipacionesOIntentos(SESION));
        assertEquals(List.of(List.of(CONVOCATORIA_A, CONVOCATORIA_B)), consultadas);
    }

    @Test
    @DisplayName("sin participaciones en sus convocatorias responde false")
    void sinHistorial() {
        hayHistorial = false;
        assertFalse(proveedor.tieneParticipacionesOIntentos(SESION));
    }

    @Test
    @DisplayName("una sesión sin convocatorias no tiene historial")
    void sesionSinConvocatorias() {
        assertFalse(proveedor.tieneParticipacionesOIntentos(UUID.randomUUID()));
    }

    @Test
    @DisplayName("un error técnico del historial se propaga: nunca se convierte en false")
    void errorTecnicoNoEsFalse() {
        IllegalStateException caida = new IllegalStateException("base no disponible");
        falloHistorial = caida;
        var error =
                assertThrows(
                        ReglaNegocioException.class,
                        () -> proveedor.tieneParticipacionesOIntentos(SESION));
        assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
        assertSame(caida, error.getCause());
        assertFalse(error.getMessage().contains("base no disponible"));
    }

    @Test
    @DisplayName("convocatorias incompletas de M4 se rechazan en lugar de ignorarse")
    void convocatoriasIncompletas() {
        deLaSesion = java.util.Arrays.asList((UUID) null);
        assertThrows(
                ReglaNegocioException.class, () -> proveedor.tieneParticipacionesOIntentos(SESION));
        deLaSesion = null;
        assertThrows(
                ReglaNegocioException.class, () -> proveedor.tieneParticipacionesOIntentos(SESION));
    }

    @Test
    @DisplayName("rechaza una sesión nula y dependencias nulas")
    void rechazaNulos() {
        assertThrows(
                IllegalArgumentException.class,
                () -> proveedor.tieneParticipacionesOIntentos(null));
        assertThrows(
                NullPointerException.class, () -> new ParticipacionesM4DesdeM5(null, historial));
        assertThrows(NullPointerException.class, () -> new ParticipacionesM4DesdeM5(m4, null));
    }

}
