package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.EmisorTokenAccesoPort;
import com.prisaly.m1.application.service.AutenticacionProtegidaService;
import com.prisaly.m1.application.service.PersonnelAuthenticationService;
import com.prisaly.m1.infrastructure.security.RegistroIntentosEnMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DM1-06 (DC-M1-04): protection wrapped around PersonnelAuthenticationService, over the real in-memory registry. */
class AutenticacionProtegidaServiceTest {
    private final PersonnelAuthenticationService autenticacion = mock(PersonnelAuthenticationService.class);
    private final RegistroIntentosEnMemoria registro = new RegistroIntentosEnMemoria(
            Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC), 5, 20, Duration.ofMinutes(15));
    private final AutenticacionProtegidaService servicio = new AutenticacionProtegidaService(autenticacion, registro);
    private final PersonnelAuthenticationService.AuthResult ok = new PersonnelAuthenticationService.AuthResult("t",
            new AuthenticatedUser(UUID.randomUUID(), "a@prisaly.test", "A", "Docente", "Activo", Set.of()));

    private void fallo(String correo) {
        assertThrows(IdentityDomainException.class, () -> servicio.autenticar(correo, "mala", "10.0.0.1"));
    }

    @Test
    void cincoFallosLimitanLaCuentaConRetryAfterYSinEvaluarLaContrasena() {
        when(autenticacion.authenticate("a@prisaly.test", "mala"))
                .thenThrow(new IdentityDomainException("AUTH_FAILED", "Authentication failed"));
        for (int i = 0; i < 5; i++) {
            fallo("a@prisaly.test");
        }
        var limitado = assertThrows(AutenticacionProtegidaService.AutenticacionLimitadaException.class,
                () -> servicio.autenticar("a@prisaly.test", "correcta", "10.0.0.1"));
        assertEquals(900, limitado.reintentarEnSegundos());
        verify(autenticacion, never()).authenticate("a@prisaly.test", "correcta");
    }

    @Test
    void cuentasExistentesEInexistentesSeLimitanIgual() {
        when(autenticacion.authenticate(any(), any()))
                .thenThrow(new IdentityDomainException("AUTH_FAILED", "Authentication failed"));
        for (String correo : new String[] {"existe@prisaly.test", "noexiste@prisaly.invalid"}) {
            for (int i = 0; i < 5; i++) {
                assertThrows(IdentityDomainException.class, () -> servicio.autenticar(correo, "x", "10.0.0." + correo.length()));
            }
            assertThrows(AutenticacionProtegidaService.AutenticacionLimitadaException.class,
                    () -> servicio.autenticar(correo, "x", "10.0.0." + correo.length()));
        }
    }

    @Test
    void laClaveDeCuentaNormalizaMayusculasYEspacios() {
        when(autenticacion.authenticate(any(), any()))
                .thenThrow(new IdentityDomainException("AUTH_FAILED", "Authentication failed"));
        for (String variante : new String[] {"A@prisaly.test", " a@PRISALY.test ", "a@prisaly.test", "A@Prisaly.Test", "a@prisaly.TEST"}) {
            fallo(variante);
        }
        assertThrows(AutenticacionProtegidaService.AutenticacionLimitadaException.class,
                () -> servicio.autenticar("a@prisaly.test", "x", "10.0.0.1"));
    }

    @Test
    void unExitoReiniciaLaCuenta() {
        when(autenticacion.authenticate("a@prisaly.test", "mala"))
                .thenThrow(new IdentityDomainException("AUTH_FAILED", "Authentication failed"));
        when(autenticacion.authenticate("a@prisaly.test", "buena")).thenReturn(ok);
        for (int i = 0; i < 4; i++) {
            fallo("a@prisaly.test");
        }
        assertSame(ok, servicio.autenticar("a@prisaly.test", "buena", "10.0.0.1"));
        for (int i = 0; i < 4; i++) {
            fallo("a@prisaly.test");
        }
        assertSame(ok, servicio.autenticar("a@prisaly.test", "buena", "10.0.0.1"));
        verify(autenticacion, times(2)).authenticate("a@prisaly.test", "buena");
    }

    @Test
    void unFalloTecnicoNoCuentaComoIntentoFallido() {
        when(autenticacion.authenticate(any(), any())).thenThrow(new EmisorTokenAccesoPort.CapacidadAgotadaException("lleno"));
        for (int i = 0; i < 10; i++) {
            assertThrows(EmisorTokenAccesoPort.CapacidadAgotadaException.class,
                    () -> servicio.autenticar("a@prisaly.test", "x", "10.0.0.1"));
        }
        assertFalse(registro.reservar(claveDe("a@prisaly.test"), "10.0.0.1").bloqueada());
    }

    private static String claveDe(String correo) {
        try {
            var m = AutenticacionProtegidaService.class.getDeclaredMethod("claveCuenta", String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, correo);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
