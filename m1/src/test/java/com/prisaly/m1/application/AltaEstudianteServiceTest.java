package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.EstudianteRegistroPort;
import com.prisaly.m1.application.port.EstudianteRegistroPort.EstudianteRegistrado;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.service.AdministracionIdentidadService;
import com.prisaly.m1.application.service.AltaEstudianteService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.infrastructure.estudiantes.AltaEstudiantesConfig;
import com.prisaly.m1.presentation.AdministracionEstudiantesController;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** P-78 (DC-79): administrator gate before any write, role check, one transaction, no credential, OFF by default. */
class AltaEstudianteServiceTest {
    final UUID admin = UUID.randomUUID();
    final AtomicInteger transacciones = new AtomicInteger();
    AdministracionIdentidadService administracion;
    IdentityCatalogService catalogo;
    RolRepositorioPort roles;
    EstudianteRegistroPort estudiantes;
    AltaEstudianteService servicio;

    @BeforeEach
    void preparar() {
        administracion = mock(AdministracionIdentidadService.class);
        catalogo = mock(IdentityCatalogService.class);
        roles = mock(RolRepositorioPort.class);
        estudiantes = mock(EstudianteRegistroPort.class);
        when(administracion.exigirAdministrador()).thenReturn(admin);
        when(roles.buscarPorNombre("ESTUDIANTE")).thenReturn(Optional.of(new Rol(9, "ESTUDIANTE", null)));
        TransactionTemplate tx = new TransactionTemplate() {
            @Override public <T> T execute(TransactionCallback<T> accion) {
                transacciones.incrementAndGet();
                return accion.doInTransaction(new SimpleTransactionStatus());
            }
        };
        servicio = new AltaEstudianteService(administracion, catalogo, roles, estudiantes, tx, "ESTUDIANTE");
    }

    @Test
    void altaCreaUsuarioSinCredencialYFilaEstudianteEnUnaTransaccion() {
        UUID usuario = UUID.randomUUID();
        when(catalogo.registrarSinCredencial("Ana QA", "ana@prisaly.test", "ESTUDIANTE")).thenReturn(
                new Usuario(usuario, new Rol(9, "ESTUDIANTE", null), "Ana QA", "ana@prisaly.test", null, "Activo",
                        OffsetDateTime.now(), null));
        EstudianteRegistrado r = servicio.alta("Ana QA", "ana@prisaly.test");
        assertEquals(usuario, r.idUsuario());
        assertNotNull(r.idEstudiante());
        assertEquals("Activo", r.estado());
        verify(estudiantes).registrar(r.idEstudiante(), usuario);
        verify(catalogo, never()).registerUser(any(), any(), any(), any());
        assertEquals(1, transacciones.get(), "user and student row share one transaction");
    }

    @Test
    void sinAdministradorNoSeEscribeNada() {
        when(administracion.exigirAdministrador()).thenThrow(new IdentityDomainException("ADMIN_REQUIRED", "x"));
        assertEquals("ADMIN_REQUIRED", assertThrows(IdentityDomainException.class,
                () -> servicio.alta("Ana", "a@prisaly.test")).code());
        assertEquals("ADMIN_REQUIRED", assertThrows(IdentityDomainException.class,
                () -> servicio.buscarPorCorreo("a@prisaly.test")).code());
        verifyNoInteractions(catalogo, estudiantes);
        assertEquals(0, transacciones.get());
    }

    @Test
    void rolDeEstudianteAusenteOVacioResponde503SinEscribir() {
        when(roles.buscarPorNombre("ESTUDIANTE")).thenReturn(Optional.empty());
        assertEquals(AltaEstudianteService.CODE_STUDENT_ROLE_NOT_CONFIGURED, assertThrows(IdentityDomainException.class,
                () -> servicio.alta("Ana", "a@prisaly.test")).code());
        var vacio = new AltaEstudianteService(administracion, catalogo, roles, estudiantes, new TransactionTemplate(), " ");
        assertEquals(AltaEstudianteService.CODE_STUDENT_ROLE_NOT_CONFIGURED, assertThrows(IdentityDomainException.class,
                () -> vacio.alta("Ana", "a@prisaly.test")).code());
        verifyNoInteractions(catalogo, estudiantes);
    }

    @Test
    void unFalloAlInsertarElEstudiantePropagaDentroDeLaTransaccion() {
        UUID usuario = UUID.randomUUID();
        when(catalogo.registrarSinCredencial(any(), any(), any())).thenReturn(new Usuario(usuario,
                new Rol(9, "ESTUDIANTE", null), "Ana", "a@prisaly.test", null, "Activo", OffsetDateTime.now(), null));
        doThrow(new IllegalStateException("fallo")).when(estudiantes).registrar(any(), any());
        assertThrows(IllegalStateException.class, () -> servicio.alta("Ana", "a@prisaly.test"));
        assertEquals(1, transacciones.get(), "the failure happens inside the transaction, so the user is rolled back");
    }

    @Test
    void busquedaDelegaTrasVerificarAdministrador() {
        var fila = new EstudianteRegistrado(UUID.randomUUID(), UUID.randomUUID(), "Ana", "a@prisaly.test", "Activo");
        when(estudiantes.buscarPorCorreo("A@prisaly.test")).thenReturn(List.of(fila));
        assertEquals(List.of(fila), servicio.buscarPorCorreo("A@prisaly.test"));
        verify(administracion).exigirAdministrador();
    }

    @Test
    void sinInterruptorNoHayBeansNiRutas() {
        for (Class<?> tipo : List.of(AltaEstudiantesConfig.class, AdministracionEstudiantesController.class)) {
            ConditionalOnProperty c = tipo.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(c, tipo.getName());
            assertEquals("prisaly.m1.alta-estudiantes.enabled", (c.name().length > 0 ? c.name() : c.value())[0]);
            assertEquals("true", c.havingValue());
            assertFalse(c.matchIfMissing());
        }
    }
}
