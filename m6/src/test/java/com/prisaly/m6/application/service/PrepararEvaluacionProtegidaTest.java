package com.prisaly.m6.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.integration.ContextoAcademico;
import com.prisaly.m6.application.integration.ContextoEvaluacionPreparado;
import com.prisaly.m6.application.integration.IntentoEvaluacion;
import com.prisaly.m6.application.port.*;
import com.prisaly.m6.domain.model.Calificacion;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrepararEvaluacionProtegidaTest {
    private final AutorizacionEvaluacionPort auth = mock(AutorizacionEvaluacionPort.class);
    private final ConsultaCalificacionPort grades = mock(ConsultaCalificacionPort.class);
    private final ConsultaIntentoEvaluacionPort attempts = mock(ConsultaIntentoEvaluacionPort.class);
    private final SolicitudCapacidadContextoAcademicoPort issuer = mock(SolicitudCapacidadContextoAcademicoPort.class);
    private final ConsultaContextoAcademicoPort contexts = mock(ConsultaContextoAcademicoPort.class);
    private final SolicitudEvaluacion request = new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID());
    private final UUID caseId = UUID.randomUUID();
    private final PrepararContextoEvaluacionService preparation = new PrepararContextoEvaluacionService(attempts, issuer, contexts);
    private final PrepararEvaluacionProtegida service = new PrepararEvaluacionProtegida(auth, grades, preparation);

    @Test void missingProviderDeniesBeforeAnyRead() {
        assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> new PrepararEvaluacionProtegida(null, grades, preparation).preparar(request));
        verifyNoInteractions(grades, attempts, issuer, contexts);
    }

    @Test void everyNonAuthorizedDecisionDeniesBeforeAnyRead() {
        for (var decision : new AutorizacionEvaluacionPort.DecisionAcceso[] {
                null, AutorizacionEvaluacionPort.DecisionAcceso.DENEGADO,
                AutorizacionEvaluacionPort.DecisionAcceso.NO_DISPONIBLE}) {
            when(auth.verificarAcceso(request)).thenReturn(decision);
            assertThrows(AccesoEvaluacionDenegadoException.class, () -> service.preparar(request));
        }
        verifyNoInteractions(grades, attempts, issuer, contexts);
    }

    @Test void providerFailureDoesNotExposeCredentials() {
        when(auth.verificarAcceso(request)).thenThrow(new IllegalStateException("secret-capability"));
        var error = assertThrows(AccesoEvaluacionDenegadoException.class, () -> service.preparar(request));
        assertNull(error.getCause());
        assertFalse(error.toString().contains("secret-capability"));
        verifyNoInteractions(grades, attempts, issuer, contexts);
    }

    @Test void existingGradeStopsRepreparation() {
        authorize();
        when(grades.findByIntentoId(request.idIntento())).thenReturn(Optional.of(mock(Calificacion.class)));
        assertThrows(IllegalStateException.class, () -> service.preparar(request));
        verifyNoInteractions(attempts, issuer, contexts);
    }

    @Test void databaseFailureNeverBecomesAbsence() {
        authorize();
        var failure = new IllegalStateException("database unavailable");
        when(grades.findByIntentoId(request.idIntento())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.preparar(request)));
        verifyNoInteractions(attempts, issuer, contexts);
    }

    @Test void rubricMustExistAndMatchRequestAndCase() {
        ready(null);
        assertThrows(IllegalStateException.class, () -> service.preparar(request));
        ready(rubric(UUID.randomUUID(), caseId));
        assertThrows(IllegalStateException.class, () -> service.preparar(request));
        ready(rubric(request.idRubrica(), UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> service.preparar(request));
    }

    @Test void repeatedPreparationIsReadOnlyAndMakesNoAcademicDecision() {
        var context = ready(rubric(request.idRubrica(), caseId));
        ContextoEvaluacionPreparado first = service.preparar(request);
        assertEquals(first, service.preparar(request));
        assertSame(context, first.contextoAcademico());
        var order = inOrder(auth, grades, attempts, issuer, contexts);
        for (int i = 0; i < 2; i++) {
            order.verify(auth).verificarAcceso(request);
            order.verify(grades).findByIntentoId(request.idIntento());
            order.verify(attempts).findById(request.idIntento());
            order.verify(issuer).solicitarCapacidad(request.idIntento(), caseId);
            order.verify(contexts).findByCasoId(caseId, "fixture");
        }
        verifyNoMoreInteractions(auth, grades, attempts, issuer, contexts);
    }

    @Test void nullRequestDoesNotAccessProviders() {
        assertThrows(NullPointerException.class, () -> service.preparar(null));
        verifyNoInteractions(auth, grades, attempts, issuer, contexts);
    }

    private void authorize() {
        when(auth.verificarAcceso(request)).thenReturn(AutorizacionEvaluacionPort.DecisionAcceso.AUTORIZADO);
        when(grades.findByIntentoId(request.idIntento())).thenReturn(Optional.empty());
    }

    private ContextoAcademico ready(ContextoAcademico.Rubrica rubric) {
        authorize();
        when(attempts.findById(request.idIntento())).thenReturn(Optional.of(new IntentoEvaluacion(
                request.idIntento(), caseId, caseId, IntentoEvaluacion.EstadoIntento.FINALIZADO, List.of())));
        when(issuer.solicitarCapacidad(request.idIntento(), caseId)).thenReturn("fixture");
        var context = new ContextoAcademico(caseId, UUID.randomUUID(), "Case", null, null, 60,
                "Borrador", false, null, null, null, List.of(), rubric);
        when(contexts.findByCasoId(caseId, "fixture")).thenReturn(Optional.of(context));
        return context;
    }

    private ContextoAcademico.Rubrica rubric(UUID id, UUID caseRef) {
        return new ContextoAcademico.Rubrica(id, caseRef, "Rubric", null, null, null, null, Set.of());
    }
}
