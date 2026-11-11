package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AsignacionPermisoRepositorioPort;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaPermisos;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DM1-04 (DC-M1-04): administrative operations (CU-02, CU-03). Every operation requires an authenticated, ACTIVE user
 * with the ADMINISTRADOR role; assignment rules stay in {@link GestionPermisosService} (DM1-02, DM1-02b).
 */
@Service
public class AdministracionIdentidadService {
    public static final String CODE_ADMIN_REQUIRED = "ADMIN_REQUIRED";
    public static final String CODE_TEACHER_ROLE_NOT_CONFIGURED = "TEACHER_ROLE_NOT_CONFIGURED";
    public static final String LOGGER_AUDITORIA = "prisaly.auditoria.identidad";
    private static final Logger auditoria = LoggerFactory.getLogger(LOGGER_AUDITORIA);

    private final IdentityAccessPort identidad;
    private final IdentityCatalogService catalogo;
    private final GestionPermisosService gestion;
    private final RolRepositorioPort roles;
    private final PermisoRepositorioPort permisos;
    private final UsuarioRepositorioPort usuarios;
    private final AsignacionPermisoRepositorioPort asignaciones;
    private final String rolDocente;

    public AdministracionIdentidadService(IdentityAccessPort identidad, IdentityCatalogService catalogo,
            GestionPermisosService gestion, RolRepositorioPort roles, PermisoRepositorioPort permisos,
            UsuarioRepositorioPort usuarios, AsignacionPermisoRepositorioPort asignaciones,
            @Value("${prisaly.m1.administracion.rol-docente:}") String rolDocente) {
        this.identidad = identidad;
        this.catalogo = catalogo;
        this.gestion = gestion;
        this.roles = roles;
        this.permisos = permisos;
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.rolDocente = rolDocente == null ? "" : rolDocente.trim();
    }

    /** The current request must come from an active administrator. Students and teachers are rejected (403). */
    public UUID exigirAdministrador() {
        AuthenticatedUser actual = identidad.currentUser();
        if (actual == null || actual.userId() == null) {
            throw new IdentityDomainException("UNAUTHENTICATED", "Authentication required");
        }
        if (!"Activo".equalsIgnoreCase(actual.accountStatus())
                || !PoliticaPermisos.ROL_ADMINISTRADOR.equals(actual.roleName())
                || usuarios.esCuentaEstudiante(actual.userId())) {
            throw new IdentityDomainException(CODE_ADMIN_REQUIRED, "Administrator required");
        }
        return actual.userId();
    }

    @Transactional(readOnly = true)
    public List<Rol> roles() {
        exigirAdministrador();
        return roles.listarRoles();
    }

    @Transactional(readOnly = true)
    public List<Permiso> permisos() {
        exigirAdministrador();
        return permisos.listarPermisos();
    }

    @Transactional(readOnly = true)
    public List<Permiso> permisosDeUsuario(UUID idUsuario) {
        exigirAdministrador();
        if (!usuarios.existePorId(idUsuario)) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_USER_NOT_FOUND, "User not found");
        }
        Set<String> codigos = asignaciones.codigosHabilitados(idUsuario);
        return permisos.listarPermisos().stream().filter(p -> codigos.contains(p.getCodigo()))
                .sorted(Comparator.comparing(Permiso::getIdPermiso)).toList();
    }

    public AsignacionPermiso asignar(UUID idUsuario, Integer idPermiso) {
        return gestion.asignar(idUsuario, idPermiso, exigirAdministrador());
    }

    public void revocar(UUID idUsuario, Integer idPermiso) {
        gestion.revocar(idUsuario, idPermiso, exigirAdministrador());
    }

    /**
     * CU-02 without a usable credential: no password is generated, returned or logged. Secure credential delivery is
     * pending (no mail or activation-token infrastructure approved); the account cannot authenticate until then.
     */
    public Usuario altaDocente(String nombre, String correo) {
        UUID admin = exigirAdministrador();
        if (rolDocente.isEmpty()) {
            throw new IdentityDomainException(CODE_TEACHER_ROLE_NOT_CONFIGURED, "Teacher role is not configured");
        }
        if (roles.buscarPorNombre(rolDocente).isEmpty()) {
            throw new IdentityDomainException(CODE_TEACHER_ROLE_NOT_CONFIGURED, "Configured teacher role does not exist");
        }
        Usuario creado = catalogo.registrarSinCredencial(nombre, correo, rolDocente);
        auditoria.info("AUDITORIA_IDENTIDAD accion=ALTA_DOCENTE usuario={} administrador={} credencial=PENDIENTE_ENTREGA",
                creado.getIdUsuario(), admin);
        return creado;
    }
}
