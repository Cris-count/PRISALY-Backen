package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.RolEntity;
import com.prisaly.m1.infrastructure.persistence.RolRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import com.prisaly.m1.infrastructure.security.ProvisionalAccessTokenStore;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonnelAuthenticationService {

    public static final String CODE_AUTH_FAILED = "AUTH_FAILED";
    public static final String CODE_DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String CODE_ROLE_NOT_FOUND = "ROLE_NOT_FOUND";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProvisionalAccessTokenStore tokenStore;
    private final IdentityAccessService identityAccessService;

    public PersonnelAuthenticationService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            ProvisionalAccessTokenStore tokenStore,
            IdentityAccessService identityAccessService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.identityAccessService = identityAccessService;
    }

    @Transactional(readOnly = true)
    public AuthResult authenticate(String email, String rawPassword) {
        UsuarioEntity usuario = usuarioRepository.findByCorreoIgnoreCase(email).orElse(null);
        if (usuario == null || usuario.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, usuario.getPasswordHash())
                || !usuario.isActive()) {
            throw new IdentityDomainException(CODE_AUTH_FAILED, "Authentication failed");
        }
        String token = tokenStore.issue(usuario.getIdUsuario());
        AuthenticatedUser authenticated = identityAccessService.requireUser(usuario.getIdUsuario());
        return new AuthResult(token, authenticated);
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }

    @Transactional
    public AuthenticatedUser registerPersonnel(
            String nombre,
            String correo,
            String rawPassword,
            String roleName) {
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new IdentityDomainException(CODE_DUPLICATE_EMAIL, "Email already registered");
        }
        RolEntity rol = rolRepository.findByNombre(roleName)
                .orElseThrow(() -> new IdentityDomainException(CODE_ROLE_NOT_FOUND, "Role not found"));
        UsuarioEntity created = new UsuarioEntity(
                UUID.randomUUID(),
                rol,
                nombre,
                correo,
                passwordEncoder.encode(rawPassword),
                "Activo",
                OffsetDateTime.now());
        usuarioRepository.save(created);
        return identityAccessService.requireUser(created.getIdUsuario());
    }

    public record AuthResult(String accessToken, AuthenticatedUser user) {
    }
}
