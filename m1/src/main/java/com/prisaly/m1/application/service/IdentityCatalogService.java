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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public static final String CODE_ACTOR_REQUIRED = "PERMISSION_ACTOR_REQUIRED";
    public static final String CODE_ACTOR_INVALID = "PERMISSION_ACTOR_INVALID";
    public static final String CODE_STUDENT_NOT_ALLOWED = "PERMISSION_NOT_ALLOWED_FOR_STUDENT";

    /** PD-01: controlled, individually assigned permissions that students never receive. */
    private static final Set<String> PERMISOS_CONTROLADOS = Set.of("CONSULTAR_CASO", "MODIFICAR_CASO");
    private static final Logger log = LoggerFactory.getLogger(IdentityCatalogService.class);

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
        String codigo = codigoPermiso(idPermiso);
        if (PERMISOS_CONTROLADOS.contains(codigo)) {
            exigirActorActivo(idAdministradorAsignador);
            if (usuarioRepository.esCuentaEstudiante(idUsuario)) {
                throw new IdentityDomainException(CODE_STUDENT_NOT_ALLOWED, "Students cannot receive this permission");
            }
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
            UsuarioPermisoEntity saved = usuarioPermisoRepository.saveAndFlush(assignment);
            auditar("ASIGNAR", idUsuario, codigo, idAdministradorAsignador);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_ASSIGNMENT, "Permission already assigned");
        }
    }

    private String codigoPermiso(Integer idPermiso) {
        return permisoRepository.findById(idPermiso)
                .map(PermisoEntity::getCodigo)
                .orElseThrow(() -> new IdentityDomainException(CODE_PERMISSION_NOT_FOUND, "Permission not found"));
    }

    /** Which administrative role may act is pending: no such role exists in the catalog yet. */
    private void exigirActorActivo(UUID actor) {
        if (actor == null) {
            throw new IdentityDomainException(CODE_ACTOR_REQUIRED, "A responsible actor is required");
        }
        UsuarioEntity usuario = usuarioRepository.findById(actor)
                .orElseThrow(() -> new IdentityDomainException(CODE_USER_NOT_FOUND, "Actor user not found"));
        if (!usuario.isActive()) {
            throw new IdentityDomainException(CODE_ACTOR_INVALID, "The responsible actor is not active");
        }
    }

    private static void auditar(String accion, UUID usuario, String permiso, UUID actor) {
        log.info("AUDITORIA_PERMISO accion={} usuario={} permiso={} actor={} fecha={}",
                accion, usuario, permiso, actor, Instant.now());
    }

    @Transactional
    public void disablePermissionAssignment(UUID idUsuario, Integer idPermiso) {
        disablePermissionAssignment(idUsuario, idPermiso, null);
    }

    /** Controlled permissions require the responsible actor, recorded in the audit log. */
    @Transactional
    public void disablePermissionAssignment(UUID idUsuario, Integer idPermiso, UUID idActor) {
        String codigo = codigoPermiso(idPermiso);
        if (PERMISOS_CONTROLADOS.contains(codigo)) {
            exigirActorActivo(idActor);
        }
        UsuarioPermisoEntity assignment = usuarioPermisoRepository
                .findByIdUsuarioAndIdPermiso(idUsuario, idPermiso)
                .orElseThrow(() -> new IdentityDomainException(
                        CODE_PERMISSION_NOT_FOUND, "Permission assignment not found"));
        assignment.setHabilitado(false);
        usuarioPermisoRepository.saveAndFlush(assignment);
        auditar("REVOCAR", idUsuario, codigo, idActor);
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
