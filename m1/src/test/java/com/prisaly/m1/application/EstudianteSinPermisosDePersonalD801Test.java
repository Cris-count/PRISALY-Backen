package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AsignacionPermisoRepositorioPort;
import com.prisaly.m1.application.port.AuditoriaPermisosPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.application.service.GestionPermisosService;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaPermisos;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * D-80-1 (DC-BE-CIERRE-SEGURIDAD-INTEGRACION-81): a student account receives NO personnel permission (deny by default,
 * any present or future code) and its effective authorization carries none even with pre-existing assignments.
 * Administrators and teachers keep their authorized behaviour.
 */
class EstudianteSinPermisosDePersonalD801Test {
    private static final UUID ESTUDIANTE = UUID.randomUUID(), DOCENTE = UUID.randomUUID(), ADMIN = UUID.randomUUID(),
            ADMIN_ESTUDIANTE = UUID.randomUUID();
    private static final Rol ADMINISTRADOR = new Rol(1, PoliticaPermisos.ROL_ADMINISTRADOR, null);
    private static final Rol DOCENTE_ROL = new Rol(2, "DOCENTE", null);
    private static final Rol ESTUDIANTE_ROL = new Rol(3, "ESTUDIANTE", null);
    /** The six PD-01/DM1-03 codes plus a code that does not exist yet in any catalog (future permission). */
    private static final String[] CODIGOS = {"CREAR_CASO", "GESTIONAR_SESION", "CONSULTAR_CASO", "MODIFICAR_CASO",
        "PUBLICAR_CASO", "ARCHIVAR_CASO", "PERMISO_FUTURO_D801"};

    private final UsuarioRepositorioPort usuarios = mock(UsuarioRepositorioPort.class);
    private final PermisoRepositorioPort permisos = mock(PermisoRepositorioPort.class);
    private final AsignacionPermisoRepositorioPort asignaciones = mock(AsignacionPermisoRepositorioPort.class);
    private final AuditoriaPermisosPort auditoria = mock(AuditoriaPermisosPort.class);
    private final GestionPermisosService gestion = new GestionPermisosService(usuarios, permisos, asignaciones, auditoria);
    private final IdentityAccessService acceso = new IdentityAccessService(usuarios, asignaciones);

    @BeforeEach
    void catalogo() {
        cuenta(ESTUDIANTE, ESTUDIANTE_ROL, true);
        cuenta(DOCENTE, DOCENTE_ROL, false);
        cuenta(ADMIN, ADMINISTRADOR, false);
        cuenta(ADMIN_ESTUDIANTE, ADMINISTRADOR, true); // inconsistent data: an ADMINISTRADOR row linked to a student
        for (int i = 0; i < CODIGOS.length; i++) {
            when(permisos.buscarPorId(i + 1)).thenReturn(Optional.of(new Permiso(i + 1, CODIGOS[i], CODIGOS[i], null)));
        }
        when(asignaciones.registrar(any(), any(), any(), any())).thenAnswer(inv -> new AsignacionPermiso(
                inv.getArgument(0), inv.getArgument(1), true, inv.getArgument(2), OffsetDateTime.now()));
        when(asignaciones.deshabilitar(any(), any())).thenReturn(true);
    }

    private void cuenta(UUID id, Rol rol, boolean estudiante) {
        when(usuarios.existePorId(id)).thenReturn(true);
        when(usuarios.buscarPorId(id)).thenReturn(Optional.of(
                new Usuario(id, rol, "U", id + "@prisaly.test", "hash", "Activo", OffsetDateTime.now(), null)));
        when(usuarios.esCuentaEstudiante(id)).thenReturn(estudiante);
    }

    @Test
    void unEstudianteNoRecibeNingunPermisoNiSiquieraUnoFuturo() {
        for (int i = 0; i < CODIGOS.length; i++) {
            int idPermiso = i + 1;
            var error = assertThrows(IdentityDomainException.class, () -> gestion.asignar(ESTUDIANTE, idPermiso, ADMIN),
                    CODIGOS[i]);
            assertEquals(PoliticaPermisos.CODE_STUDENT_NOT_ALLOWED, error.code(), CODIGOS[i]);
            verify(auditoria).registrarRechazo("ASIGNAR", ESTUDIANTE, CODIGOS[i], ADMIN,
                    PoliticaPermisos.CODE_STUDENT_NOT_ALLOWED);
        }
        // A rejected attempt writes nothing.
        verify(asignaciones, never()).registrar(any(), any(), any(), any());
        verify(asignaciones, never()).reactivar(any(), any(), any(), any());
        verify(auditoria, never()).registrarExitoAlConfirmar(any(), any(), any(), any());
    }

    @Test
    void unEstudianteNoActuaComoAdministradorAsignador() {
        var error = assertThrows(IdentityDomainException.class, () -> gestion.asignar(DOCENTE, 1, ADMIN_ESTUDIANTE));
        assertEquals(PoliticaPermisos.CODE_ACTOR_NOT_ADMIN, error.code());
        verify(asignaciones, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void elDocenteSigueRecibiendoLosPermisosAutorizadosDelAdministrador() {
        for (String codigo : new String[] {"CREAR_CASO", "GESTIONAR_SESION", "CONSULTAR_CASO", "PERMISO_FUTURO_D801"}) {
            int idPermiso = java.util.Arrays.asList(CODIGOS).indexOf(codigo) + 1;
            assertTrue(gestion.asignar(DOCENTE, idPermiso, ADMIN).isHabilitado(), codigo);
            verify(auditoria).registrarExitoAlConfirmar("ASIGNAR", DOCENTE, codigo, ADMIN);
        }
    }

    @Test
    void autorizacionEfectivaIgnoraAsignacionesPreexistentesDeUnEstudiante() {
        when(asignaciones.codigosHabilitados(ESTUDIANTE)).thenReturn(Set.of("GESTIONAR_SESION", "CREAR_CASO"));
        assertEquals(Set.of(), acceso.requireUser(ESTUDIANTE).permissionCodes());
        assertFalse(acceso.hasPermission(ESTUDIANTE, "GESTIONAR_SESION"));
        assertFalse(acceso.hasPermission(ESTUDIANTE, "CREAR_CASO"));
        verify(asignaciones, never()).codigosHabilitados(ESTUDIANTE);
    }

    @Test
    void autorizacionEfectivaDeDocenteYAdministradorNoCambia() {
        when(asignaciones.codigosHabilitados(DOCENTE)).thenReturn(Set.of("GESTIONAR_SESION", "CREAR_CASO"));
        when(asignaciones.codigosHabilitados(ADMIN)).thenReturn(Set.of());
        assertEquals(Set.of("GESTIONAR_SESION", "CREAR_CASO"), acceso.requireUser(DOCENTE).permissionCodes());
        assertTrue(acceso.hasPermission(DOCENTE, "GESTIONAR_SESION"));
        assertEquals(PoliticaPermisos.ROL_ADMINISTRADOR, acceso.requireUser(ADMIN).roleName());
    }

    @Test
    void unaAsignacionPreexistenteDeEstudiantePuedeRevocarse() {
        gestion.revocar(ESTUDIANTE, 2, ADMIN);
        verify(asignaciones).deshabilitar(ESTUDIANTE, 2);
        verify(auditoria).registrarExitoAlConfirmar("REVOCAR", ESTUDIANTE, "GESTIONAR_SESION", ADMIN);
    }
}
