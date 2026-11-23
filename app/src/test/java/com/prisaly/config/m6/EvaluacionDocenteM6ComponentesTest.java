package com.prisaly.config.m6;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m3.api.AutoriaCasoNoDisponibleException;
import com.prisaly.m3.api.AutoriaCasoPort;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Decision;
import com.prisaly.m3.application.port.AutorizacionContextoAcademicoPort.Operacion;
import com.prisaly.m5.infrastructure.evaluacion.LecturaIntentoDocenteDesdeM5;
import com.prisaly.m6.api.SolicitudEvaluacion;
import com.prisaly.m6.application.AccesoEvaluacionDenegadoException;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Unit tests of the switch-guarded APP pieces of DC-55: owner check, authorization, capabilities, switch. */
class EvaluacionDocenteM6ComponentesTest {
    static final UUID DOCENTE = UUID.randomUUID(), OTRO = UUID.randomUUID(), CASO = UUID.randomUUID();

    IdentityAccessPort identidad;
    AutoriaCasoPort autoria;
    PropietarioActivoCasoM6 propietario;

    @BeforeEach
    void preparar() {
        identidad = mock(IdentityAccessPort.class);
        autoria = mock(AutoriaCasoPort.class);
        propietario = new PropietarioActivoCasoM6(identidad, autoria);
        when(autoria.docenteCreador(CASO)).thenReturn(Optional.of(DOCENTE));
    }

    private void actual(UUID id, String estado) {
        when(identidad.currentUser()).thenReturn(new AuthenticatedUser(id, "d@prisaly.test", "D", "DOCENTE", estado, Set.of()));
    }

