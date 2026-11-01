package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m4.application.port.*;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.*;
import com.prisaly.m4.infrastructure.config.M4ProviderAutoConfiguration;

class ContratosM4AdaptersTest {
    @Test
    void m1UsaIdentidadActualYPermisoExacto() {
        var m1 = mock(IdentityAccessPort.class);
        UUID actual = UUID.randomUUID();
        when(m1.currentUser()).thenReturn(usuario(actual));
        when(m1.hasPermission(actual, "GESTIONAR_SESION")).thenReturn(true);
        var adapter = new IdentidadM1Adapter(m1);
        assertEquals(actual, adapter.docenteActual());
        assertTrue(adapter.tienePermiso(actual, "GESTIONAR_SESION"));
        assertFalse(adapter.tienePermiso(UUID.randomUUID(), "GESTIONAR_SESION"));
        verify(m1).hasPermission(actual, "GESTIONAR_SESION");
        verify(m1, never()).requireUser(any());
    }

    @ParameterizedTest
    @CsvSource({"UNAUTHENTICATED,M4-NO-AUTENTICADO", "USER_INACTIVE,M4-SIN-PERMISO",
            "USER_NOT_FOUND,M4-SIN-PERMISO", "FALLO_NO_RECONOCIDO,M4-PROVEEDOR-NO-DISPONIBLE"})
    void erroresM1NoSeTraducenEnPermiso(String origen, String esperado) {
        var m1 = mock(IdentityAccessPort.class);
        when(m1.currentUser()).thenThrow(new IdentityDomainException(origen, "Detalle interno"));
        var error = assertThrows(ReglaNegocioException.class, () -> new IdentidadM1Adapter(m1).docenteActual());
        assertEquals(esperado, error.getCodigo());
        assertFalse(error.getMessage().contains("Detalle interno"));
    }

    @Test
    void permisoM1DenegadoNoSeSustituyePorRol() {
        var m1 = mock(IdentityAccessPort.class);
        UUID actual = UUID.randomUUID();
        when(m1.currentUser()).thenReturn(usuario(actual));
        assertFalse(new IdentidadM1Adapter(m1).tienePermiso(actual, "GESTIONAR_SESION"));
        verify(m1, never()).hasRole(any(), any());
    }

    @Test
    void autoconfiguracionSeleccionaContratosPublicosSiExisten() {
        var m1 = mock(IdentityAccessPort.class);
        var m3 = mock(CasoRepositoryPort.class);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(M4ProviderAutoConfiguration.class))
                .withBean(IdentityAccessPort.class, () -> m1).withBean(CasoRepositoryPort.class, () -> m3)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertInstanceOf(IdentidadM1Adapter.class, context.getBean(IdentidadPort.class));
                    assertInstanceOf(CasoAcademicoM3Adapter.class, context.getBean(CasoAcademicoPort.class));
                });
    }

    @Test
    void m3ResuelveUuidSinUsarRepositoriosInternos() {
        var m3 = mock(CasoRepositoryPort.class);
        UUID id = UUID.randomUUID();
        when(m3.findById(id.toString())).thenReturn(Optional.of(caso(id, CasoEstado.PUBLICADO, false)));
        assertTrue(new CasoAcademicoM3Adapter(m3).estaPublicado(id));
        verify(m3).findById(id.toString());
        verify(m3, never()).insert(any());
        verify(m3, never()).update(any());
    }

    @Test
    void m3RechazaBorradorInexistenteYReferenciaEquivocada() {
        var m3 = mock(CasoRepositoryPort.class);
        UUID id = UUID.randomUUID();
        var adapter = new CasoAcademicoM3Adapter(m3);
        when(m3.findById(id.toString())).thenReturn(Optional.empty());
        assertFalse(adapter.estaPublicado(id));
        when(m3.findById(id.toString())).thenReturn(Optional.of(caso(id, CasoEstado.BORRADOR, false)));
        assertFalse(adapter.estaPublicado(id));
        when(m3.findById(id.toString())).thenReturn(Optional.of(caso(UUID.randomUUID(), CasoEstado.PUBLICADO, false)));
        assertFalse(adapter.estaPublicado(id));
    }

    @Test
    void f303SinTransaccionNoSeDeclaraConfirmado() {
        var m3 = mock(CasoRepositoryPort.class);
        var error = assertThrows(ReglaNegocioException.class,
                () -> new CasoAcademicoM3Adapter(m3).solicitarBloqueoAcademico(UUID.randomUUID()));
        assertEquals("M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO", error.getCodigo());
        verifyNoInteractions(m3);
    }

    @Test
    void booleanoDeAgregadoNoSustituyeContratoAtomicoF303() {
        var m3 = mock(CasoRepositoryPort.class);
        UUID id = UUID.randomUUID();
        var adapter = new CasoAcademicoM3Adapter(m3);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            when(m3.findById(id.toString())).thenReturn(Optional.of(caso(id, CasoEstado.PUBLICADO, false)));
            var error = assertThrows(ReglaNegocioException.class, () -> adapter.solicitarBloqueoAcademico(id));
            assertEquals("M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO", error.getCodigo());
            when(m3.findById(id.toString())).thenReturn(Optional.of(caso(id, CasoEstado.PUBLICADO, true)));
            var noConfirmado = assertThrows(ReglaNegocioException.class, () -> adapter.solicitarBloqueoAcademico(id));
            assertEquals("M4-BLOQUEO-ACADEMICO-NO-CONFIRMADO", noConfirmado.getCodigo());
            verify(m3, never()).update(any());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private AuthenticatedUser usuario(UUID id) {
        return new AuthenticatedUser(id, "fixture@example.invalid", "Fixture", "Docente", "Activo", Set.of());
    }

    private Caso caso(UUID id, CasoEstado estado, boolean bloqueado) {
        return new Caso(id.toString(), "Caso", UUID.randomUUID().toString(), 60, estado, List.of(), null, bloqueado);
    }
}
