package com.prisaly.m4.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.prisaly.m4.application.port.CasoAcademicoPort;
import com.prisaly.m4.application.port.CierreParticipacionesPort;
import com.prisaly.m4.application.port.ConvocatoriaRepository;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.port.RelojAcademicoPort;
import com.prisaly.m4.application.port.SesionRepository;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DC-92 DD-01: the system close reuses the teacher close; it is idempotent and never early. */
class CierreAutomaticoDd01Test {
    final SesionRepository repo = mock(SesionRepository.class);
    final RelojAcademicoPort reloj = mock(RelojAcademicoPort.class);
    final CierreParticipacionesPort cierre = mock(CierreParticipacionesPort.class);
    final SesionAcademicaService service = new SesionAcademicaService(repo, mock(ConvocatoriaRepository.class),
            mock(IdentidadPort.class), mock(CasoAcademicoPort.class), mock(NotificacionPort.class), reloj, cierre);
    final Instant fin = Instant.parse("2031-03-12T17:00:00Z");
    SesionAcademica sesion;

    @BeforeEach
    void preparar() {
        sesion = SesionAcademica.crear(UUID.randomUUID(), UUID.randomUUID(), "Espacio", null,
                fin.minusSeconds(7200), fin, 1800);
        sesion.iniciar(fin.minusSeconds(7000));
        when(repo.buscarParaActualizar(sesion.getId())).thenReturn(Optional.of(sesion));
        when(repo.guardar(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void cierraTrasElFinMasElMargenYCierraLosIntentosAgotados() {
        when(reloj.ahora()).thenReturn(fin.plus(Duration.ofDays(1)));
        assertTrue(service.cerrarAutomaticamente(sesion.getId(), Duration.ofDays(1)));
        assertEquals(EstadoSesion.FINALIZADO, sesion.getEstado());
        assertEquals(fin.plus(Duration.ofDays(1)), sesion.getFechaFinReal());
        verify(cierre).cerrarIntentosVencidos(sesion.getId());
    }

    @Test
    void dentroDelMargenNoCierraNiEscribe() {
        when(reloj.ahora()).thenReturn(fin.plus(Duration.ofHours(23)));
        assertFalse(service.cerrarAutomaticamente(sesion.getId(), Duration.ofDays(1)));
        assertEquals(EstadoSesion.EN_CURSO, sesion.getEstado());
        verify(repo, never()).guardar(any());
        verifyNoInteractions(cierre);
    }

    @Test
    void unaSesionYaCerradaOInexistenteEsIdempotente() {
        when(reloj.ahora()).thenReturn(fin.plus(Duration.ofDays(2)));
        sesion.cerrar(fin);
        assertFalse(service.cerrarAutomaticamente(sesion.getId(), Duration.ofDays(1)));
        UUID otra = UUID.randomUUID();
        when(repo.buscarParaActualizar(otra)).thenReturn(Optional.empty());
        assertFalse(service.cerrarAutomaticamente(otra, Duration.ofDays(1)));
        verify(repo, never()).guardar(any());
        verifyNoInteractions(cierre);
    }
}