    // ─── Owner check (M1 + M3) ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void soloElCreadorActivoEsPropietario() {
        actual(DOCENTE, "Activo");
        assertTrue(propietario.esDocenteActivoPropietario(CASO));
        assertFalse(propietario.esDocenteActivoPropietario(UUID.randomUUID()), "case without author");
        assertFalse(propietario.esDocenteActivoPropietario(null));
        actual(OTRO, "Activo");
        assertFalse(propietario.esDocenteActivoPropietario(CASO), "foreign teacher");
        actual(DOCENTE, "Inactivo");
        assertFalse(propietario.esDocenteActivoPropietario(CASO), "inactive owner");
        when(identidad.currentUser()).thenThrow(new IdentityDomainException("USER_INACTIVE", "inactive"));
        assertFalse(propietario.esDocenteActivoPropietario(CASO), "M1 denial");
        verify(identidad, never()).hasPermission(any(), any());
    }

    @Test
    void falloTecnicoDeAutoriaPropagaNuncaAutoriza() {
        actual(DOCENTE, "Activo");
        when(autoria.docenteCreador(CASO)).thenThrow(new AutoriaCasoNoDisponibleException());
        assertThrows(AutoriaCasoNoDisponibleException.class, () -> propietario.esDocenteActivoPropietario(CASO));
    }

    // ─── Evaluation authorization ───────────────────────────────────────────────────────────────────────────────

    @Test
    void autorizacionSegunPropiedadDelIntento() {
        var lector = mock(LecturaIntentoDocenteDesdeM5.class);
        var autorizacion = new AutorizacionDocentePropietarioM6(lector);
        var solicitud = new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID());
        when(lector.perteneceAlDocenteActual(solicitud.idIntento())).thenReturn(true);
        assertEquals(DecisionAcceso.AUTORIZADO, autorizacion.verificarAcceso(solicitud));
        when(lector.perteneceAlDocenteActual(solicitud.idIntento())).thenReturn(false);
        assertEquals(DecisionAcceso.DENEGADO, autorizacion.verificarAcceso(solicitud));
        when(lector.perteneceAlDocenteActual(solicitud.idIntento())).thenThrow(new AccesoEvaluacionDenegadoException());
        assertEquals(DecisionAcceso.DENEGADO, autorizacion.verificarAcceso(solicitud));
        reset(lector);
        when(lector.perteneceAlDocenteActual(solicitud.idIntento())).thenThrow(new IllegalStateException("db down"));
        assertEquals(DecisionAcceso.NO_DISPONIBLE, autorizacion.verificarAcceso(solicitud));
        assertEquals(DecisionAcceso.DENEGADO, autorizacion.verificarAcceso(null));
    }

    // ─── Capabilities ───────────────────────────────────────────────────────────────────────────────────────────

    static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-11-22T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @Test
    void capacidadSoloAlPropietarioDeUnSoloUsoLigadaAlCasoYAlDocente() {
        var reloj = new Reloj();
        var capacidades = new CapacidadesContextoEvaluacionM6(propietario, reloj);
        actual(OTRO, "Activo");
        assertThrows(AccesoEvaluacionDenegadoException.class, () -> capacidades.solicitarCapacidad(UUID.randomUUID(), CASO));
        assertEquals(0, capacidades.pendientes());

        actual(DOCENTE, "Activo");
        String c1 = capacidades.solicitarCapacidad(UUID.randomUUID(), CASO);
        assertTrue(c1.length() >= 43, "32 random bytes");
        assertEquals(Decision.AUTORIZADO, capacidades.verificar(c1, CASO, Operacion.CONSULTAR_CONTEXTO_EVALUABLE));
        assertEquals(Decision.DENEGADO, capacidades.verificar(c1, CASO, Operacion.CONSULTAR_CONTEXTO_EVALUABLE), "single use");

        String c2 = capacidades.solicitarCapacidad(UUID.randomUUID(), CASO);
        assertEquals(Decision.DENEGADO, capacidades.verificar(c2, UUID.randomUUID(), Operacion.CONSULTAR_CONTEXTO_EVALUABLE));
        assertEquals(Decision.DENEGADO, capacidades.verificar(c2, CASO, Operacion.CONSULTAR_CONTEXTO_EVALUABLE),
                "consumed by the failed verification");

        String c3 = capacidades.solicitarCapacidad(UUID.randomUUID(), CASO);
        actual(OTRO, "Activo");
        assertEquals(Decision.DENEGADO, capacidades.verificar(c3, CASO, Operacion.CONSULTAR_CONTEXTO_EVALUABLE), "other teacher");

        actual(DOCENTE, "Activo");
        String c4 = capacidades.solicitarCapacidad(UUID.randomUUID(), CASO);
        reloj.ahora = reloj.ahora.plus(CapacidadesContextoEvaluacionM6.VIGENCIA);
        assertEquals(Decision.DENEGADO, capacidades.verificar(c4, CASO, Operacion.CONSULTAR_CONTEXTO_EVALUABLE), "expired");

        assertEquals(Decision.DENEGADO, capacidades.verificar("inventada", CASO, Operacion.CONSULTAR_CONTEXTO_EVALUABLE));
        assertEquals(Decision.DENEGADO, capacidades.verificar(null, CASO, Operacion.CONSULTAR_CONTEXTO_EVALUABLE));
        assertEquals(Decision.DENEGADO, capacidades.verificar(capacidades.solicitarCapacidad(UUID.randomUUID(), CASO),
                CASO, null));
    }

    @Test
    void capacidadesAcotadasYAleatorias() {
        var reloj = new Reloj();
        var capacidades = new CapacidadesContextoEvaluacionM6(propietario, reloj);
        actual(DOCENTE, "Activo");
        Set<String> emitidas = new HashSet<>();
        for (int i = 0; i < CapacidadesContextoEvaluacionM6.MAXIMO_PENDIENTES; i++) {
            emitidas.add(capacidades.solicitarCapacidad(UUID.randomUUID(), CASO));
        }
        assertEquals(CapacidadesContextoEvaluacionM6.MAXIMO_PENDIENTES, emitidas.size());
        assertThrows(IllegalStateException.class, () -> capacidades.solicitarCapacidad(UUID.randomUUID(), CASO));
        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(2)); // expired ones are purged on the next issue
        assertNotNull(capacidades.solicitarCapacidad(UUID.randomUUID(), CASO));
        assertEquals(1, capacidades.pendientes());
    }

    // ─── Switch ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void sinInterruptorNoSeEnsamblaNada() {
        var runner = new ApplicationContextRunner().withUserConfiguration(M6EvaluacionConfig.class);
        runner.run(ctx -> assertEquals(0, ctx.getBeansOfType(EvaluacionDocenteM6.class).size()));
        runner.withPropertyValues("prisaly.m6.evaluacion.enabled=false")
                .run(ctx -> assertEquals(0, ctx.getBeansOfType(EvaluacionDocenteM6.class).size()));
    }
}
