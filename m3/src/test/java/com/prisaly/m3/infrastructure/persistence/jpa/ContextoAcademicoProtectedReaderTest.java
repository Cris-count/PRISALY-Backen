package com.prisaly.m3.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m3.api.AccesoContextoAcademicoDenegadoException;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Decision;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Operacion;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContextoAcademicoProtectedReaderTest {
    @Test
    void absentOrBlankCapabilityOrMissingVerifierNeverReads() {
        var reader = mock(CasoJpaAggregateReader.class);
        var auth = mock(AutorizacionContextoAcademicoPort.class);
        for (String capability : new String[] {null, "", " "}) {
            assertThrows(AccesoContextoAcademicoDenegadoException.class,
                    () -> new ContextoAcademicoProtectedReader(auth, reader).consultar(UUID.randomUUID(), capability));
        }
        assertThrows(AccesoContextoAcademicoDenegadoException.class,
                () -> new ContextoAcademicoProtectedReader(null, reader).consultar(UUID.randomUUID(), "untrusted"));
        verifyNoInteractions(reader, auth);
    }

    @Test
    void deniedFabricatedExpiredRevokedWrongCaseOrOperationNeverReads() {
        var reader = mock(CasoJpaAggregateReader.class);
        var auth = mock(AutorizacionContextoAcademicoPort.class);
        UUID id = UUID.randomUUID();
        // These labels model M1 rejection outcomes, not real cryptographic credentials.
        for (String scenario : new String[] {"fabricated", "expired", "revoked", "wrong-case", "wrong-operation"}) {
            when(auth.verificar(scenario, id, Operacion.CONSULTAR_CONTEXTO_EVALUABLE)).thenReturn(Decision.DENEGADO);
            assertThrows(AccesoContextoAcademicoDenegadoException.class,
                    () -> new ContextoAcademicoProtectedReader(auth, reader).consultar(id, scenario));
            verify(auth).verificar(scenario, id, Operacion.CONSULTAR_CONTEXTO_EVALUABLE);
        }
        verifyNoInteractions(reader);
    }

    @Test
    void unavailableNullDecisionOrVerifierFailureDeniesWithoutLeakingCapability() {
        var reader = mock(CasoJpaAggregateReader.class);
        UUID id = UUID.randomUUID();
        for (Decision decision : new Decision[] {Decision.NO_DISPONIBLE, null}) {
            var producer = new ContextoAcademicoProtectedReader((capability, resource, operation) -> decision, reader);
            assertThrows(AccesoContextoAcademicoDenegadoException.class, () -> producer.consultar(id, "sensitive"));
        }
        var producer = new ContextoAcademicoProtectedReader((capability, resource, operation) -> {
            throw new IllegalStateException("verifier detail: " + capability);
        }, reader);
        var error = assertThrows(AccesoContextoAcademicoDenegadoException.class, () -> producer.consultar(id, "sensitive"));
        assertFalse(error.toString().contains("sensitive")); assertNull(error.getCause());
        verifyNoInteractions(reader);
    }

    @Test
    void successfulVerificationPrecedesExactlyOneCompleteRead() {
        var snapshot = ContextoAcademicoProducerMapperTest.full();
        UUID id = snapshot.caso().idCaso();
        var reader = mock(CasoJpaAggregateReader.class);
        var auth = mock(AutorizacionContextoAcademicoPort.class);
        when(auth.verificar("opaque-fixture", id, Operacion.CONSULTAR_CONTEXTO_EVALUABLE)).thenReturn(Decision.AUTORIZADO);
        when(reader.findById(id)).thenReturn(Optional.of(snapshot));
        var producer = new ContextoAcademicoProtectedReader(auth, reader);
        assertEquals(ContextoAcademicoProducerMapper.map(snapshot), producer.consultar(id, "opaque-fixture").orElseThrow());
        var order = inOrder(auth, reader);
        order.verify(auth).verificar("opaque-fixture", id, Operacion.CONSULTAR_CONTEXTO_EVALUABLE);
        order.verify(reader).findById(id); order.verifyNoMoreInteractions();
    }

    @Test
    void onlyAuthorizedMissingCaseReturnsEmptyAndSecurityDoesNotDiscloseExistence() {
        var reader = mock(CasoJpaAggregateReader.class);
        var auth = mock(AutorizacionContextoAcademicoPort.class);
        UUID id = UUID.randomUUID();
        when(auth.verificar("accepted", id, Operacion.CONSULTAR_CONTEXTO_EVALUABLE)).thenReturn(Decision.AUTORIZADO);
        when(reader.findById(id)).thenReturn(Optional.empty());
        var producer = new ContextoAcademicoProtectedReader(auth, reader);
        assertTrue(producer.consultar(id, "accepted").isEmpty());
        verify(reader).findById(id);
        clearInvocations(reader);
        for (UUID deniedId : new UUID[] {id, UUID.randomUUID()}) {
            assertThrows(AccesoContextoAcademicoDenegadoException.class, () -> producer.consultar(deniedId, "rejected"));
        }
        verifyNoInteractions(reader);
    }

    @Test
    void persistenceAndIntegrityErrorsAreNotConvertedToSecurityOrEmpty() {
        var reader = mock(CasoJpaAggregateReader.class);
        UUID id = UUID.randomUUID();
        var producer = new ContextoAcademicoProtectedReader((capability, resource, operation) -> Decision.AUTORIZADO, reader);
        var failure = new org.springframework.dao.DataAccessResourceFailureException("Database unavailable");
        when(reader.findById(id)).thenThrow(failure);
        assertSame(failure, assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> producer.consultar(id, "accepted-fixture")));
        var source = ContextoAcademicoProducerMapperTest.full();
        var broken = new CasoJpaAggregateSnapshot(source.caso(), java.util.List.of(), source.preguntas(),
                source.opciones(), source.rubrica(), source.rubricaPreguntas());
        doReturn(Optional.of(broken)).when(reader).findById(id);
        assertThrows(IllegalStateException.class, () -> producer.consultar(id, "accepted-fixture"));
        assertThrows(NullPointerException.class, () -> producer.consultar(null, "accepted-fixture"));
    }
}
