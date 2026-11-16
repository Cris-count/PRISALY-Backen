package com.prisaly.m5.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Caso;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Escenario;
import com.prisaly.m3.api.ContenidoCasoEstudiantilPort.Pregunta;
import com.prisaly.m3.api.GuionNarrativoEstudiantilPort;
import com.prisaly.m4.api.ProgramacionParticipacionPort;
import com.prisaly.m5.application.ContenidoIntento;
import com.prisaly.m5.application.ContenidoIntentoNoDisponibleException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.fase.CompletitudIntento;
import com.prisaly.m5.application.fase.FasesIntento;
import com.prisaly.m5.application.fase.FasesNoDisponiblesException;
import com.prisaly.m5.application.fase.PuntosDecisionFase;
import com.prisaly.m5.application.inicio.ElegibilidadNoRatificada;
import com.prisaly.m5.application.inicio.LimiteTemporalNoRatificado;
import com.prisaly.m5.application.port.GuionesDelIntento;
import com.prisaly.m5.application.port.RepositorioFases;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * DC-BE-SIM-B1-B2A-24: el ensamblado de las fases candidatas. Con el valor de producción no se construye nada (M5 como
 * antes); habilitadas sin el guion de M3 el arranque falla cerrado; ninguna operación nueva existe sin fases, ni en los
 * métodos default de la interfaz ni en el Proxy real del ensamblaje (DC-BE-SIM-B1-B2A-CORRECCION-26, QA-B25-01).
 */
@DisplayName("Ensamblado de las fases académicas candidatas")
class ConfiguracionFasesTest {

    @Test @DisplayName("deshabilitadas (valor de producción): no se construye nada ni se exige ninguna dependencia")
    void deshabilitadasNoConstruyenNada() {
        assertEquals(Optional.empty(), ConfiguracionParticipacion.fases(false, null, null, null, null));
    }

    @Test @DisplayName("habilitadas sin el guion narrativo de M3: el arranque falla cerrado con un código explícito")
    void habilitadasSinNarrativaFallanCerradas() {
        var error = assertThrows(IllegalStateException.class, () -> ConfiguracionParticipacion.fases(true,
                Optional::empty, Mockito.mock(ProgramacionParticipacionPort.class), null, Mockito.mock(JdbcTemplate.class)));
        assertTrue(error.getMessage().startsWith(ConfiguracionParticipacion.CODIGO_FASES), error.getMessage());
        assertThrows(IllegalStateException.class, () -> ConfiguracionParticipacion.fases(true, Optional::empty,
                null, Mockito.mock(GuionNarrativoEstudiantilPort.class), Mockito.mock(JdbcTemplate.class)));
        assertTrue(ConfiguracionParticipacion.fases(true, Optional::empty, Mockito.mock(ProgramacionParticipacionPort.class),
                Mockito.mock(GuionNarrativoEstudiantilPort.class), Mockito.mock(JdbcTemplate.class)).isPresent());
    }

    @Test @DisplayName("las operaciones nuevas fallan cerradas en cualquier composición que no las implemente")
    void operacionesNuevasCerradasPorDefecto() {
        OperacionesParticipacion sinFases = Mockito.mock(OperacionesParticipacion.class, Mockito.CALLS_REAL_METHODS);
        var intento = new ReferenciaIntento(UUID.randomUUID().toString());
        assertThrows(FasesNoDisponiblesException.class, () -> sinFases.consultarFase(intento));
        assertThrows(FasesNoDisponiblesException.class, () -> sinFases.iniciarCuestionario(intento));
        assertThrows(FasesNoDisponiblesException.class, () -> sinFases.consultarNarrativa(intento));
        assertThrows(ContenidoIntentoNoDisponibleException.class, () -> sinFases.consultarCompletitud(intento));
    }

    // DC-BE-SIM-B1-B2A-CORRECCION-26 (QA-B25-01): el Proxy real, construido por el ensamblaje, no expone P1–P4 con el
    // interruptor apagado. La prueba anterior solo cubría los métodos default de la interfaz.

    @Test @DisplayName("QA-B25-01: con el interruptor apagado el Proxy ensamblado deniega P1–P4 sin tocar identidad, contenido ni BD")
    void proxyEnsambladoApagadoDeniegaLasCuatroOperaciones() {
        var fixture = new Fixture(Titularidad.PROPIO);
        AccesoProtegidoParticipacion acceso = fixture.ensamblar(ConfiguracionParticipacion.fases(false, null, null, null, null));

        assertThrows(FasesNoDisponiblesException.class, () -> acceso.consultarNarrativa(fixture.ref), "P1");
        assertThrows(FasesNoDisponiblesException.class, () -> acceso.consultarFase(fixture.ref), "P2");
        assertThrows(FasesNoDisponiblesException.class, () -> acceso.iniciarCuestionario(fixture.ref), "P3");
        assertThrows(FasesNoDisponiblesException.class, () -> acceso.consultarCompletitud(fixture.ref), "P4");
        assertEquals(0, fixture.identidad.get(), "la guarda actúa antes de resolver la identidad");
        assertEquals(0, fixture.contenido.get(), "P4 no lee el contenido autorizado");
        assertEquals(0, fixture.repositorio.get(), "P4 no lee el intento");
        assertEquals(0, fixture.transacciones.get(), "no se abre ninguna transacción");
        // Las operaciones previas a B2a siguen disponibles en el mismo ensamblado.
        assertEquals(1, acceso.consultarContenido(fixture.ref).caso().escenarios().size());
    }

