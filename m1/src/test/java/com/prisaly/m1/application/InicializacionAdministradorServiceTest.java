package com.prisaly.m1.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.InicializacionAdministradorPort;
import com.prisaly.m1.application.port.RolRepositorioPort;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.application.service.InicializacionAdministradorService;
import com.prisaly.m1.application.service.InicializacionAdministradorService.Estado;
import com.prisaly.m1.domain.model.Rol;
import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.rule.PoliticaContrasena;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** P5-U2, P5-U3, P5-U5 and the transactional order of DC-P5-FASE-B-01 (ports mocked, no database). */
class InicializacionAdministradorServiceTest {

    private static final String CLAVE = "Fixture#Inicial-2026";
    private static final Rol ADMIN = new Rol(1, "ADMINISTRADOR", "PD-01");

    private InicializacionAdministradorPort marcador;
    private RolRepositorioPort roles;
    private IdentityCatalogService catalogo;
    private InicializacionAdministradorService servicio;

    @BeforeEach
    void preparar() {
        marcador = mock(InicializacionAdministradorPort.class);
        roles = mock(RolRepositorioPort.class);
        catalogo = mock(IdentityCatalogService.class);
        servicio = new InicializacionAdministradorService(marcador, roles, catalogo);
    }

    @Test
    void primeraEjecucionBloqueaCompruebaRegistraYMarcaEnEseOrden() {
        UUID id = UUID.randomUUID();
        when(roles.buscarPorNombre("ADMINISTRADOR")).thenReturn(Optional.of(ADMIN));
        when(catalogo.registerUser("Admin Inicial", "admin@prisaly.test", CLAVE, "ADMINISTRADOR"))
                .thenReturn(new Usuario(id, ADMIN, "Admin Inicial", "admin@prisaly.test", "$hash", "Activo",
                        OffsetDateTime.now(), null));

        var r = servicio.inicializar("  Admin Inicial ", "admin@prisaly.test", CLAVE, "ARRANQUE");

        assertEquals(Estado.INICIALIZADO, r.estado());
        assertEquals(id, r.idUsuarioAdministrador());
        var orden = inOrder(marcador, roles, catalogo);
        orden.verify(marcador).bloquear();
        orden.verify(marcador).marcadorExiste();
        orden.verify(roles).buscarPorNombre("ADMINISTRADOR");
        orden.verify(catalogo).registerUser("Admin Inicial", "admin@prisaly.test", CLAVE, "ADMINISTRADOR");
        orden.verify(marcador).registrarMarcador(id, "ARRANQUE");
    }

    @Test
    void marcadorExistenteNoCreaNadaAunqueHayaRol() {
        when(marcador.marcadorExiste()).thenReturn(true);

        var r = servicio.inicializar("Admin", "admin@prisaly.test", CLAVE, "ARRANQUE");

        assertEquals(Estado.YA_REALIZADA, r.estado());
        assertNull(r.idUsuarioAdministrador());
        verify(marcador).bloquear();
        verifyNoInteractions(catalogo, roles);
        verify(marcador, never()).registrarMarcador(any(), anyString());
    }

    @Test
    void sinRolAdministradorFallaSinCrearUsuarioNiRol() {
        when(roles.buscarPorNombre("ADMINISTRADOR")).thenReturn(Optional.empty());

        var ex = assertThrows(IdentityDomainException.class,
                () -> servicio.inicializar("Admin", "admin@prisaly.test", CLAVE, "ARRANQUE"));

        assertEquals(InicializacionAdministradorService.CODE_ADMIN_ROLE_NOT_PROVISIONED, ex.code());
        verifyNoInteractions(catalogo);
        verify(roles, never()).registrar(anyString(), anyString());
        verify(marcador, never()).registrarMarcador(any(), anyString());
    }

    @Test
    void politicaDeContrasenaRechazadaNoDejaMarcador() {
        when(roles.buscarPorNombre("ADMINISTRADOR")).thenReturn(Optional.of(ADMIN));
        when(catalogo.registerUser(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IdentityDomainException(PoliticaContrasena.CODE_TOO_SHORT, "Password too short"));

        var ex = assertThrows(IdentityDomainException.class,
                () -> servicio.inicializar("Admin", "admin@prisaly.test", "corta", "ARRANQUE"));

        assertEquals(PoliticaContrasena.CODE_TOO_SHORT, ex.code());
        verify(marcador, never()).registrarMarcador(any(), anyString());
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,admin@prisaly.test,clave,ARRANQUE", "' ',admin@prisaly.test,clave,ARRANQUE",
            "Admin,NULL,clave,ARRANQUE", "Admin,' ',clave,ARRANQUE", "Admin,admin@prisaly.test,NULL,ARRANQUE",
            "Admin,admin@prisaly.test,clave,NULL", "Admin,admin@prisaly.test,clave,' '",
            "Admin,admin@prisaly.test,clave,ORIGEN-DEMASIADO-LARGO-PARA-LA-COLUMNA-40X"}, nullValues = "NULL")
    void datosIncompletosSeRechazanAntesDeBloquearOEscribir(String nombre, String correo, String clave, String origen) {
        var ex = assertThrows(IdentityDomainException.class, () -> servicio.inicializar(nombre, correo, clave, origen));

        assertEquals(InicializacionAdministradorService.CODE_INITIALIZATION_DATA_MISSING, ex.code());
        verifyNoInteractions(marcador, roles, catalogo);
    }
}
