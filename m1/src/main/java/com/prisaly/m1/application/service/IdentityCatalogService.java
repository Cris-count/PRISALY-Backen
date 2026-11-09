package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AuditoriaPermisosPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaPermisos;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de roles, permisos y cuentas. Las reglas de asignación viven en
 * {@link GestionPermisosService}; los dos métodos de asignación son su punto de entrada estable.
 */
@Service
public class IdentityCatalogService {

    public static final String CODE_DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String CODE_DUPLICATE_ROLE = "DUPLICATE_ROLE";
    public static final String CODE_DUPLICATE_PERMISSION = "DUPLICATE_PERMISSION";
    public static final String CODE_DUPLICATE_ASSIGNMENT = PoliticaPermisos.CODE_DUPLICATE_ASSIGNMENT;
    public static final String CODE_ROLE_NOT_FOUND = "ROLE_NOT_FOUND";
    public static final String CODE_USER_NOT_FOUND = PoliticaPermisos.CODE_USER_NOT_FOUND;
    public static final String CODE_PERMISSION_NOT_FOUND = PoliticaPermisos.CODE_PERMISSION_NOT_FOUND;
    public static final String CODE_ACTOR_REQUIRED = PoliticaPermisos.CODE_ACTOR_REQUIRED;
    public static final String CODE_ACTOR_INVALID = PoliticaPermisos.CODE_ACTOR_INVALID;
    public static final String CODE_ACTOR_NOT_ADMIN = PoliticaPermisos.CODE_ACTOR_NOT_ADMIN;
    public static final String CODE_STUDENT_NOT_ALLOWED = PoliticaPermisos.CODE_STUDENT_NOT_ALLOWED;
    public static final String CODE_SELF_ASSIGNMENT = PoliticaPermisos.CODE_SELF_ASSIGNMENT;
    public static final String ROL_ADMINISTRADOR = PoliticaPermisos.ROL_ADMINISTRADOR;
    public static final String LOGGER_AUDITORIA = AuditoriaPermisosPort.LOGGER;

    private static final String ESTADO_ACTIVO = "Activo";
    private static final String ESTADO_INACTIVO = "Inactivo";

    private final RolRepositorioPort roles;
    private final PermisoRepositorioPort permisos;
    private final UsuarioRepositorioPort usuarios;
    private final GestionPermisosService gestionPermisos;
    private final PasswordEncoder passwordEncoder;

    public IdentityCatalogService(RolRepositorioPort roles, PermisoRepositorioPort permisos,
            UsuarioRepositorioPort usuarios, GestionPermisosService gestionPermisos, PasswordEncoder passwordEncoder) {
        this.roles = roles;
        this.permisos = permisos;
        this.usuarios = usuarios;
        this.gestionPermisos = gestionPermisos;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Rol createRole(String nombre, String descripcion) {
        if (roles.buscarPorNombre(nombre).isPresent()) {
            throw new IdentityDomainException(CODE_DUPLICATE_ROLE, "Role name already exists");
        }
        try {
            return roles.registrar(nombre, descripcion);
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_ROLE, "Role name already exists");
        }
    }

    @Transactional
    public Permiso createPermission(String codigo, String nombre, String descripcion) {
        if (permisos.buscarPorCodigo(codigo).isPresent()) {
            throw new IdentityDomainException(CODE_DUPLICATE_PERMISSION, "Permission code already exists");
        }
        try {
            return permisos.registrar(codigo, nombre, descripcion);
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_PERMISSION, "Permission code already exists");
        }
    }

    @Transactional
    public Usuario registerUser(String nombre, String correo, String rawPassword, String roleName) {
        String normalizedEmail = normalizeEmail(correo);
        if (usuarios.existePorCorreo(normalizedEmail)) {
            throw new IdentityDomainException(CODE_DUPLICATE_EMAIL, "Email already registered");
        }
        Rol rol = roles.buscarPorNombre(roleName)
                .orElseThrow(() -> new IdentityDomainException(CODE_ROLE_NOT_FOUND, "Role not found"));
        Usuario nuevo = new Usuario(UUID.randomUUID(), rol, nombre, normalizedEmail,
                passwordEncoder.encode(rawPassword), ESTADO_ACTIVO, OffsetDateTime.now(), null);
        try {
            return usuarios.registrar(nuevo);
        } catch (DataIntegrityViolationException ex) {
            throw new IdentityDomainException(CODE_DUPLICATE_EMAIL, "Email already registered");
        }
    }

    public AsignacionPermiso assignPermission(UUID idUsuario, Integer idPermiso, UUID idAdministradorAsignador) {
        return gestionPermisos.asignar(idUsuario, idPermiso, idAdministradorAsignador);
    }

    public void disablePermissionAssignment(UUID idUsuario, Integer idPermiso) {
        gestionPermisos.revocar(idUsuario, idPermiso, null);
    }

    public void disablePermissionAssignment(UUID idUsuario, Integer idPermiso, UUID idActor) {
        gestionPermisos.revocar(idUsuario, idPermiso, idActor);
    }

    /** Indica si este ambiente tiene inicializado el rol administrativo. */
    @Transactional(readOnly = true)
    public boolean existeRolAdministrador() {
        return roles.buscarPorNombre(ROL_ADMINISTRADOR).isPresent();
    }

    @Transactional
    public void deactivateUser(UUID idUsuario) {
        if (!usuarios.existePorId(idUsuario)) {
            throw new IdentityDomainException(CODE_USER_NOT_FOUND, "User not found");
        }
        usuarios.cambiarEstado(idUsuario, ESTADO_INACTIVO, OffsetDateTime.now());
    }

    public static String normalizeEmail(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("correo is required");
        }
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
