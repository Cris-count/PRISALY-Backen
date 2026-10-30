package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.infrastructure.persistence.RolEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioPermisoEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioPermisoRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityAccessServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioPermisoRepository usuarioPermisoRepository;

    private IdentityAccessService service;

    @BeforeEach
    void setUp() {
        service = new IdentityAccessService(usuarioRepository, usuarioPermisoRepository);
    }

    @Test
    void requireUser_inactive_throwsForbiddenCode() {
        UUID userId = UUID.randomUUID();
        RolEntity rol = new RolEntity("Docente", "Docente");
        UsuarioEntity usuario = new UsuarioEntity(
                userId, rol, "Ana", "ana@prisaly.test", "hash", "Inactivo", OffsetDateTime.now());
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.requireUser(userId));

        assertEquals(IdentityAccessService.CODE_USER_INACTIVE, error.code());
    }

    @Test
    void hasPermission_respectsEnabledAssignmentsOnly() {
        UUID userId = UUID.randomUUID();
        RolEntity rol = new RolEntity("Docente", "Docente");
        UsuarioEntity usuario = new UsuarioEntity(
                userId, rol, "Ana", "ana@prisaly.test", "hash", "Activo", OffsetDateTime.now());
        PermisoEntity permiso = new PermisoEntity("CREAR_CASO", "Crear", null);
        UsuarioPermisoEntity assignment = mock(UsuarioPermisoEntity.class);
        when(assignment.getPermiso()).thenReturn(permiso);
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));
        when(usuarioPermisoRepository.findByIdUsuarioAndHabilitadoTrue(userId))
                .thenReturn(List.of(assignment));

        assertTrue(service.hasPermission(userId, "CREAR_CASO"));
        assertFalse(service.hasPermission(userId, "OTRO"));
        AuthenticatedUser authenticated = service.requireUser(userId);
        assertEquals("Docente", authenticated.roleName());
        assertTrue(authenticated.permissionCodes().contains("CREAR_CASO"));
    }
}
