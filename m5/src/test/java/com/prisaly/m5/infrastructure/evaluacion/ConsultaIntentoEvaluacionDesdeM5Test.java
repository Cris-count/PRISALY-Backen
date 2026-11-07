package com.prisaly.m5.infrastructure.evaluacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.*;
import com.prisaly.m5.application.acceso.*;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.respuesta.*;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.ConsultaIntentoEvaluacionPort;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ConsultaIntentoEvaluacionDesdeM5Test {
    final UUID id = UUID.randomUUID(), participacion = UUID.randomUUID(), convocatoria = UUID.randomUUID(),
            estudiante = UUID.randomUUID(), caso = UUID.randomUUID(), pregunta = UUID.randomUUID(), opcion = UUID.randomUUID();
    final ReferenciaIntento referencia = new ReferenciaIntento(id.toString());
    final AutorizacionEvaluacionPort autorizacion = mock(AutorizacionEvaluacionPort.class);
    final OperacionesParticipacion operaciones = mock(OperacionesParticipacion.class);
    final RepositorioIntentos repositorio = mock(RepositorioIntentos.class);
    final IdentidadEstudianteActual identidad = mock(IdentidadEstudianteActual.class);
    final TitularidadIntentos titularidad = mock(TitularidadIntentos.class);
    final ProgramacionParticipacionPort programacion = mock(ProgramacionParticipacionPort.class);
    final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    final SolicitudEvaluacion solicitud = new SolicitudEvaluacion(id, UUID.randomUUID());
    AccesoProtegidoParticipacion protegida;
    ConsultaIntentoEvaluacionPort lector;

    @BeforeEach void preparar() {
        when(autorizacion.verificarAcceso(solicitud)).thenReturn(AutorizacionEvaluacionPort.DecisionAcceso.AUTORIZADO);
        when(identidad.actual()).thenReturn(Optional.of(new IdentificadorEstudiante(estudiante.toString())));
        when(titularidad.de(referencia, new IdentificadorEstudiante(estudiante.toString()))).thenReturn(Titularidad.PROPIO);
        protegida = new AccesoProtegidoParticipacion(new ControlAccesoIntentos(identidad, titularidad), operaciones);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(operaciones.consultarIntento(referencia)).thenReturn(resumen(EstadoIntento.FINALIZADO, List.of(respuesta())));
        when(repositorio.vinculo(referencia)).thenReturn(Optional.of(new RepositorioIntentos.VinculoParticipacion(participacion, convocatoria)));
        when(programacion.consultarConvocatoria(convocatoria, estudiante)).thenReturn(Optional.of(contexto(convocatoria)));
        lector = fabrica().para(solicitud);
    }
    FabricaConsultaIntentoEvaluacion fabrica() {
        return new FabricaConsultaIntentoEvaluacion(autorizacion, protegida, repositorio, identidad, programacion, tx);
    }
    Respuesta respuesta() { return new Respuesta(new ReferenciaPregunta(pregunta.toString()), new ValorRespuesta(opcion.toString())); }
    ResumenIntento resumen(EstadoIntento estado, List<Respuesta> respuestas) {
        return new ResumenIntento(referencia, estado, Optional.empty(), respuestas);
    }
    ContextoProgramacion contexto(UUID convocatoriaId) {
        return new ContextoProgramacion(convocatoriaId, estudiante, UUID.randomUUID(), caso, "Programada", "Autorizada",
                Optional.empty(), "Publicado", true, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600),
                Optional.empty(), Optional.empty(), Duration.ofMinutes(60), Instant.now());
    }
    @Test void mapeaInmutableEnTransaccionEstableSinCargarAgregados() {
        var dato = lector.findById(id).orElseThrow();
        assertEquals(participacion, dato.idParticipacion()); assertEquals(caso, dato.idCaso());
        assertEquals(opcion, dato.respuestas().getFirst().idOpcionSeleccionada());
        assertThrows(UnsupportedOperationException.class, () -> dato.respuestas().clear());
        verify(repositorio, never()).buscar(any());
        verify(tx).getTransaction(argThat(d -> d.isReadOnly()
                && d.getIsolationLevel() == TransactionDefinition.ISOLATION_REPEATABLE_READ
                && d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }
    @Test void autorizaEnCadaLecturaYDeniegaAntesDeAcceder() {
        lector.findById(id);
        when(autorizacion.verificarAcceso(solicitud)).thenReturn(AutorizacionEvaluacionPort.DecisionAcceso.DENEGADO);
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> lector.findById(id));
        verify(operaciones, times(1)).consultarIntento(referencia);
    }
    @Test void nuloIndisponibilidadYFalloInstitucionalNoLeen() {
        when(autorizacion.verificarAcceso(solicitud)).thenReturn(null,
                AutorizacionEvaluacionPort.DecisionAcceso.NO_DISPONIBLE).thenThrow(new IllegalStateException("secreto"));
        for (int i = 0; i < 3; i++) {
            var error = assertThrows(AccesoEvaluacionDenegadoException.class, () -> lector.findById(id));
            assertNull(error.getCause());
        }
        verifyNoInteractions(operaciones, repositorio, programacion, tx);
    }
    @Test void rechazaOtroIntentoYNulo() {
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> lector.findById(UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> lector.findById(null));
        verifyNoInteractions(autorizacion, operaciones, repositorio);
    }
    @Test void conservaProteccionM5() {
        when(titularidad.de(any(), any())).thenReturn(Titularidad.AJENO);
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> lector.findById(id));
        verifyNoInteractions(operaciones, repositorio, programacion);
    }
    @Test void inexistenteEsVacioTrasAutorizacion() {
        when(titularidad.de(any(), any())).thenReturn(Titularidad.INEXISTENTE);
        assertTrue(lector.findById(id).isEmpty()); verifyNoInteractions(repositorio, programacion);
    }
    @Test void conservaEnCursoParaRechazoExplicitoM6() {
        when(operaciones.consultarIntento(referencia)).thenReturn(resumen(EstadoIntento.EN_CURSO, List.of()));
        var preparacion = new com.prisaly.m6.application.service.PrepararContextoEvaluacionService(
                lector, null, (casoId, capacidad) -> { fail("No debe consultar M3 para EnCurso"); return Optional.empty(); });
        var error = assertThrows(com.prisaly.m6.application.PreparacionContextoException.class,
                () -> preparacion.preparar(id));
        assertEquals(com.prisaly.m6.application.PreparacionContextoException.Codigo.INTENTO_NO_FINALIZADO, error.codigo());
    }
    @Test void rechazaVinculoAusenteContextoAjenoYDuplicados() {
        when(repositorio.vinculo(referencia)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> lector.findById(id));
        when(repositorio.vinculo(referencia)).thenReturn(Optional.of(new RepositorioIntentos.VinculoParticipacion(participacion, convocatoria)));
        when(programacion.consultarConvocatoria(convocatoria, estudiante)).thenReturn(Optional.of(contexto(UUID.randomUUID())));
        assertThrows(IllegalStateException.class, () -> lector.findById(id));
        when(programacion.consultarConvocatoria(convocatoria, estudiante)).thenReturn(Optional.of(contexto(convocatoria)));
        when(operaciones.consultarIntento(referencia)).thenReturn(resumen(EstadoIntento.FINALIZADO, List.of(respuesta(), respuesta())));
        assertThrows(IllegalStateException.class, () -> lector.findById(id));
    }
    @Test void falloTecnicoNoSeConvierteEnAusencia() {
        when(repositorio.vinculo(referencia)).thenThrow(new IllegalStateException("bd"));
        assertThrows(IllegalStateException.class, () -> lector.findById(id)); verify(tx).rollback(any());
    }
    @Test void springPublicaFabricaSinLectorGlobal() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(AutorizacionEvaluacionPort.class, () -> autorizacion);
            ctx.registerBean(AccesoProtegidoParticipacion.class, () -> protegida);
            ctx.registerBean(RepositorioIntentos.class, () -> repositorio);
            ctx.registerBean(IdentidadEstudianteActual.class, () -> identidad);
            ctx.registerBean(ProgramacionParticipacionPort.class, () -> programacion);
            ctx.registerBean(PlatformTransactionManager.class, () -> tx);
            ctx.register(EnsamblajeConsultaIntentoEvaluacion.class); ctx.refresh();
            assertTrue(ctx.getBeansOfType(ConsultaIntentoEvaluacionPort.class).isEmpty());
            assertEquals(caso, ctx.getBean(FabricaConsultaIntentoEvaluacion.class).para(solicitud).findById(id).orElseThrow().idCaso());
        }
    }
    @Test void springSinAutorizadorNoArranca() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EnsamblajeConsultaIntentoEvaluacion.class);
            assertThrows(org.springframework.beans.factory.UnsatisfiedDependencyException.class, ctx::refresh);
        }
    }
}
