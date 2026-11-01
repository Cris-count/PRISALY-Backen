package com.prisaly.m6.application.proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.port.EvaluacionCasoUso;
import com.prisaly.m6.domain.strategy.ResultadoEvaluacion;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluacionProtectionProxyTest {
    @Test
    void authorizationPrecedesExactlyOneDelegationAndPreservesRequestAndResult() {
        var authorization = mock(AutorizacionEvaluacionPort.class);
        var delegate = mock(EvaluacionCasoUso.class);
        var request = request();
        var result = new ResultadoEvaluacion(new BigDecimal("12.500"), " {\"fixture\":true} ");
        when(authorization.verificarAcceso(request)).thenReturn(DecisionAcceso.AUTORIZADO);
        when(delegate.evaluar(request)).thenReturn(result);
        EvaluacionCasoUso proxy = new EvaluacionProtectionProxy(authorization, delegate);

        assertSame(result, proxy.evaluar(request));

        var order = inOrder(authorization, delegate);
        order.verify(authorization).verificarAcceso(same(request));
        order.verify(delegate).evaluar(same(request));
        verifyNoMoreInteractions(authorization, delegate);
        assertEquals(new BigDecimal("12.500"), result.valor());
        assertEquals(" {\"fixture\":true} ", result.detalleEvaluacion());
    }

    @Test
    void deniedAndUnavailableDecisionsNeverDelegate() {
        for (var decision : List.of(DecisionAcceso.DENEGADO, DecisionAcceso.NO_DISPONIBLE)) {
            var authorization = mock(AutorizacionEvaluacionPort.class);
            var delegate = mock(EvaluacionCasoUso.class);
            var request = request();
            when(authorization.verificarAcceso(request)).thenReturn(decision);
            assertThrows(AccesoEvaluacionDenegadoException.class,
                    () -> new EvaluacionProtectionProxy(authorization, delegate).evaluar(request));
            verify(authorization).verificarAcceso(same(request));
            verifyNoMoreInteractions(authorization); verifyNoInteractions(delegate);
        }
    }

    @Test
    void missingProviderDeniesInsteadOfAssigningDefaultPermission() {
        var delegate = mock(EvaluacionCasoUso.class);
        assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> new EvaluacionProtectionProxy(null, delegate).evaluar(request()));
        verifyNoInteractions(delegate);
    }

    @Test
    void absentDecisionIsNotInterpretedAsAuthorization() {
        var authorization = mock(AutorizacionEvaluacionPort.class);
        var delegate = mock(EvaluacionCasoUso.class);
        var request = request();
        when(authorization.verificarAcceso(request)).thenReturn(null);
        assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> new EvaluacionProtectionProxy(authorization, delegate).evaluar(request));
        verify(authorization).verificarAcceso(same(request)); verifyNoInteractions(delegate);
    }

    @Test
    void providerFailureDeniesAndRetainsCauseWithoutCallingDelegate() {
        var authorization = mock(AutorizacionEvaluacionPort.class);
        var delegate = mock(EvaluacionCasoUso.class);
        var request = request(); var failure = new IllegalStateException("Test provider unavailable");
        when(authorization.verificarAcceso(request)).thenThrow(failure);
        var denied = assertThrows(AccesoEvaluacionDenegadoException.class,
                () -> new EvaluacionProtectionProxy(authorization, delegate).evaluar(request));
        assertSame(failure, denied.getCause()); verifyNoInteractions(delegate);
        verify(authorization).verificarAcceso(same(request));
    }

    @Test
    void delegateFailureIsPropagatedWithoutBeingReclassifiedAsAuthorizationFailure() {
        var authorization = mock(AutorizacionEvaluacionPort.class);
        var delegate = mock(EvaluacionCasoUso.class);
        var request = request(); var failure = new IllegalArgumentException("Test delegate failure");
        when(authorization.verificarAcceso(request)).thenReturn(DecisionAcceso.AUTORIZADO);
        when(delegate.evaluar(request)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> new EvaluacionProtectionProxy(authorization, delegate).evaluar(request)));
        var order = inOrder(authorization, delegate);
        order.verify(authorization).verificarAcceso(same(request)); order.verify(delegate).evaluar(same(request));
        verifyNoMoreInteractions(authorization, delegate);
    }

    @Test
    void eachInvocationChecksAccessAgainWithoutCachingPositiveDecision() {
        var authorization = mock(AutorizacionEvaluacionPort.class);
        var delegate = mock(EvaluacionCasoUso.class);
        var request = request(); var result = new ResultadoEvaluacion(BigDecimal.ONE, "{}");
        when(authorization.verificarAcceso(request)).thenReturn(DecisionAcceso.AUTORIZADO, DecisionAcceso.DENEGADO);
        when(delegate.evaluar(request)).thenReturn(result);
        var proxy = new EvaluacionProtectionProxy(authorization, delegate);
        assertSame(result, proxy.evaluar(request));
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> proxy.evaluar(request));
        verify(authorization, times(2)).verificarAcceso(same(request)); verify(delegate).evaluar(same(request));
        verifyNoMoreInteractions(authorization, delegate);
    }

    @Test
    void invalidWiringOrRequestDoesNotExecuteAnAcademicOperation() {
        var authorization = mock(AutorizacionEvaluacionPort.class);
        var delegate = mock(EvaluacionCasoUso.class);
        assertThrows(NullPointerException.class, () -> new EvaluacionProtectionProxy(authorization, null));
        var proxy = new EvaluacionProtectionProxy(authorization, delegate);
        assertThrows(NullPointerException.class, () -> proxy.evaluar(null));
        verifyNoInteractions(authorization, delegate);
    }

    private static SolicitudEvaluacion request() {
        return new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID());
    }
}
