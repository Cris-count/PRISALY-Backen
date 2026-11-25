package com.prisaly.config.m6;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m6.application.port.ConsultaCalificacionPort;
import com.prisaly.m6.domain.model.Calificacion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** RF-27 candidate (DC-88): notify only on the first release, and a failed notice rolls the release back. */
class NotificacionCalificacionRf27Test {
    final UUID intento = UUID.randomUUID(), calificacion = UUID.randomUUID(), docente = UUID.randomUUID();
    final EvaluacionDocenteM6 evaluacion = mock(EvaluacionDocenteM6.class);
    final ConsultaCalificacionPort calificaciones = mock(ConsultaCalificacionPort.class);
    final IdentityAccessPort identidad = mock(IdentityAccessPort.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final NotificacionCalificacionM7 notificacion = mock(NotificacionCalificacionM7.class);
    final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    LiberacionCalificacionM6 servicio;

    @BeforeEach
    void preparar() {
        when(evaluacion.esDelDocenteActual(intento)).thenReturn(true);
        when(calificaciones.findByIntentoId(intento)).thenReturn(Optional.of(new Calificacion(calificacion, intento,
                UUID.randomUUID(), new BigDecimal("4.00"), java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), "{}", "S1")));
        when(identidad.currentUser()).thenReturn(new AuthenticatedUser(docente, "d@prisaly.test", "D", "DOCENTE", "Activo", Set.of()));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("idDocente", docente,
                "fechaLiberacion", OffsetDateTime.now()));
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        servicio = new LiberacionCalificacionM6(evaluacion, calificaciones, identidad,
                new ControlAccesoIntentos(mock(IdentidadEstudianteActual.class), mock(TitularidadIntentos.class)), jdbc,
                notificacion, new TransactionTemplate(tx));
    }

    @Test
    void laPrimeraLiberacionNotificaYConfirmaUnaSolaTransaccion() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertTrue(servicio.liberar(intento).nueva());
        verify(notificacion).notificar(calificacion, intento);
        verify(tx).commit(any());
        verify(tx, never()).rollback(any());
    }

    @Test
    void unaLiberacionRepetidaNoNotifica() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertFalse(servicio.liberar(intento).nueva());
        verifyNoInteractions(notificacion);
    }

    @Test
    void siLaNotificacionFallaLaLiberacionSeRevierte() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        doThrow(new IllegalStateException("titular no resoluble")).when(notificacion).notificar(calificacion, intento);
        assertThrows(IllegalStateException.class, () -> servicio.liberar(intento));
        verify(tx).rollback(any());
        verify(tx, never()).commit(any());
    }

    @Test
    void notificacionSinTransaccionSeRechazaAlConstruir() {
        assertThrows(IllegalArgumentException.class, () -> new LiberacionCalificacionM6(evaluacion, calificaciones, identidad,
                new ControlAccesoIntentos(mock(IdentidadEstudianteActual.class), mock(TitularidadIntentos.class)), jdbc,
                notificacion, null));
    }
}
