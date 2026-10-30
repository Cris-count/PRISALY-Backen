package com.prisaly.m1.application.service;

import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.infrastructure.persistence.PermisoRepository;
import com.prisaly.m1.infrastructure.persistence.RolEntity;
import com.prisaly.m1.infrastructure.persistence.RolRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioPermisoEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioPermisoRepository;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M1 identity catalog persistence: Rol, Permiso, Usuario, UsuarioPermiso.
 * Password hashes are stored with BCrypt; emails are normalized to lowercase.
 */
@Service
public class IdentityCatalogService {

    public static final String CODE_DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String CODE_DUPLICATE_ROLE = "DUPLICATE_ROLE";
    public static final String CODE_DUPLICATE_PERMISSION = "DUPLICATE_PERMISSION";
    public static final String CODE_DUPLICATE_ASSIGNMENT = "DUPLICATE_ASSIGNMENT";
    public static final String CODE_ROLE_NOT_FOUND = "ROLE_NOT_FOUND";
    public static final String CODE_USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String CODE_PERMISSION_NOT_FOUND = "PERMISSION_NOT_FOUND";

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioPermisoRepository usuarioPermisoRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityCatalogService(
            RolRepository rolRepository,
            PermisoRepository permisoRepository,
            UsuarioRepository usuarioRepository,
            UsuarioPermisoRepository usuarioPermisoRepository,
            PasswordEncoder passwordEncoder) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioPermisoRepository = usuarioPermisoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RolEntity createRole(String nombre, String descripcion) {
        if (rolRepository.findByNombre(nombre).isPresent()) {
            throw new IdentityDomainException(CODE_DUPLICATE_ROLE, "Role name already exists");
        }
        try {
            return rolRepository.saveAndFlush(new RolEntity(nombre, descripcion));
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_ROLE, "Role name already exists");
        }
    }

    @Transactional
    public PermisoEntity createPermission(String codigo, String nombre, String descripcion) {
        if (permisoRepository.findByCodigo(codigo).isPresent()) {
            throw new IdentityDomainException(CODE_DUPLICATE_PERMISSION, "Permission code already exists");
        }
        try {
            return permisoRepository.saveAndFlush(new PermisoEntity(codigo, nombre, descripcion));
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_PERMISSION, "Permission code already exists");
        }
    }

    @Transactional
    public UsuarioEntity registerUser(
            String nombre,
            String correo,
            String rawPassword,
            String roleName) {
        String normalizedEmail = normalizeEmail(correo);
        if (usuarioRepository.existsByCorreoIgnoreCase(normalizedEmail)) {
            throw new IdentityDomainException(CODE_DUPLICATE_EMAIL, "Email already registered");
        }
        RolEntity rol = rolRepository.findByNombre(roleName)
                .orElseThrow(() -> new IdentityDomainException(CODE_ROLE_NOT_FOUND, "Role not found"));
        UsuarioEntity created = new UsuarioEntity(
                UUID.randomUUID(),
                rol,
                nombre,
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                "Activo",
                OffsetDateTime.now());
        try {
            return usuarioRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_EMAIL, "Email already registered");
        }
    }

    @Transactional
    public UsuarioPermisoEntity assignPermission(
            UUID idUsuario,
            Integer idPermiso,
            UUID idAdministradorAsignador) {
        if (!usuarioRepository.existsById(idUsuario)) {
            throw new IdentityDomainException(CODE_USER_NOT_FOUND, "User not found");
        }
        if (!permisoRepository.existsById(idPermiso)) {
            throw new IdentityDomainException(CODE_PERMISSION_NOT_FOUND, "Permission not found");
        }
        if (usuarioPermisoRepository.existsByIdUsuarioAndIdPermiso(idUsuario, idPermiso)) {
            throw new IdentityDomainException(CODE_DUPLICATE_ASSIGNMENT, "Permission already assigned");
        }
        if (idAdministradorAsignador != null && !usuarioRepository.existsById(idAdministradorAsignador)) {
            throw new IdentityDomainException(CODE_USER_NOT_FOUND, "Assigner user not found");
        }
        UsuarioPermisoEntity assignment = new UsuarioPermisoEntity(
                idUsuario,
                idPermiso,
                true,
                idAdministradorAsignador,
                OffsetDateTime.now());
        try {
            return usuarioPermisoRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_ASSIGNMENT, "Permission already assigned");
        }
    }

    @Transactional
    public void disablePermissionAssignment(UUID idUsuario, Integer idPermiso) {
        UsuarioPermisoEntity assignment = usuarioPermisoRepository
                .findByIdUsuarioAndIdPermiso(idUsuario, idPermiso)
                .orElseThrow(() -> new IdentityDomainException(
                        CODE_PERMISSION_NOT_FOUND, "Permission assignment not found"));
        assignment.setHabilitado(false);
        usuarioPermisoRepository.saveAndFlush(assignment);
    }

    @Transactional
    public void deactivateUser(UUID idUsuario) {
        UsuarioEntity usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IdentityDomainException(CODE_USER_NOT_FOUND, "User not found"));
        usuario.changeEstado("Inactivo", OffsetDateTime.now());
        usuarioRepository.saveAndFlush(usuario);
    }

    public static String normalizeEmail(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("correo is required");
        }
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
