package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.ContrasenasBloqueadasPort;
import com.prisaly.m1.application.port.PermisoRepositorioPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.port.UsuarioRepositorioPort;
import com.prisaly.m1.application.service.GestionPermisosService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.domain.model.Permiso;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaContrasena;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * DC-CIERRE-NOCTURNO-02 (JaCoCo: 7/22 branches): the account and catalogue rules of {@link IdentityCatalogService}
 * that the integration suites only reach on their happy path. Fixture passwords only; no real credential.
 */
class IdentityCatalogServiceTest {
    private static final String VALIDA = "Fixture#Clave-2026";
    private static final String OTRA_VALIDA = "Otra#Fixture-2026x";
    private static final Rol DOCENTE = new Rol(7, "DocenteIT", "Rol de prueba");

    private final RolRepositorioPort roles = mock(RolRepositorioPort.class);
    private final PermisoRepositorioPort permisos = mock(PermisoRepositorioPort.class);
    private final UsuarioRepositorioPort usuarios = mock(UsuarioRepositorioPort.class);
    private final GestionPermisosService gestion = mock(GestionPermisosService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ContrasenasBloqueadasPort bloqueadas = mock(ContrasenasBloqueadasPort.class);
    private final IdentityCatalogService servicio =
            new IdentityCatalogService(roles, permisos, usuarios, gestion, encoder, bloqueadas);

    private static Usuario usuario(UUID id, String hash, String estado) {
        return new Usuario(id, DOCENTE, "Docente", "docente@prisaly.test", hash, estado, OffsetDateTime.now(), null);
    }

    private static String codigo(Runnable r) {
        return assertThrows(IdentityDomainException.class, r::run).code();
    }

    // ---------------------------------------------------------------- roles and permissions

    @Test
    void rolDuplicadoSeRechazaAntesYDuranteLaInsercionConcurrente() {
        when(roles.buscarPorNombre("X")).thenReturn(Optional.of(DOCENTE));
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_ROLE, codigo(() -> servicio.createRole("X", "d")));
        verify(roles, never()).registrar(anyString(), any());

        when(roles.buscarPorNombre("Y")).thenReturn(Optional.empty());
        when(roles.registrar("Y", "d")).thenThrow(new DataIntegrityViolationException("uk"));
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_ROLE, codigo(() -> servicio.createRole("Y", "d")));
    }

    @Test
    void permisoDuplicadoSeRechazaAntesYDuranteLaInsercionConcurrente() {
        when(permisos.buscarPorCodigo("P")).thenReturn(Optional.of(new Permiso(1, "P", "n", "d")));
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_PERMISSION,
                codigo(() -> servicio.createPermission("P", "n", "d")));
        verify(permisos, never()).registrar(anyString(), anyString(), any());

        when(permisos.buscarPorCodigo("Q")).thenReturn(Optional.empty());
        when(permisos.registrar("Q", "n", "d")).thenThrow(new DataIntegrityViolationException("uk"));
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_PERMISSION,
                codigo(() -> servicio.createPermission("Q", "n", "d")));
    }

    // ---------------------------------------------------------------- registerUser (DM1-08)

    @Test
    void laPoliticaDeContrasenaSeAplicaAntesDeConsultarOHashear() {
        assertEquals(PoliticaContrasena.CODE_TOO_SHORT,
                codigo(() -> servicio.registerUser("N", "a@prisaly.test", "corta", "DocenteIT")));
        verifyNoInteractions(usuarios, roles, encoder);
    }

    @Test
    void registroNormalizaElCorreoYGuardaSoloElHash() {
        when(usuarios.existePorCorreo("docente@prisaly.test")).thenReturn(false);
        when(roles.buscarPorNombre("DocenteIT")).thenReturn(Optional.of(DOCENTE));
        when(encoder.encode(VALIDA)).thenReturn("$hash$");
        when(usuarios.registrar(any())).thenAnswer(i -> i.getArgument(0));

        Usuario u = servicio.registerUser("Docente", "  Docente@PRISALY.test ", VALIDA, "DocenteIT");

        assertEquals("docente@prisaly.test", u.getCorreo());
        assertEquals("$hash$", u.getPasswordHash());
        assertEquals(true, u.isActive());
    }

    @Test
    void registroConCorreoExistenteRolInexistenteOCarreraConcurrenteSeRechaza() {
        when(usuarios.existePorCorreo("a@prisaly.test")).thenReturn(true);
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_EMAIL,
                codigo(() -> servicio.registerUser("N", "a@prisaly.test", VALIDA, "DocenteIT")));

        when(usuarios.existePorCorreo("b@prisaly.test")).thenReturn(false);
        when(roles.buscarPorNombre("NoExiste")).thenReturn(Optional.empty());
        assertEquals(IdentityCatalogService.CODE_ROLE_NOT_FOUND,
                codigo(() -> servicio.registerUser("N", "b@prisaly.test", VALIDA, "NoExiste")));

        when(roles.buscarPorNombre("DocenteIT")).thenReturn(Optional.of(DOCENTE));
        when(encoder.encode(any())).thenReturn("$hash$");
        when(usuarios.registrar(any())).thenThrow(new DataIntegrityViolationException("uk_correo"));
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_EMAIL,
                codigo(() -> servicio.registerUser("N", "b@prisaly.test", VALIDA, "DocenteIT")));
    }

    // ---------------------------------------------------------------- registrarSinCredencial (DM1-04)

    @Test
    void altaSinCredencialNuncaGeneraNiHasheaContrasena() {
        when(usuarios.existePorCorreo("d@prisaly.test")).thenReturn(false);
        when(roles.buscarPorNombre("DocenteIT")).thenReturn(Optional.of(DOCENTE));
        when(usuarios.registrar(any())).thenAnswer(i -> i.getArgument(0));

        Usuario u = servicio.registrarSinCredencial("Docente", "D@prisaly.test", "DocenteIT");

        assertNull(u.getPasswordHash());
        assertEquals("d@prisaly.test", u.getCorreo());
        verifyNoInteractions(encoder);
    }

    @Test
    void altaSinCredencialRechazaDuplicadosYRolInexistente() {
        when(usuarios.existePorCorreo("d@prisaly.test")).thenReturn(true);
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_EMAIL,
                codigo(() -> servicio.registrarSinCredencial("N", "d@prisaly.test", "DocenteIT")));

        when(usuarios.existePorCorreo("e@prisaly.test")).thenReturn(false);
        when(roles.buscarPorNombre("NoExiste")).thenReturn(Optional.empty());
        assertEquals(IdentityCatalogService.CODE_ROLE_NOT_FOUND,
                codigo(() -> servicio.registrarSinCredencial("N", "e@prisaly.test", "NoExiste")));

        when(roles.buscarPorNombre("DocenteIT")).thenReturn(Optional.of(DOCENTE));
        when(usuarios.registrar(any())).thenThrow(new DataIntegrityViolationException("uk_correo"));
        assertEquals(IdentityCatalogService.CODE_DUPLICATE_EMAIL,
                codigo(() -> servicio.registrarSinCredencial("N", "e@prisaly.test", "DocenteIT")));
    }

    // ---------------------------------------------------------------- cambiarContrasena (DM1-08)

    @Test
    void cambioDeContrasenaExitosoGuardaElNuevoHash() {
        UUID id = UUID.randomUUID();
        when(usuarios.buscarPorId(id)).thenReturn(Optional.of(usuario(id, "$actual$", "Activo")));
        when(encoder.matches(VALIDA, "$actual$")).thenReturn(true);
        when(encoder.encode(OTRA_VALIDA)).thenReturn("$nuevo$");

        servicio.cambiarContrasena(id, VALIDA, OTRA_VALIDA);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(usuarios).actualizarPasswordHash(eq(id), hash.capture(), any());
        assertEquals("$nuevo$", hash.getValue());
    }

    @Test
    void cambioDeContrasenaRechazaTodasLasCondicionesDeAutenticacionSinRevelarCual() {
        UUID inexistente = UUID.randomUUID();
        when(usuarios.buscarPorId(inexistente)).thenReturn(Optional.empty());
        assertEquals(IdentityCatalogService.CODE_USER_NOT_FOUND,
                codigo(() -> servicio.cambiarContrasena(inexistente, VALIDA, OTRA_VALIDA)));

        UUID inactivo = UUID.randomUUID(), sinCredencial = UUID.randomUUID(), activo = UUID.randomUUID();
        when(usuarios.buscarPorId(inactivo)).thenReturn(Optional.of(usuario(inactivo, "$h$", "Inactivo")));
        when(usuarios.buscarPorId(sinCredencial)).thenReturn(Optional.of(usuario(sinCredencial, null, "Activo")));
        when(usuarios.buscarPorId(activo)).thenReturn(Optional.of(usuario(activo, "$h$", "Activo")));
        when(encoder.matches(VALIDA, "$h$")).thenReturn(true);
        when(encoder.matches("incorrecta-12345", "$h$")).thenReturn(false);

        for (Runnable intento : new Runnable[] {
                () -> servicio.cambiarContrasena(inactivo, VALIDA, OTRA_VALIDA),
                () -> servicio.cambiarContrasena(sinCredencial, VALIDA, OTRA_VALIDA),
                () -> servicio.cambiarContrasena(activo, null, OTRA_VALIDA),
                () -> servicio.cambiarContrasena(activo, "incorrecta-12345", OTRA_VALIDA)}) {
            assertEquals(IdentityCatalogService.CODE_AUTH_FAILED, codigo(intento));
        }
        verify(usuarios, never()).actualizarPasswordHash(any(), any(), any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void nuevaContrasenaQueIncumpleLaPoliticaNoSeGuarda() {
        UUID id = UUID.randomUUID();
        when(usuarios.buscarPorId(id)).thenReturn(Optional.of(usuario(id, "$h$", "Activo")));
        when(encoder.matches(VALIDA, "$h$")).thenReturn(true);

        assertEquals(PoliticaContrasena.CODE_TOO_SHORT, codigo(() -> servicio.cambiarContrasena(id, VALIDA, "corta")));
        verify(usuarios, never()).actualizarPasswordHash(any(), any(), any());
        verify(encoder, never()).encode(any());
    }

    // ---------------------------------------------------------------- other operations

    @Test
    void desactivarUsuarioInexistenteSeRechazaYElExistentePasaAInactivo() {
        UUID id = UUID.randomUUID();
        when(usuarios.existePorId(id)).thenReturn(false);
        assertEquals(IdentityCatalogService.CODE_USER_NOT_FOUND, codigo(() -> servicio.deactivateUser(id)));
        verify(usuarios, never()).cambiarEstado(any(), any(), any());

        when(usuarios.existePorId(id)).thenReturn(true);
        servicio.deactivateUser(id);
        verify(usuarios).cambiarEstado(eq(id), eq("Inactivo"), any());
    }

    @Test
    void revocacionSinActorDelegaConActorNulo() {
        UUID usuario = UUID.randomUUID();
        servicio.disablePermissionAssignment(usuario, 3);
        verify(gestion).revocar(usuario, 3, null);
    }

    @Test
    void existeRolAdministradorConsultaElCatalogo() {
        when(roles.buscarPorNombre(IdentityCatalogService.ROL_ADMINISTRADOR)).thenReturn(Optional.empty());
        assertEquals(false, servicio.existeRolAdministrador());
        when(roles.buscarPorNombre(IdentityCatalogService.ROL_ADMINISTRADOR)).thenReturn(Optional.of(DOCENTE));
        assertEquals(true, servicio.existeRolAdministrador());
    }

    @Test
    void correoNuloOVacioSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> IdentityCatalogService.normalizeEmail(null));
        assertThrows(IllegalArgumentException.class, () -> IdentityCatalogService.normalizeEmail("   "));
    }
}
