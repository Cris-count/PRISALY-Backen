package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort.Decision;
import com.prisaly.m3.domain.model.CasoEstado;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class M3CaseReadAuthorizationAdapterTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String PERMISO = "PERMISO_LECTURA_FIXTURE";
    private static final PoliticaLecturaCaso POLITICA =
            PoliticaLecturaCaso.desdeConfiguracion(PERMISO, "BORRADOR, PUBLICADO", true);

    // Identity double: current user and permission lookup are controlled per test.
    private static IdentityAccessPort identidad(Supplier<AuthenticatedUser> current, Set<String> permisos) {
        return new IdentityAccessPort() {
            @Override public AuthenticatedUser currentUser() { return current.get(); }
            @Override public AuthenticatedUser requireUser(UUID userId) { return current.get(); }
            @Override public boolean hasPermission(UUID userId, String code) { current.get(); return permisos.contains(code); }
            @Override public boolean hasRole(UUID userId, String roleName) { return false; }
        };
    }

    private static AuthenticatedUser usuario(String estado) {
        return new AuthenticatedUser(USER, "docente@prisaly.test", "Docente", "Docente", estado, Set.of());
    }

    @Test
    void conPermisoYEstadoVisibleAutoriza() {
        var adapter = new M3CaseReadAuthorizationAdapter(identidad(() -> usuario("Activo"), Set.of(PERMISO)), POLITICA);
        assertEquals(Decision.AUTORIZADO, adapter.verificarAcceso(USER, CASO));
        assertEquals(Decision.AUTORIZADO, adapter.verificarVisibilidad(USER, CASO, CasoEstado.BORRADOR, true));
    }

    @Test
    void politicaPendienteDeniegaAunConPermisos() {
        var adapter = new M3CaseReadAuthorizationAdapter(
                identidad(() -> usuario("Activo"), Set.of(PERMISO, "CREAR_CASO")), PoliticaLecturaCaso.pendiente());
        assertEquals(Decision.DENEGADO, adapter.verificarAcceso(USER, CASO));
        assertEquals(Decision.DENEGADO, adapter.verificarVisibilidad(USER, CASO, CasoEstado.BORRADOR, true));
    }

    @Test
    void crearCasoNoEsPermisoDeLectura() {
        var adapter = new M3CaseReadAuthorizationAdapter(identidad(() -> usuario("Activo"), Set.of("CREAR_CASO")), POLITICA);
        assertEquals(Decision.DENEGADO, adapter.verificarAcceso(USER, CASO));
    }

    @Test
    void usuarioSuplantadoInactivoONuloSeDeniega() {
        var activo = new M3CaseReadAuthorizationAdapter(identidad(() -> usuario("Activo"), Set.of(PERMISO)), POLITICA);
        assertEquals(Decision.DENEGADO, activo.verificarAcceso(UUID.randomUUID(), CASO));
        assertEquals(Decision.DENEGADO, activo.verificarAcceso(null, CASO));
        assertEquals(Decision.DENEGADO, activo.verificarAcceso(USER, null));
        var inactivo = new M3CaseReadAuthorizationAdapter(identidad(() -> usuario("Inactivo"), Set.of(PERMISO)), POLITICA);
        assertEquals(Decision.DENEGADO, inactivo.verificarAcceso(USER, CASO));
    }

    @Test
    void estadoNoVisibleOCasoInactivoSeDeniega() {
        var adapter = new M3CaseReadAuthorizationAdapter(identidad(() -> usuario("Activo"), Set.of(PERMISO)), POLITICA);
        assertEquals(Decision.DENEGADO, adapter.verificarVisibilidad(USER, CASO, CasoEstado.ARCHIVADO, true));
        assertEquals(Decision.DENEGADO, adapter.verificarVisibilidad(USER, CASO, CasoEstado.PUBLICADO, false));
        assertEquals(Decision.DENEGADO, adapter.verificarVisibilidad(USER, CASO, null, true));
    }

    @Test
    void denegacionDeIdentidadEsDenegadoYFalloTecnicoEsNoDisponible() {
        var sinSesion = new M3CaseReadAuthorizationAdapter(identidad(() -> {
            throw new IdentityDomainException("UNAUTHENTICATED", "no session");
        }, Set.of(PERMISO)), POLITICA);
        assertEquals(Decision.DENEGADO, sinSesion.verificarAcceso(USER, CASO));

        var caido = new M3CaseReadAuthorizationAdapter(identidad(() -> {
            throw new IllegalStateException("database unavailable");
        }, Set.of(PERMISO)), POLITICA);
        assertEquals(Decision.NO_DISPONIBLE, caido.verificarAcceso(USER, CASO));
        assertEquals(Decision.NO_DISPONIBLE, caido.verificarVisibilidad(USER, CASO, CasoEstado.BORRADOR, true));
    }

    @Test
    void laPoliticaParcialOInvalidaFallaAlArrancar() {
        assertThrows(IllegalStateException.class, () -> PoliticaLecturaCaso.desdeConfiguracion(PERMISO, "", true));
        assertThrows(IllegalStateException.class, () -> PoliticaLecturaCaso.desdeConfiguracion("", "BORRADOR", true));
        assertThrows(IllegalStateException.class, () -> PoliticaLecturaCaso.desdeConfiguracion("leer caso", "BORRADOR", true));
        assertThrows(IllegalArgumentException.class, () -> PoliticaLecturaCaso.desdeConfiguracion(PERMISO, "VISIBLE", true));
        assertFalse(PoliticaLecturaCaso.desdeConfiguracion("", "", true).ratificada());
        assertTrue(PoliticaLecturaCaso.desdeConfiguracion(PERMISO, "PUBLICADO", false).visible(CasoEstado.PUBLICADO, false));
    }
}
