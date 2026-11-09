package com.prisaly.m1.application.service;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AsignacionPermisoRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.domain.model.Usuario;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccessService implements IdentityAccessPort {

    public static final String CODE_UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String CODE_USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String CODE_USER_INACTIVE = "USER_INACTIVE";

    private final UsuarioRepositorioPort usuarioRepository;
    private final AsignacionPermisoRepositorioPort usuarioPermisoRepository;

    public IdentityAccessService(
            UsuarioRepositorioPort usuarioRepository,
            AsignacionPermisoRepositorioPort usuarioPermisoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioPermisoRepository = usuarioPermisoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IdentityDomainException(CODE_UNAUTHENTICATED, "No authenticated user in request context");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UUID userId)) {
            throw new IdentityDomainException(CODE_UNAUTHENTICATED, "No authenticated user in request context");
        }
        return requireUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser requireUser(UUID userId) {
        Usuario usuario = usuarioRepository.buscarPorId(userId)
                .orElseThrow(() -> new IdentityDomainException(CODE_USER_NOT_FOUND, "User not found"));
        if (!usuario.isActive()) {
            throw new IdentityDomainException(CODE_USER_INACTIVE, "User account is inactive");
        }
        return toAuthenticatedUser(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String permissionCode) {
        AuthenticatedUser user = requireUser(userId);
        return user.permissionCodes().contains(permissionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRole(UUID userId, String roleName) {
        AuthenticatedUser user = requireUser(userId);
        return user.roleName().equalsIgnoreCase(roleName);
    }

    private AuthenticatedUser toAuthenticatedUser(Usuario usuario) {
        Set<String> permissions = usuarioPermisoRepository.codigosHabilitados(usuario.getIdUsuario());
        return new AuthenticatedUser(
                usuario.getIdUsuario(),
                usuario.getCorreo(),
                usuario.getNombre(),
                usuario.getRol().getNombre(),
                usuario.getEstado(),
                Set.copyOf(permissions));
    }
}
