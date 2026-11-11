package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AsignacionPermisoRepositorioPort;
import com.prisaly.m1.application.port.AuditoriaPermisosPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.application.service.GestionPermisosService;
import com.prisaly.m1.domain.model.AsignacionPermiso;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaPermisos;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** DM1-02 (DC-M1-03): every assignment and revocation requires an existing, active administrator. */
class GestionPermisosServiceTest {
    private static final UUID DOCENTE = UUID.randomUUID(), ADMIN = UUID.randomUUID(), OTRO = UUID.randomUUID(),
            INACTIVO = UUID.randomUUID(), AUSENTE = UUID.randomUUID();
    private static final Rol ADMINISTRADOR = new Rol(1, PoliticaPermisos.ROL_ADMINISTRADOR, null);
    private static final Rol DOCENTE_ROL = new Rol(2, "DOCENTE", null);
    private static final String[] CODIGOS = {"CREAR_CASO", "GESTIONAR_SESION", "CONSULTAR_CASO", "MODIFICAR_CASO",
        "PUBLICAR_CASO", "ARCHIVAR_CASO"};

    private final UsuarioRepositorioPort usuarios = mock(UsuarioRepositorioPort.class);
    private final PermisoRepositorioPort permisos = mock(PermisoRepositorioPort.class);
    private final AsignacionPermisoRepositorioPort asignaciones = mock(AsignacionPermisoRepositorioPort.class);
    private final AuditoriaPermisosPort auditoria = mock(AuditoriaPermisosPort.class);
    private final GestionPermisosService servicio = new GestionPermisosService(usuarios, permisos, asignaciones, auditoria);

    @BeforeEach
    void catalogo() {
        for (UUID id : new UUID[] {DOCENTE, ADMIN, OTRO, INACTIVO}) {
            when(usuarios.existePorId(id)).thenReturn(true);
        }
        when(usuarios.buscarPorId(DOCENTE)).thenReturn(Optional.of(usuario(DOCENTE, DOCENTE_ROL, "Activo")));
        when(usuarios.buscarPorId(ADMIN)).thenReturn(Optional.of(usuario(ADMIN, ADMINISTRADOR, "Activo")));
        when(usuarios.buscarPorId(OTRO)).thenReturn(Optional.of(usuario(OTRO, DOCENTE_ROL, "Activo")));
        when(usuarios.buscarPorId(INACTIVO)).thenReturn(Optional.of(usuario(INACTIVO, ADMINISTRADOR, "Inactivo")));
        for (int i = 0; i < CODIGOS.length; i++) {
            when(permisos.buscarPorId(i + 1)).thenReturn(Optional.of(new Permiso(i + 1, CODIGOS[i], CODIGOS[i], null)));
        }
        when(asignaciones.registrar(any(), any(), any(), any())).thenAnswer(inv ->
                new AsignacionPermiso(inv.getArgument(0), inv.getArgument(1), true, inv.getArgument(2), OffsetDateTime.now()));
        when(asignaciones.deshabilitar(any(), any())).thenReturn(true);
    }

    private static Usuario usuario(UUID id, Rol rol, String estado) {
        return new Usuario(id, rol, "U", id + "@prisaly.test", "hash", estado, OffsetDateTime.now(), null);
    }

    private static int id(String codigo) {
        for (int i = 0; i < CODIGOS.length; i++) {
            if (CODIGOS[i].equals(codigo)) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException(codigo);
    }

    private void rechazoAsignar(String esperado, String codigo, UUID actor) {
        var error = assertThrows(IdentityDomainException.class, () -> servicio.asignar(DOCENTE, id(codigo), actor));
        assertEquals(esperado, error.code());
        verify(auditoria).registrarRechazo("ASIGNAR", DOCENTE, codigo, actor, esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREAR_CASO", "GESTIONAR_SESION", "CONSULTAR_CASO", "MODIFICAR_CASO", "PUBLICAR_CASO", "ARCHIVAR_CASO"})
    void asignarCualquierPermisoExigeAdministradorActivo(String codigo) {
        rechazoAsignar(PoliticaPermisos.CODE_ACTOR_REQUIRED, codigo, null);
        rechazoAsignar(PoliticaPermisos.CODE_USER_NOT_FOUND, codigo, AUSENTE);
        rechazoAsignar(PoliticaPermisos.CODE_ACTOR_NOT_ADMIN, codigo, OTRO);
        rechazoAsignar(PoliticaPermisos.CODE_ACTOR_INVALID, codigo, INACTIVO);
        verify(asignaciones, never()).registrar(any(), any(), any(), any());
        servicio.asignar(DOCENTE, id(codigo), ADMIN);
        verify(asignaciones).registrar(eq(DOCENTE), eq(id(codigo)), eq(ADMIN), any());
        verify(auditoria).registrarExitoAlConfirmar("ASIGNAR", DOCENTE, codigo, ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREAR_CASO", "GESTIONAR_SESION", "CONSULTAR_CASO", "PUBLICAR_CASO"})
    void revocarCualquierPermisoExigeAdministradorActivo(String codigo) {
        for (UUID actor : new UUID[] {null, AUSENTE, OTRO, INACTIVO}) {
            assertThrows(IdentityDomainException.class, () -> servicio.revocar(DOCENTE, id(codigo), actor));
        }
        verify(asignaciones, never()).deshabilitar(any(), any());
        servicio.revocar(DOCENTE, id(codigo), ADMIN);
        verify(asignaciones).deshabilitar(DOCENTE, id(codigo));
        verify(auditoria).registrarExitoAlConfirmar("REVOCAR", DOCENTE, codigo, ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLICAR_CASO", "ARCHIVAR_CASO"})
    void separacionDeFuncionesSeConserva(String codigo) {
        var error = assertThrows(IdentityDomainException.class, () -> servicio.asignar(ADMIN, id(codigo), ADMIN));
        assertEquals(PoliticaPermisos.CODE_SELF_ASSIGNMENT, error.code());
        verify(asignaciones, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void exclusionDeEstudiantesSeConservaParaLosPermisosControlados() {
        when(usuarios.esCuentaEstudiante(DOCENTE)).thenReturn(true);
        for (String codigo : new String[] {"CONSULTAR_CASO", "MODIFICAR_CASO", "PUBLICAR_CASO", "ARCHIVAR_CASO"}) {
            var error = assertThrows(IdentityDomainException.class, () -> servicio.asignar(DOCENTE, id(codigo), ADMIN));
            assertEquals(PoliticaPermisos.CODE_STUDENT_NOT_ALLOWED, error.code());
        }
        verify(asignaciones, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void duplicadoYUsuarioInexistenteSiguenRechazandose() {
        when(asignaciones.existe(DOCENTE, id("CREAR_CASO"))).thenReturn(true);
        assertEquals(PoliticaPermisos.CODE_DUPLICATE_ASSIGNMENT, assertThrows(IdentityDomainException.class,
                () -> servicio.asignar(DOCENTE, id("CREAR_CASO"), ADMIN)).code());
        assertEquals(PoliticaPermisos.CODE_USER_NOT_FOUND, assertThrows(IdentityDomainException.class,
                () -> servicio.asignar(AUSENTE, id("CREAR_CASO"), ADMIN)).code());
        verify(asignaciones, never()).registrar(any(), any(), any(), any());
    }
}
