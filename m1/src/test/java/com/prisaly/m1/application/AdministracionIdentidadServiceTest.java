package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AsignacionPermisoRepositorioPort;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.application.service.AdministracionIdentidadService;
import com.prisaly.m1.application.service.GestionPermisosService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Rol;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DM1-04 (DC-M1-04): only an active ADMINISTRADOR (never a student account) reaches the administrative operations. */
class AdministracionIdentidadServiceTest {
    private static final UUID ADMIN = UUID.randomUUID(), DOCENTE = UUID.randomUUID();
    private final IdentityAccessPort identidad = mock(IdentityAccessPort.class);
    private final IdentityCatalogService catalogo = mock(IdentityCatalogService.class);
    private final GestionPermisosService gestion = mock(GestionPermisosService.class);
    private final RolRepositorioPort roles = mock(RolRepositorioPort.class);
    private final PermisoRepositorioPort permisos = mock(PermisoRepositorioPort.class);
    private final UsuarioRepositorioPort usuarios = mock(UsuarioRepositorioPort.class);
    private final AsignacionPermisoRepositorioPort asignaciones = mock(AsignacionPermisoRepositorioPort.class);

    private AdministracionIdentidadService servicio(String rolDocente) {
        return new AdministracionIdentidadService(identidad, catalogo, gestion, roles, permisos, usuarios, asignaciones, rolDocente);
    }

    private void actual(String rol, String estado) {
        when(identidad.currentUser()).thenReturn(new AuthenticatedUser(ADMIN, "x@prisaly.test", "X", rol, estado, Set.of()));
    }

    private String codigo(Runnable accion) {
        return assertThrows(IdentityDomainException.class, accion::run).code();
    }

    @Test
    void sinIdentidadEsNoAutenticado() {
        assertEquals("UNAUTHENTICATED", codigo(() -> servicio("").roles()));
    }

    @Test
    void docenteAdministradorInactivoYEstudianteSonRechazados() {
        actual("DocenteBruno", "Activo");
        assertEquals(AdministracionIdentidadService.CODE_ADMIN_REQUIRED, codigo(() -> servicio("").permisos()));
        actual("ADMINISTRADOR", "Inactivo");
        assertEquals(AdministracionIdentidadService.CODE_ADMIN_REQUIRED, codigo(() -> servicio("").permisos()));
        actual("ADMINISTRADOR", "Activo");
        when(usuarios.esCuentaEstudiante(ADMIN)).thenReturn(true);
        assertEquals(AdministracionIdentidadService.CODE_ADMIN_REQUIRED, codigo(() -> servicio("").asignar(DOCENTE, 1)));
        verifyNoInteractions(gestion, permisos);
    }

    @Test
    void asignarYRevocarUsanAlAdministradorActualComoActor() {
        actual("ADMINISTRADOR", "Activo");
        servicio("").asignar(DOCENTE, 7);
        servicio("").revocar(DOCENTE, 7);
        verify(gestion).asignar(DOCENTE, 7, ADMIN);
        verify(gestion).revocar(DOCENTE, 7, ADMIN);
    }

    @Test
    void permisosDeUsuarioInexistenteEsNoEncontrado() {
        actual("ADMINISTRADOR", "Activo");
        assertEquals("USER_NOT_FOUND", codigo(() -> servicio("").permisosDeUsuario(DOCENTE)));
    }

    @Test
    void permisosDeUsuarioSoloDevuelveLosHabilitados() {
        actual("ADMINISTRADOR", "Activo");
        when(usuarios.existePorId(DOCENTE)).thenReturn(true);
        when(asignaciones.codigosHabilitados(DOCENTE)).thenReturn(Set.of("CREAR_CASO"));
        when(permisos.listarPermisos()).thenReturn(List.of(new Permiso(1, "CREAR_CASO", "Crear caso", null),
                new Permiso(2, "GESTIONAR_SESION", "Gestionar sesion", null)));
        assertEquals(List.of(1), servicio("").permisosDeUsuario(DOCENTE).stream().map(Permiso::getIdPermiso).toList());
    }

    @Test
    void altaDeDocenteExigeRolConfiguradoYExistenteYNoGeneraCredencial() {
        actual("ADMINISTRADOR", "Activo");
        assertEquals(AdministracionIdentidadService.CODE_TEACHER_ROLE_NOT_CONFIGURED,
                codigo(() -> servicio(" ").altaDocente("Ana", "ana@prisaly.test")));
        when(roles.buscarPorNombre("DocenteRol")).thenReturn(Optional.empty());
        assertEquals(AdministracionIdentidadService.CODE_TEACHER_ROLE_NOT_CONFIGURED,
                codigo(() -> servicio("DocenteRol").altaDocente("Ana", "ana@prisaly.test")));
        verify(catalogo, never()).registrarSinCredencial(any(), any(), any());
        when(roles.buscarPorNombre("DocenteRol")).thenReturn(Optional.of(new Rol(3, "DocenteRol", "d")));
        when(catalogo.registrarSinCredencial("Ana", "ana@prisaly.test", "DocenteRol")).thenReturn(
                new com.prisaly.m1.domain.model.Usuario(DOCENTE, new Rol(3, "DocenteRol", "d"), "Ana", "ana@prisaly.test",
                        null, "Activo", java.time.OffsetDateTime.now(), null));
        assertEquals(DOCENTE, servicio("DocenteRol").altaDocente("Ana", "ana@prisaly.test").getIdUsuario());
        verify(catalogo, never()).registerUser(any(), any(), any(), any());
    }
}
