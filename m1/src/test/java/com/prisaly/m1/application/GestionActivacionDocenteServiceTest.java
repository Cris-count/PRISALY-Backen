package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.service.ActivacionDocenteService;
import com.prisaly.m1.application.service.AdministracionIdentidadService;
import com.prisaly.m1.application.service.AutenticacionProtegidaService.AutenticacionLimitadaException;
import com.prisaly.m1.application.service.GestionActivacionDocenteService;
import com.prisaly.m1.infrastructure.security.RegistroIntentosEnMemoria;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** D-05 (DC-77): administrator gate, transaction boundary and public-activation attempt limits. */
class GestionActivacionDocenteServiceTest {
    static final String TOKEN = "A".repeat(43);
    final UUID admin = UUID.randomUUID(), docente = UUID.randomUUID();
    final AtomicInteger transacciones = new AtomicInteger();
    ActivacionDocenteService nucleo;
    AdministracionIdentidadService administracion;
    GestionActivacionDocenteService servicio;

    @BeforeEach
    void preparar() {
        nucleo = mock(ActivacionDocenteService.class);
        administracion = mock(AdministracionIdentidadService.class);
        when(administracion.exigirAdministrador()).thenReturn(admin);
        TransactionTemplate tx = new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> accion) {
                transacciones.incrementAndGet();
                TransactionStatus estado = new SimpleTransactionStatus();
                return accion.doInTransaction(estado);
            }
        };
        // 100 per token, 3 per origin, 15 minutes: the origin limit is the one exercised here.
        servicio = new GestionActivacionDocenteService(nucleo, administracion,
                new RegistroIntentosEnMemoria(Clock.systemUTC(), 100, 3, Duration.ofMinutes(15)), tx);
    }

    @Test
    void emitirYRevocarExigenAdministradorAntesDeAbrirTransaccion() {
        servicio.emitir(docente);
        verify(nucleo).emitir(docente, admin);
        when(nucleo.revocar(docente, admin)).thenReturn(1);
        assertEquals(1, servicio.revocar(docente));
        verify(nucleo).revocar(docente, admin);
        assertEquals(2, transacciones.get());

        when(administracion.exigirAdministrador()).thenThrow(new IdentityDomainException("ADMIN_REQUIRED", "x"));
        assertEquals("ADMIN_REQUIRED", assertThrows(IdentityDomainException.class, () -> servicio.emitir(docente)).code());
        assertEquals("ADMIN_REQUIRED", assertThrows(IdentityDomainException.class, () -> servicio.revocar(docente)).code());
        verifyNoMoreInteractions(nucleo);
        assertEquals(2, transacciones.get(), "a rejected actor never opens a transaction");
    }

    @Test
    void tokensInvalidosCuentanPorOrigenYAlLimiteSeResponde429SinEvaluar() {
        doThrow(new IdentityDomainException(ActivacionDocenteService.CODE_ACTIVACION_INVALIDA, "x"))
                .when(nucleo).activar(anyString(), anyString());
        for (int i = 0; i < 3; i++) {
            assertThrows(IdentityDomainException.class, () -> servicio.activar(TOKEN, "Clave#Valida-2026", "10.0.0.1"));
        }
        var limitada = assertThrows(AutenticacionLimitadaException.class,
                () -> servicio.activar(TOKEN, "Clave#Valida-2026", "10.0.0.1"));
        assertTrue(limitada.reintentarEnSegundos() > 0);
        verify(nucleo, times(3)).activar(anyString(), anyString());
        // Another origin is independent.
        assertThrows(IdentityDomainException.class, () -> servicio.activar(TOKEN, "Clave#Valida-2026", "10.0.0.2"));
    }

    @Test
    void contrasenaRechazadaPorPoliticaNoCuentaComoFallo() {
        doThrow(new IdentityDomainException("PASSWORD_TOO_SHORT", "x")).when(nucleo).activar(anyString(), any());
        for (int i = 0; i < 10; i++) {
            assertEquals("PASSWORD_TOO_SHORT", assertThrows(IdentityDomainException.class,
                    () -> servicio.activar(TOKEN, "corta", "10.0.0.3")).code());
        }
        verify(nucleo, times(10)).activar(anyString(), any());
    }

    @Test
    void exitoLiberaYFalloTecnicoNoCuenta() {
        doThrow(new IllegalStateException("bd caida")).when(nucleo).activar(anyString(), anyString());
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class, () -> servicio.activar(TOKEN, "Clave#Valida-2026", "10.0.0.4"));
        }
        doNothing().when(nucleo).activar(anyString(), anyString());
        servicio.activar(TOKEN, "Clave#Valida-2026", "10.0.0.4");
        servicio.activar(TOKEN, "Clave#Valida-2026", null); // unknown origin is a valid key, never a bypass
        verify(nucleo, times(7)).activar(anyString(), anyString());
    }
}
