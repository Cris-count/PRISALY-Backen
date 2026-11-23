package com.prisaly.m5.infrastructure.evaluacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.prisaly.m4.api.ResponsableConvocatoriaPort;
import com.prisaly.m4.api.ResponsableConvocatoriaPort.ProgramacionConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioIntentos.VinculoParticipacion;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** B1 (DC-55): teacher reading port. Owner check before reading the attempt; only finalized answers leave M5. */
class LecturaIntentoDocenteDesdeM5Test {
    final UUID idIntento = UUID.randomUUID(), participacion = UUID.randomUUID(), convocatoria = UUID.randomUUID(),
            caso = UUID.randomUUID();
    final ReferenciaIntento ref = new ReferenciaIntento(idIntento.toString());

    RepositorioIntentos repositorio;
    ResponsableConvocatoriaPort responsables;
    LecturaIntentoDocenteDesdeM5.PropietarioDelCaso propietario;
    PlatformTransactionManager transacciones;
    TransactionDefinition definicion;
    LecturaIntentoDocenteDesdeM5 lector;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioIntentos.class);
        responsables = mock(ResponsableConvocatoriaPort.class);
        propietario = mock(LecturaIntentoDocenteDesdeM5.PropietarioDelCaso.class);
        transacciones = mock(PlatformTransactionManager.class);
        when(transacciones.getTransaction(any())).thenAnswer(inv -> {
            definicion = inv.getArgument(0);
            return new SimpleTransactionStatus();
        });
        lector = new LecturaIntentoDocenteDesdeM5(repositorio, responsables, propietario, transacciones);
        when(repositorio.vinculo(ref)).thenReturn(Optional.of(new VinculoParticipacion(participacion, convocatoria)));
        when(responsables.consultar(convocatoria))
                .thenReturn(Optional.of(new ProgramacionConvocatoria(UUID.randomUUID(), caso, UUID.randomUUID())));
    }

    private Intento intento(EstadoIntento estado) {
        Intento i = mock(Intento.class);
        when(i.estado()).thenReturn(estado);
        when(i.respuestas()).thenReturn(List.of());
        return i;
    }

    @Test
    void lecturaConsistenteDeSoloLecturaEnTransaccionPropia() {
        when(propietario.esDocenteActivoPropietario(caso)).thenReturn(true);
        Intento finalizado = intento(EstadoIntento.FINALIZADO);
        when(repositorio.buscar(ref)).thenReturn(Optional.of(finalizado));
        var leido = lector.findById(idIntento).orElseThrow();
        assertEquals(IntentoEvaluacion.EstadoIntento.FINALIZADO, leido.estado());
        assertEquals(caso, leido.idCaso());
        assertEquals(participacion, leido.idParticipacion());
        assertTrue(definicion.isReadOnly());
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, definicion.getIsolationLevel());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definicion.getPropagationBehavior());
    }

    @Test
    void docenteNoPropietarioSeDeniegaSinLeerElIntento() {
        when(propietario.esDocenteActivoPropietario(caso)).thenReturn(false);
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> lector.findById(idIntento));
        verify(repositorio, never()).buscar(any());
        assertFalse(lector.perteneceAlDocenteActual(idIntento));
    }

    @Test
    void intentoAbiertoSeDevuelveSinRespuestas() {
        when(propietario.esDocenteActivoPropietario(caso)).thenReturn(true);
        Intento abierto = intento(EstadoIntento.EN_CURSO);
        when(repositorio.buscar(ref)).thenReturn(Optional.of(abierto));
        var leido = lector.findById(idIntento).orElseThrow();
        assertEquals(IntentoEvaluacion.EstadoIntento.EN_CURSO, leido.estado());
        assertEquals(List.of(), leido.respuestas());
        verify(abierto, never()).respuestas();
    }

    @Test
    void ausenciaYRelacionesInconsistentes() {
        UUID otro = UUID.randomUUID();
        when(repositorio.vinculo(new ReferenciaIntento(otro.toString()))).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), lector.findById(otro));
        assertFalse(lector.perteneceAlDocenteActual(otro));
        verifyNoInteractions(propietario);
        when(responsables.consultar(convocatoria)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> lector.findById(idIntento));
    }

    @Test
    void noEsBeanNiLectorGenerico() {
        assertTrue(Modifier.isFinal(LecturaIntentoDocenteDesdeM5.class.getModifiers()));
        assertTrue(Arrays.stream(LecturaIntentoDocenteDesdeM5.class.getAnnotations())
                .noneMatch(a -> a.annotationType().getName().startsWith("org.springframework")));
        // Only the M6 port method and the ownership check are public.
        assertEquals(List.of("findById", "perteneceAlDocenteActual"), Arrays.stream(
                LecturaIntentoDocenteDesdeM5.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName).sorted().toList());
    }
}
