package com.prisaly.m5.infrastructure.programacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.domain.model.Convocatoria;
import com.prisaly.m5.api.HistorialParticipacionPort;

/**
 * Lógica del proveedor con dobles de M4 y del historial. La integración real con
 * PostgreSQL y el adaptador JPA de M4 está en {@code ParticipacionesM4DesdeM5IT}.
 */
@DisplayName("ParticipacionesM4DesdeM5 (proveedor de ParticipacionesPort de M4)")
class ParticipacionesM4DesdeM5Test {

    private static final UUID SESION = UUID.randomUUID();
    private static final UUID CONVOCATORIA_A = UUID.randomUUID();
    private static final UUID CONVOCATORIA_B = UUID.randomUUID();

    private List<Convocatoria> deLaSesion = List.of(convocatoria(CONVOCATORIA_A), convocatoria(CONVOCATORIA_B));
    private final List<Collection<UUID>> consultadas = new ArrayList<>();
    private boolean hayHistorial = true;
    private RuntimeException falloHistorial;

    private final ConvocatoriaRepository m4 = new ConvocatoriaRepository() {
        @Override
        public Convocatoria guardar(Convocatoria convocatoria) {
            throw new AssertionError("solo lectura");
        }

        @Override
        public Optional<Convocatoria> buscarPorId(UUID id) {
            throw new AssertionError("no se usa");
        }

        @Override
        public List<Convocatoria> listarPorSesion(UUID sesionId) {
            return SESION.equals(sesionId) ? deLaSesion : List.of();
        }

        @Override
        public boolean existeConvocatoria(UUID sesionId, UUID estudianteId) {
            throw new AssertionError("no se usa");
        }

        @Override
        public boolean estaConvocado(UUID sesionId, UUID estudianteId) {
            throw new AssertionError("no se usa");
        }
    };

    private final HistorialParticipacionPort historial = convocatorias -> {
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
        assertSame(caida, assertThrows(IllegalStateException.class, () -> proveedor.tieneParticipacionesOIntentos(SESION)));
    }

    @Test
    @DisplayName("convocatorias incompletas de M4 se rechazan en lugar de ignorarse")
    void convocatoriasIncompletas() {
        deLaSesion = List.of(new Convocatoria());
        assertThrows(IllegalStateException.class, () -> proveedor.tieneParticipacionesOIntentos(SESION));
        deLaSesion = null;
        assertThrows(IllegalStateException.class, () -> proveedor.tieneParticipacionesOIntentos(SESION));
    }

    @Test
    @DisplayName("rechaza una sesión nula y dependencias nulas")
    void rechazaNulos() {
        assertThrows(IllegalArgumentException.class, () -> proveedor.tieneParticipacionesOIntentos(null));
        assertThrows(NullPointerException.class, () -> new ParticipacionesM4DesdeM5(null, historial));
        assertThrows(NullPointerException.class, () -> new ParticipacionesM4DesdeM5(m4, null));
    }

    private static Convocatoria convocatoria(UUID id) {
        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setId(id);
        return convocatoria;
    }
}
