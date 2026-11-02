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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class IdentityAccessServiceTest {

    private static final String PERMISO_GESTIONAR_SESION = "GESTIONAR_SESION";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioPermisoRepository usuarioPermisoRepository;

    private IdentityAccessService service;

    @BeforeEach
    void setUp() {
        service = new IdentityAccessService(usuarioRepository, usuarioPermisoRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireUser_inactive_throwsForbiddenCode() {
        UUID userId = UUID.randomUUID();
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId, "Inactivo")));

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.requireUser(userId));

        assertEquals(IdentityAccessService.CODE_USER_INACTIVE, error.code());
    }

    @Test
    void requireUser_unknown_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(usuarioRepository.findById(userId)).thenReturn(Optional.empty());

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.requireUser(userId));

        assertEquals(IdentityAccessService.CODE_USER_NOT_FOUND, error.code());
    }

    @Test
    void currentUser_withoutAuthentication_throwsUnauthenticated() {
        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.currentUser());

        assertEquals(IdentityAccessService.CODE_UNAUTHENTICATED, error.code());
    }

    @Test
    void currentUser_withNonUuidPrincipal_throwsUnauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-uuid", null, List.of()));

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.currentUser());

        assertEquals(IdentityAccessService.CODE_UNAUTHENTICATED, error.code());
    }

    @Test
    void currentUser_withUuidPrincipal_returnsPersistedIdentity() {
        UUID userId = UUID.randomUUID();
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId, "Activo")));
        when(usuarioPermisoRepository.findByIdUsuarioAndHabilitadoTrue(userId)).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        AuthenticatedUser authenticated = service.currentUser();

        assertEquals(userId, authenticated.userId());
        assertEquals("Docente", authenticated.roleName());
    }

    @Test
    void hasPermission_gestionarSesion_respectsEnabledAssignmentsOnly() {
        UUID userId = UUID.randomUUID();
        PermisoEntity permiso = new PermisoEntity(PERMISO_GESTIONAR_SESION, "Gestionar sesión", null);
        UsuarioPermisoEntity assignment = mock(UsuarioPermisoEntity.class);
        when(assignment.getPermiso()).thenReturn(permiso);
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId, "Activo")));
        when(usuarioPermisoRepository.findByIdUsuarioAndHabilitadoTrue(userId))
                .thenReturn(List.of(assignment));

        assertTrue(service.hasPermission(userId, PERMISO_GESTIONAR_SESION));
        assertFalse(service.hasPermission(userId, "CREAR_CASO"));
        assertTrue(service.requireUser(userId).permissionCodes().contains(PERMISO_GESTIONAR_SESION));
    }

    @Test
    void hasPermission_withoutAssignment_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId, "Activo")));
        when(usuarioPermisoRepository.findByIdUsuarioAndHabilitadoTrue(userId)).thenReturn(List.of());

        assertFalse(service.hasPermission(userId, PERMISO_GESTIONAR_SESION));
    }

    @Test
    void hasRole_matchesRoleNameIgnoreCase() {
        UUID userId = UUID.randomUUID();
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario(userId, "Activo")));
        when(usuarioPermisoRepository.findByIdUsuarioAndHabilitadoTrue(userId)).thenReturn(List.of());

        assertTrue(service.hasRole(userId, "docente"));
        assertFalse(service.hasRole(userId, "Administrador"));
    }

    private static UsuarioEntity usuario(UUID userId, String estado) {
        RolEntity rol = new RolEntity("Docente", "Docente");
        return new UsuarioEntity(
                userId, rol, "Ana", "ana@prisaly.test", "hash", estado, OffsetDateTime.now());
    }
}
