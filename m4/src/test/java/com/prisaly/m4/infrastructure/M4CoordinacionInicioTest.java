package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m4.infrastructure.adapter.ProgramacionInicioCoordinadoJdbcAdapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

class M4CoordinacionInicioTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final ProgramacionParticipacionPort consulta = mock(ProgramacionParticipacionPort.class);
    final ProgramacionInicioCoordinadoJdbcAdapter reserva =
            new ProgramacionInicioCoordinadoJdbcAdapter(jdbc, consulta);

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void sinTransaccionNoConsultaNiAutoriza() {
        assertThrows(
                ConsultaProgramacionNoDisponibleException.class,
                () -> reserva.bloquearYConsultarConvocatoria(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(jdbc, consulta);
    }

    @Test
    void soloLecturaNoConsultaNiAutoriza() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(
                ConsultaProgramacionNoDisponibleException.class,
                () -> reserva.bloquearYConsultarConvocatoria(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(jdbc, consulta);
    }

    @Test
    void aislamientoDeInstantaneaNoSeAceptaComoLecturaFresca() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class))
                .thenReturn("repeatable read");
        assertThrows(
                ConsultaProgramacionNoDisponibleException.class,
                () -> reserva.bloquearYConsultarConvocatoria(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(consulta);
    }

    @Test
    void falloSqlConservaCausaSinAutorizar() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var causa = new DataAccessResourceFailureException("detalle reservado");
        when(jdbc.queryForObject("SELECT current_setting('transaction_isolation')", String.class))
                .thenThrow(causa);
        var error =
                assertThrows(
                        ConsultaProgramacionNoDisponibleException.class,
                        () ->
                                reserva.bloquearYConsultarConvocatoria(
                                        UUID.randomUUID(), UUID.randomUUID()));
        assertSame(causa, error.getCause());
        assertFalse(error.getMessage().contains("detalle reservado"));
        verifyNoInteractions(consulta);
    }
}