    @Test @DisplayName("con el interruptor encendido P4 conserva titularidad y REPEATABLE_READ, sin exigir una fila FaseIntento")
    void proxyEnsambladoEncendidoConservaP4() {
        var fixture = new Fixture(Titularidad.PROPIO);
        RepositorioFases filas = Mockito.mock(RepositorioFases.class);
        AccesoProtegidoParticipacion acceso = fixture.ensamblar(Optional.of(new FasesIntento(filas,
                Mockito.mock(GuionesDelIntento.class), PuntosDecisionFase.noRatificados())));

        CompletitudIntento completitud = acceso.consultarCompletitud(fixture.ref);

        assertEquals(1, completitud.preguntasPresentables());
        assertEquals(List.of(fixture.pregunta), completitud.sinResponder());
        assertEquals(1, fixture.identidad.get());
        // Titularidad, contenido y respuestas en una sola instantánea: la exterior es REPEATABLE_READ de solo lectura y
        // la lectura de respuestas de la fachada se une a ella (REQUIRED), sin abrir otra.
        TransactionDefinition exterior = fixture.definiciones.get(0);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, exterior.getPropagationBehavior());
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, exterior.getIsolationLevel());
        assertTrue(exterior.isReadOnly());
        assertTrue(fixture.definiciones.stream().skip(1)
                .allMatch(d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
        Mockito.verifyNoInteractions(filas); // intentos SIN_FASES: P4 no depende de la fila de fase

        var ajeno = new Fixture(Titularidad.AJENO);
        AccesoProtegidoParticipacion deOtro = ajeno.ensamblar(Optional.of(new FasesIntento(filas,
                Mockito.mock(GuionesDelIntento.class), PuntosDecisionFase.noRatificados())));
        assertThrows(AccesoIntentoDenegadoException.class, () -> deOtro.consultarCompletitud(ajeno.ref));
        assertEquals(0, ajeno.contenido.get(), "sin titularidad no se lee el contenido");
        assertEquals(0, ajeno.repositorio.get(), "sin titularidad no se leen las respuestas");
    }

    /** Colaboradores en memoria con contadores; el Proxy y el ensamblaje son los reales. */
    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final ReferenciaIntento ref = new ReferenciaIntento(id.toString());
        final UUID pregunta = UUID.randomUUID();
        final AtomicInteger identidad = new AtomicInteger();
        final AtomicInteger contenido = new AtomicInteger();
        final AtomicInteger repositorio = new AtomicInteger();
        final AtomicInteger transacciones = new AtomicInteger();
        final List<TransactionDefinition> definiciones = new java.util.ArrayList<>();
        final Titularidad titularidad;

        Fixture(Titularidad titularidad) {
            this.titularidad = titularidad;
        }

        AccesoProtegidoParticipacion ensamblar(Optional<FasesIntento> fases) {
            var resumen = new ResumenIntento(ref, EstadoIntento.EN_CURSO, Optional.empty(), List.of());
            RepositorioIntentos intentos = proxy(RepositorioIntentos.class, nombre -> {
                repositorio.incrementAndGet();
                return switch (nombre) {
                    case "buscar" -> Optional.of(Intento.iniciar());
                    case "resumir" -> resumen;
                    default -> throw new UnsupportedOperationException(nombre);
                };
            });
            RepositorioParticipaciones participaciones = proxy(RepositorioParticipaciones.class, nombre -> {
                throw new UnsupportedOperationException(nombre);
            });
            PlatformTransactionManager tx = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition d) {
                    transacciones.incrementAndGet();
                    definiciones.add(d);
                    return new SimpleTransactionStatus();
                }
                @Override public void commit(TransactionStatus s) { }
                @Override public void rollback(TransactionStatus s) { }
            };
            var caso = new Caso(UUID.randomUUID(), "Caso", null, null, List.of(new Escenario(UUID.randomUUID(), "Escenario",
                    null, null, 1, List.of(new Pregunta(pregunta, "Pregunta", 1, List.of())))));
            return ConfiguracionParticipacion.ensamblar(intentos, participaciones, tx,
                    () -> {
                        identidad.incrementAndGet();
                        return Optional.of(new IdentificadorEstudiante(UUID.randomUUID().toString()));
                    },
                    (i, e) -> titularidad, new ElegibilidadNoRatificada(), new LimiteTemporalNoRatificado(), c -> { },
                    (i, e) -> {
                        contenido.incrementAndGet();
                        return new ContenidoIntento(id, UUID.randomUUID(), caso);
                    },
                    fases);
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> tipo, Function<String, Object> respuesta) {
            return (T) java.lang.reflect.Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[] {tipo},
                    (p, m, a) -> m.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(p, m, a)
                            : respuesta.apply(m.getName()));
        }
    }
}
