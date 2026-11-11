package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AsignacionPermisoRepositorioPort;
import com.prisaly.m1.application.port.AuditoriaPermisosPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaPermisos;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Asigna y revoca permisos aplicando PD-01 y dejando constancia de cada intento. */
@Service
public class GestionPermisosService {
    private static final String ASIGNAR = "ASIGNAR";
    private static final String REVOCAR = "REVOCAR";

    private final UsuarioRepositorioPort usuarios;
    private final PermisoRepositorioPort permisos;
    private final AsignacionPermisoRepositorioPort asignaciones;
    private final AuditoriaPermisosPort auditoria;

    public GestionPermisosService(UsuarioRepositorioPort usuarios, PermisoRepositorioPort permisos,
            AsignacionPermisoRepositorioPort asignaciones, AuditoriaPermisosPort auditoria) {
        this.usuarios = usuarios;
        this.permisos = permisos;
        this.asignaciones = asignaciones;
        this.auditoria = auditoria;
    }

    @Transactional
    public AsignacionPermiso asignar(UUID idUsuario, Integer idPermiso, UUID idActor) {
        try {
            return ejecutarAsignacion(idUsuario, idPermiso, idActor);
        } catch (IdentityDomainException rechazo) {
            auditoria.registrarRechazo(ASIGNAR, idUsuario, etiqueta(idPermiso), idActor, rechazo.code());
            throw rechazo;
        }
    }

    @Transactional
    public void revocar(UUID idUsuario, Integer idPermiso, UUID idActor) {
        try {
            ejecutarRevocacion(idUsuario, idPermiso, idActor);
        } catch (IdentityDomainException rechazo) {
            auditoria.registrarRechazo(REVOCAR, idUsuario, etiqueta(idPermiso), idActor, rechazo.code());
            throw rechazo;
        }
    }

    private AsignacionPermiso ejecutarAsignacion(UUID idUsuario, Integer idPermiso, UUID idActor) {
        if (!usuarios.existePorId(idUsuario)) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_USER_NOT_FOUND, "User not found");
        }
        String codigo = codigo(idPermiso);
        // DM1-02 (DC-M1-03): every assignment needs an active administrator, not only the PD-01 controlled set.
        exigirAdministrador(idActor);
        if (PoliticaPermisos.exigeSeparacionDeFunciones(codigo) && idUsuario.equals(idActor)) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_SELF_ASSIGNMENT,
                    "Assigning and executing must stay separate");
        }
        if (PoliticaPermisos.esControlado(codigo)) {
            if (usuarios.esCuentaEstudiante(idUsuario)) {
                throw new IdentityDomainException(PoliticaPermisos.CODE_STUDENT_NOT_ALLOWED,
                        "Students cannot receive this permission");
            }
        }
        if (asignaciones.existeHabilitada(idUsuario, idPermiso)) {
            throw duplicada();
        }
        if (idActor != null && !usuarios.existePorId(idActor)) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_USER_NOT_FOUND, "Assigner user not found");
        }
        try {
            // DC-M1-04: a revoked row (habilitado=false) is re-enabled instead of being reported as a duplicate.
            AsignacionPermiso guardada = asignaciones.existe(idUsuario, idPermiso)
                    ? asignaciones.reactivar(idUsuario, idPermiso, idActor, OffsetDateTime.now())
                    : asignaciones.registrar(idUsuario, idPermiso, idActor, OffsetDateTime.now());
            auditoria.registrarExitoAlConfirmar(ASIGNAR, idUsuario, codigo, idActor);
            return guardada;
        } catch (DataIntegrityViolationException ex) {
            throw duplicada();
        }
    }

    private void ejecutarRevocacion(UUID idUsuario, Integer idPermiso, UUID idActor) {
        String codigo = codigo(idPermiso);
        // DM1-02 (DC-M1-03): every revocation needs an active administrator as well.
        exigirAdministrador(idActor);
        if (!asignaciones.deshabilitar(idUsuario, idPermiso)) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_PERMISSION_NOT_FOUND,
                    "Permission assignment not found");
        }
        auditoria.registrarExitoAlConfirmar(REVOCAR, idUsuario, codigo, idActor);
    }

    private void exigirAdministrador(UUID actor) {
        if (actor == null) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_ACTOR_REQUIRED, "A responsible actor is required");
        }
        Usuario usuario = usuarios.buscarPorId(actor).orElseThrow(
                () -> new IdentityDomainException(PoliticaPermisos.CODE_USER_NOT_FOUND, "Actor user not found"));
        if (!usuario.isActive()) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_ACTOR_INVALID, "The responsible actor is not active");
        }
        if (!PoliticaPermisos.ROL_ADMINISTRADOR.equals(usuario.getRol().getNombre())) {
            throw new IdentityDomainException(PoliticaPermisos.CODE_ACTOR_NOT_ADMIN,
                    "The responsible actor is not an administrator");
        }
    }

    private String codigo(Integer idPermiso) {
        return permisos.buscarPorId(idPermiso).map(Permiso::getCodigo).orElseThrow(
                () -> new IdentityDomainException(PoliticaPermisos.CODE_PERMISSION_NOT_FOUND, "Permission not found"));
    }

    private String etiqueta(Integer idPermiso) {
        return permisos.buscarPorId(idPermiso).map(Permiso::getCodigo).orElse("id:" + idPermiso);
    }

    private static IdentityDomainException duplicada() {
        return new IdentityDomainException(PoliticaPermisos.CODE_DUPLICATE_ASSIGNMENT, "Permission already assigned");
    }
}
