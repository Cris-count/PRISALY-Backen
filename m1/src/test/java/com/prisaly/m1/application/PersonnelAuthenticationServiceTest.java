package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.application.service.PersonnelAuthenticationService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.RolEntity;
import com.prisaly.m1.infrastructure.persistence.RolRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import com.prisaly.m1.infrastructure.security.ProvisionalAccessTokenStore;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PersonnelAuthenticationServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private IdentityAccessService identityAccessService;

    private ProvisionalAccessTokenStore tokenStore;
    private PersonnelAuthenticationService service;

    @BeforeEach
    void setUp() {
        tokenStore = new ProvisionalAccessTokenStore();
        service = new PersonnelAuthenticationService(
                usuarioRepository, rolRepository, passwordEncoder, tokenStore, identityAccessService);
    }

    @Test
    void m1T01_validCredentials_authenticate() {
        UUID userId = UUID.randomUUID();
        RolEntity rol = new RolEntity("Docente", "Docente");
        UsuarioEntity usuario = new UsuarioEntity(
                userId, rol, "Ana", "ana@prisaly.test", "hash", "Activo", OffsetDateTime.now());
        when(usuarioRepository.findByCorreoIgnoreCase("ana@prisaly.test")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(identityAccessService.requireUser(userId))
                .thenReturn(new AuthenticatedUser(
                        userId, "ana@prisaly.test", "Ana", "Docente", "Activo", Set.of("CREAR_CASO")));

        PersonnelAuthenticationService.AuthResult result = service.authenticate("ana@prisaly.test", "secret");

        assertEquals(userId, result.user().userId());
        assertEquals(userId, tokenStore.resolveActiveUserId(result.accessToken()).orElseThrow());
    }

    @Test
    void m1T02_wrongPassword_rejectsSafely() {
        UUID userId = UUID.randomUUID();
        RolEntity rol = new RolEntity("Docente", "Docente");
        UsuarioEntity usuario = new UsuarioEntity(
                userId, rol, "Ana", "ana@prisaly.test", "hash", "Activo", OffsetDateTime.now());
        when(usuarioRepository.findByCorreoIgnoreCase("ana@prisaly.test")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.authenticate("ana@prisaly.test", "bad"));

        assertEquals(PersonnelAuthenticationService.CODE_AUTH_FAILED, error.code());
    }

    @Test
    void m1T03_unknownUser_sameFailureCode() {
        when(usuarioRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.authenticate("ghost@prisaly.test", "x"));

        assertEquals(PersonnelAuthenticationService.CODE_AUTH_FAILED, error.code());
    }

    @Test
    void m1T04_duplicateEmail_rejected() {
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@prisaly.test")).thenReturn(true);

        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                () -> service.registerPersonnel("Ana", "ana@prisaly.test", "secret", "Docente"));

        assertEquals(PersonnelAuthenticationService.CODE_DUPLICATE_EMAIL, error.code());
    }

    @Test
    void logout_revokesToken() {
        UUID userId = UUID.randomUUID();
        String token = tokenStore.issue(userId);
        service.logout(token);
        assertEquals(Optional.empty(), tokenStore.resolveActiveUserId(token));
        verify(usuarioRepository, org.mockito.Mockito.never()).save(any());
    }
}
