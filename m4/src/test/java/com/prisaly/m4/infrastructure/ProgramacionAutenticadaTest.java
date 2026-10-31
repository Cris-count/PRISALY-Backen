package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.prisaly.m1.application.port.AuthenticatedStudent;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.ProgramacionAutenticadaM1Adapter;

class ProgramacionAutenticadaTest {
    @Test
    void identidadEstudiantilUsaStudentIdYNoUserId() {
        var m1 = mock(StudentAccessPort.class);
        var consulta = mock(ProgramacionParticipacionPort.class);
        UUID usuario = UUID.randomUUID();
        UUID estudiante = UUID.randomUUID();
        UUID sesion = UUID.randomUUID();
        when(m1.requireCurrentStudent()).thenReturn(new AuthenticatedStudent(usuario, estudiante));
        when(consulta.consultarSesion(sesion, estudiante)).thenReturn(Optional.empty());
        assertTrue(new ProgramacionAutenticadaM1Adapter(m1, consulta).consultarSesion(sesion).isEmpty());
        verify(consulta).consultarSesion(sesion, estudiante);
        verify(consulta, never()).consultarSesion(sesion, usuario);
    }

    @Test
    void consultaConvocatoriaConIdentidadActualSinAceptarIdentidadDelCliente() {
        var consulta = mock(ProgramacionParticipacionPort.class);
        UUID estudiante = UUID.randomUUID();
        UUID convocatoria = UUID.randomUUID();
        when(consulta.consultarConvocatoria(convocatoria, estudiante)).thenReturn(Optional.empty());
        var adapter = new ProgramacionAutenticadaM1Adapter(
                () -> new AuthenticatedStudent(UUID.randomUUID(), estudiante), consulta);
        assertTrue(adapter.consultarConvocatoria(convocatoria).isEmpty());
        verify(consulta).consultarConvocatoria(convocatoria, estudiante);
    }

    @Test
    void proveedorProductivoActualM1DeniegaPorDD02SinConsultarSesiones() {
        var consulta = mock(ProgramacionParticipacionPort.class);
        var adapter = new ProgramacionAutenticadaM1Adapter(new DenyingStudentAccessService(), consulta);
        var error = assertThrows(ReglaNegocioException.class, () -> adapter.consultarSesion(UUID.randomUUID()));
        assertEquals("M4-PROVEEDOR-NO-DISPONIBLE", error.getCodigo());
        verifyNoInteractions(consulta);
    }

    @Test
    void identidadIncompletaNoDevuelveDatos() {
        var consulta = mock(ProgramacionParticipacionPort.class);
        var adapter = new ProgramacionAutenticadaM1Adapter(() -> new AuthenticatedStudent(UUID.randomUUID(), null), consulta);
        assertThrows(ReglaNegocioException.class, () -> adapter.consultarSesion(UUID.randomUUID()));
        verifyNoInteractions(consulta);
    }
}
