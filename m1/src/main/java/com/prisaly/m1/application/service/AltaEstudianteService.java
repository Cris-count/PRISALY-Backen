package com.prisaly.m1.application.service;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.EstudianteRegistroPort;
import com.prisaly.m1.application.port.EstudianteRegistroPort.EstudianteRegistrado;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.domain.model.Usuario;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * P-78 (DC-BE-D78-1-AUTORIZACION-CONTROLADA-79, candidate, QA only): administrative registration of students on the
 * official schema (no schema change). Created only by {@code AltaEstudiantesConfig} with
 * {@code prisaly.m1.alta-estudiantes.enabled=true}.
 * <ul>
 *   <li>Every operation first requires an active administrator ({@link AdministracionIdentidadService#exigirAdministrador}).</li>
 *   <li>{@code "Usuario"} (configured student role, {@code Activo}, <b>no credential</b>) and {@code "Estudiante"} are
 *       created in one transaction: if the student row fails, no user remains.</li>
 *   <li>No password is generated, stored or returned; no mail is sent. Student access is only the DD-02 contract,
 *       which this class neither implements nor enables.</li>
 *   <li>Audited after commit on {@code prisaly.auditoria.identidad}.</li>
 * </ul>
 */
public class AltaEstudianteService {
    public static final String CODE_STUDENT_ROLE_NOT_CONFIGURED = "STUDENT_ROLE_NOT_CONFIGURED";
    private static final Logger auditoria = LoggerFactory.getLogger(AdministracionIdentidadService.LOGGER_AUDITORIA);

    private final AdministracionIdentidadService administracion;
    private final IdentityCatalogService catalogo;
    private final RolRepositorioPort roles;
    private final EstudianteRegistroPort estudiantes;
    private final TransactionTemplate transaccion;
    private final String rolEstudiante;

    public AltaEstudianteService(AdministracionIdentidadService administracion, IdentityCatalogService catalogo,
            RolRepositorioPort roles, EstudianteRegistroPort estudiantes, TransactionTemplate transaccion,
            String rolEstudiante) {
        this.administracion = Objects.requireNonNull(administracion, "administracion");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.estudiantes = Objects.requireNonNull(estudiantes, "estudiantes");
        this.transaccion = Objects.requireNonNull(transaccion, "transaccion");
        this.rolEstudiante = rolEstudiante == null ? "" : rolEstudiante.trim();
    }

    /** Registers a student; returns the created identifiers. */
    public EstudianteRegistrado alta(String nombre, String correo) {
        UUID admin = administracion.exigirAdministrador();
        if (rolEstudiante.isEmpty() || roles.buscarPorNombre(rolEstudiante).isEmpty()) {
            throw new IdentityDomainException(CODE_STUDENT_ROLE_NOT_CONFIGURED, "Student role is not configured");
        }
        return transaccion.execute(estado -> {
            Usuario usuario = catalogo.registrarSinCredencial(nombre, correo, rolEstudiante);
            UUID idEstudiante = UUID.randomUUID();
            estudiantes.registrar(idEstudiante, usuario.getIdUsuario());
            auditarAlConfirmar(usuario.getIdUsuario(), idEstudiante, admin);
            return new EstudianteRegistrado(usuario.getIdUsuario(), idEstudiante, usuario.getNombre(),
                    usuario.getCorreo(), usuario.getEstado());
        });
    }

    /** Exact lookup by email, so the teacher can be given the {@code idEstudiante} to convoke. */
    public List<EstudianteRegistrado> buscarPorCorreo(String correo) {
        administracion.exigirAdministrador();
        return estudiantes.buscarPorCorreo(correo);
    }

    private static void auditarAlConfirmar(UUID usuario, UUID estudiante, UUID admin) {
        Runnable linea = () -> auditoria.info(
                "AUDITORIA_IDENTIDAD accion=ALTA_ESTUDIANTE usuario={} estudiante={} administrador={} credencial=NINGUNA",
                usuario, estudiante, admin);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            linea.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                linea.run();
            }
        });
    }
}
