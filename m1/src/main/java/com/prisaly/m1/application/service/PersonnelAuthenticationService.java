package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.EmisorTokenAccesoPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.domain.model.Usuario;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonnelAuthenticationService {

    public static final String CODE_AUTH_FAILED = "AUTH_FAILED";
    public static final String CODE_DUPLICATE_EMAIL = IdentityCatalogService.CODE_DUPLICATE_EMAIL;
    public static final String CODE_ROLE_NOT_FOUND = IdentityCatalogService.CODE_ROLE_NOT_FOUND;

    private final UsuarioRepositorioPort usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmisorTokenAccesoPort tokenStore;
    private final IdentityAccessService identityAccessService;
    private final IdentityCatalogService identityCatalogService;
    private volatile String referenceHash;

    public PersonnelAuthenticationService(
            UsuarioRepositorioPort usuarioRepository,
            PasswordEncoder passwordEncoder,
            EmisorTokenAccesoPort tokenStore,
            IdentityAccessService identityAccessService,
            IdentityCatalogService identityCatalogService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.identityAccessService = identityAccessService;
        this.identityCatalogService = identityCatalogService;
    }

    @Transactional(readOnly = true)
    public AuthResult authenticate(String email, String rawPassword) {
        String normalizedEmail = IdentityCatalogService.normalizeEmail(email);
        Usuario usuario = usuarioRepository.buscarPorCorreo(normalizedEmail).orElse(null);
        String hash = usuario == null ? null : usuario.getPasswordHash();
        // H-01: the hash comparison always runs, so response time does not reveal whether the account exists.
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hash != null ? hash : referenceHash());
        if (hash == null || !passwordMatches || !usuario.isActive()) {
            throw new IdentityDomainException(CODE_AUTH_FAILED, "Authentication failed");
        }
        String token = tokenStore.issue(usuario.getIdUsuario());
        AuthenticatedUser authenticated = identityAccessService.requireUser(usuario.getIdUsuario());
        return new AuthResult(token, authenticated);
    }

    /** Hash of a random secret, encoded once with the configured encoder; it never matches a real password. */
    private String referenceHash() {
        String hash = referenceHash;
        if (hash == null) {
            synchronized (this) {
                hash = referenceHash;
                if (hash == null) {
                    byte[] secret = new byte[32];
                    new SecureRandom().nextBytes(secret);
                    hash = passwordEncoder.encode(Base64.getEncoder().encodeToString(secret));
                    referenceHash = hash;
                }
            }
        }
        return hash;
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
        Usuario created = identityCatalogService.registerUser(nombre, correo, rawPassword, roleName);
        return identityAccessService.requireUser(created.getIdUsuario());
    }

    public record AuthResult(String accessToken, AuthenticatedUser user) {
    }
}
