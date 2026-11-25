package com.prisaly.config.m6;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.web.evaluacion.CalificacionEstudianteController;
import com.prisaly.web.evaluacion.LiberacionCalificacionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;

/** N2 candidate (DC-87): shipped OFF, and ON fails startup instead of degrading when its prerequisites are missing. */
class LiberacionN2ComponentesTest {

    @Test
    void configuracionYRutasSoloExistenConElInterruptorEnTrue() {
        for (Class<?> tipo : new Class<?>[] {M6LiberacionConfig.class, LiberacionCalificacionController.class,
                CalificacionEstudianteController.class}) {
            var c = tipo.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(c, tipo.getSimpleName());
            assertArrayEquals(new String[] {"prisaly.m6.liberacion.enabled"}, c.name(), tipo.getSimpleName());
            assertEquals("true", c.havingValue());
            assertFalse(c.matchIfMissing());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinEvaluacionM6ElArranqueFalla() {
        ObjectProvider<EvaluacionDocenteM6> sinM6 = mock(ObjectProvider.class);
        var ex = assertThrows(IllegalStateException.class, () -> new M6LiberacionConfig().liberacionCalificacionM6(sinM6,
                mock(ConsultaCalificacionPort.class), mock(IdentityAccessPort.class), mock(IdentidadEstudianteActual.class),
                mock(TitularidadIntentos.class), mock(JdbcTemplate.class)));
        assertTrue(ex.getMessage().startsWith("M6-LIBERACION-REQUIERE-EVALUACION"), ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinLaTablaCandidataV019ElArranqueFalla() {
        ObjectProvider<EvaluacionDocenteM6> conM6 = mock(ObjectProvider.class);
        when(conM6.getIfAvailable()).thenReturn(mock(EvaluacionDocenteM6.class));
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        var ex = assertThrows(IllegalStateException.class, () -> new M6LiberacionConfig().liberacionCalificacionM6(conM6,
                mock(ConsultaCalificacionPort.class), mock(IdentityAccessPort.class), mock(IdentidadEstudianteActual.class),
                mock(TitularidadIntentos.class), jdbc));
        assertTrue(ex.getMessage().startsWith("M6-LIBERACION-REQUIERE-V019"), ex.getMessage());
    }
}
