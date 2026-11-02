package com.prisaly.m5.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.infrastructure.programacion.CoordinacionInicioDesdeM4;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

class CoordinacionInicioTest {
    final ReferenciaConvocatoria convocatoria =
            new ReferenciaConvocatoria(UUID.randomUUID().toString());
    final UUID estudiante = UUID.randomUUID();
    final ProgramacionInicioCoordinadoPort reserva = mock(ProgramacionInicioCoordinadoPort.class);
    final IdentidadEstudianteActual identidad = mock(IdentidadEstudianteActual.class);

    @Test
    void sinIdentidadNoReserva() {
        when(identidad.actual()).thenReturn(Optional.empty());
        assertThrows(
                EstudianteNoIdentificadoException.class,
                () -> new CoordinacionInicioDesdeM4(reserva, identidad).accept(convocatoria));
        verifyNoInteractions(reserva);
    }

    @Test
    void convocatoriaAjenaNoContinua() {
        when(identidad.actual())
                .thenReturn(Optional.of(new IdentificadorEstudiante(estudiante.toString())));
        when(reserva.bloquearYConsultarConvocatoria(
                        UUID.fromString(convocatoria.valor()), estudiante))
                .thenReturn(Optional.empty());
        assertThrows(
                ConvocatoriaNoEncontradaException.class,
                () -> new CoordinacionInicioDesdeM4(reserva, identidad).accept(convocatoria));
    }

    @Test
    void respuestaNulaDelProveedorNoAutoriza() {
        when(identidad.actual())
                .thenReturn(Optional.of(new IdentificadorEstudiante(estudiante.toString())));
        when(reserva.bloquearYConsultarConvocatoria(any(), any())).thenReturn(null);
        assertThrows(
                ConsultaProgramacionNoDisponibleException.class,
                () -> new CoordinacionInicioDesdeM4(reserva, identidad).accept(convocatoria));
    }

    @Test
    void fachadaSinCoordinadorNoInicia() {
        var fachada = mock(ParticipacionFacade.class);
        var transacciones = mock(PlatformTransactionManager.class);
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        assertThrows(
                ConsultaProgramacionNoDisponibleException.class,
                () ->
                        new FachadaParticipacionTransaccional(fachada, transacciones)
                                .iniciarOReanudar(convocatoria));
        verifyNoInteractions(fachada);
        verify(transacciones).rollback(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservaDentroDeLaTransaccionAntesDeParticipacionEIntento() {
        var fachada = mock(ParticipacionFacade.class);
        var transacciones = mock(PlatformTransactionManager.class);
        Consumer<ReferenciaConvocatoria> coordinacion = mock(Consumer.class);
        when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        new FachadaParticipacionTransaccional(fachada, transacciones, coordinacion)
                .iniciarOReanudar(convocatoria);
        var orden = inOrder(transacciones, coordinacion, fachada);
        orden.verify(transacciones).getTransaction(any());
        orden.verify(coordinacion).accept(convocatoria);
        orden.verify(fachada).iniciarOReanudar(convocatoria);
        orden.verify(transacciones).commit(any());
    }
}
