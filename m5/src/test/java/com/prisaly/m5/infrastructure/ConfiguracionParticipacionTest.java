package com.prisaly.m5.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionInicioCoordinadoPort;
import com.prisaly.m5.application.ConvocatoriaNoEncontradaException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaConvocatoria;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepositorioIntentosEnMemoria;
import com.prisaly.m5.application.RepositorioParticipacionesEnMemoria;
import com.prisaly.m5.application.ResultadoInicio;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.inicio.DecisionPendiente;
import com.prisaly.m5.application.inicio.InicioNoDisponibleException;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.application.port.RepositorioParticipaciones;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.infrastructure.identidad.IdentidadEstudianteDesdeM1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contexto de Spring de M5 sin base de datos: qué beans publica {@link ConfiguracionParticipacion}
 * y cómo se comportan ante fallos de M1 y M4.
 */
@DisplayName("ConfiguracionParticipacion (contexto Spring)")
class ConfiguracionParticipacionTest {

    private static final ReferenciaIntento INTENTO =
            new ReferenciaIntento(UUID.randomUUID().toString());
    private static final IdentificadorEstudiante ANA =
            new IdentificadorEstudiante(UUID.randomUUID().toString());
    private static final ReferenciaPregunta PREGUNTA = new ReferenciaPregunta("P-1");
    private static final ValorRespuesta VALOR = new ValorRespuesta("O-1");

    /**
     * Repositorio que falla si alguien lo usa: prueba que una denegación no llega a la
     * persistencia.
     */
    private static final RepositorioIntentos INTOCABLE =
            new RepositorioIntentos() {
                @Override
                public Optional<Intento> buscar(ReferenciaIntento referencia) {
                    throw new AssertionError("una operación denegada no debe leer el intento");
                }

                @Override
                public void guardar(ReferenciaIntento referencia, Intento intento) {
                    throw new AssertionError("una operación denegada no debe escribir");
                }
            };

    /** Repositorio de participaciones que falla si alguien lo usa. */
    private static final RepositorioParticipaciones PARTICIPACIONES_INTOCABLE =
            new RepositorioParticipaciones() {
                @Override
                public Optional<ReferenciaParticipacion> bloquear(
                        ReferenciaConvocatoria convocatoria) {
                    throw new AssertionError(
                            "una operación denegada no debe leer la participación");
                }

                @Override
                public ReferenciaParticipacion crearOBloquear(ReferenciaConvocatoria convocatoria) {
                    throw new AssertionError("una operación denegada no debe escribir");
                }

                @Override
                public Optional<IntentoEnCurso> intentoEnCurso(
                        ReferenciaParticipacion participacion) {
                    throw new AssertionError("una operación denegada no debe leer intentos");
                }

                @Override
                public boolean tieneIntentos(ReferenciaParticipacion participacion) {
                    throw new AssertionError("una operación denegada no debe leer intentos");
                }

                @Override
                public Instant ahora() {
                    throw new AssertionError("una operación denegada no debe leer el reloj");
                }

                @Override
                public ReferenciaIntento crearPrimerIntento(
                        ReferenciaParticipacion participacion, Instant inicio, Instant limite) {
                    throw new AssertionError("una operación denegada no debe escribir");
                }
            };

    private static final ReferenciaConvocatoria CONVOCATORIA =
            new ReferenciaConvocatoria(UUID.randomUUID().toString());

    private AnnotationConfigApplicationContext contexto(Object... beans) {
        AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext();
        // Este contexto sin JDBC prueba M5; la exclusión mutua real se verifica en app.
        contexto.registerBean(
                ProgramacionInicioCoordinadoPort.class,
                () ->
                        (convocatoria, estudiante) ->
                                Optional.of(
                                        new ContextoProgramacion(
                                                convocatoria,
                                                estudiante,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                "SinIniciar",
                                                "Pendiente",
                                                Optional.empty(),
                                                "Publicado",
                                                true,
                                                Instant.EPOCH,
                                                Instant.EPOCH.plusSeconds(3600),
                                                Optional.empty(),
                                                Optional.empty(),
                                                Duration.ofMinutes(30),
                                                Duration.ofMinutes(20),
                                                Instant.EPOCH)));
        for (int i = 0; i < beans.length; i++) {
            registrar(contexto, "dependencia" + i, beans[i]);
        }
        contexto.register(ConfiguracionParticipacion.class);
        contexto.refresh();
        return contexto;
    }

    @Test
    @DisplayName(
            "el único bean de operaciones es el proxy protegido; la fachada sin control no es un"
                    + " bean")
    void unicoBeanEsElProxy() {
        try (var ctx =
                contexto(
                        INTOCABLE,
                        PARTICIPACIONES_INTOCABLE,
                        new TransaccionesContadas(),
                        identidad(Optional.of(ANA)),
                        titularidad(Titularidad.PROPIO))) {
            Map<String, OperacionesParticipacion> operaciones =
                    ctx.getBeansOfType(OperacionesParticipacion.class);
            assertEquals(1, operaciones.size(), operaciones.keySet().toString());
            assertInstanceOf(
                    AccesoProtegidoParticipacion.class, operaciones.values().iterator().next());
            assertEquals(Map.of(), ctx.getBeansOfType(ParticipacionFacade.class));
            assertEquals(Map.of(), ctx.getBeansOfType(FachadaParticipacionTransaccional.class));
            assertEquals(Map.of(), ctx.getBeansOfType(ControlAccesoIntentos.class));
            // Un consumidor que pide la interfaz recibe el proxy, sin ambigüedad.
            assertInstanceOf(
                    AccesoProtegidoParticipacion.class,
                    ctx.getBean(OperacionesParticipacion.class));
        }
    }

    @Test
    @DisplayName("sin proveedor de identidad (M1) el contexto no arranca")
    void sinIdentidadNoArranca() {
        assertThrows(
                UnsatisfiedDependencyException.class,
                () ->
                        contexto(
                                INTOCABLE,
                                PARTICIPACIONES_INTOCABLE,
                                new TransaccionesContadas(),
                                titularidad(Titularidad.PROPIO)));
    }

    @Test
    @DisplayName("sin proveedor de titularidad (M4) el contexto no arranca")
    void sinTitularidadNoArranca() {
        assertThrows(
                UnsatisfiedDependencyException.class,
                () ->
                        contexto(
                                INTOCABLE,
                                PARTICIPACIONES_INTOCABLE,
                                new TransaccionesContadas(),
                                identidad(Optional.of(ANA))));
    }

    @Test
    @DisplayName(
            "con el proveedor real de M1 (DD-02 abierta) toda operación se deniega sin tocar datos")
    void proveedorRealDeM1Deniega() {
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx =
                contexto(
                        INTOCABLE,
                        PARTICIPACIONES_INTOCABLE,
                        transacciones,
                        new IdentidadEstudianteDesdeM1(new DenyingStudentAccessService()),
                        titularidad(Titularidad.PROPIO))) {
            OperacionesParticipacion operaciones = ctx.getBean(OperacionesParticipacion.class);
            assertThrows(
                    EstudianteNoIdentificadoException.class,
                    () -> operaciones.consultarIntento(INTENTO));
            assertThrows(
                    EstudianteNoIdentificadoException.class,
                    () -> operaciones.registrarRespuesta(INTENTO, PREGUNTA, VALOR));
            assertThrows(
                    EstudianteNoIdentificadoException.class,
                    () ->
                            operaciones.finalizarIntento(
                                    INTENTO, MotivoFinalizacion.ENTREGA_ESTUDIANTE));
            assertEquals(0, transacciones.abiertas.get());
        }
    }

    @Test
    @DisplayName("un intento ajeno no se modifica ni se abre transacción")
    void intentoAjeno() {
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx =
                contexto(
                        INTOCABLE,
                        PARTICIPACIONES_INTOCABLE,
                        transacciones,
                        identidad(Optional.of(ANA)),
                        titularidad(Titularidad.AJENO))) {
            OperacionesParticipacion operaciones = ctx.getBean(OperacionesParticipacion.class);
            assertThrows(
                    AccesoIntentoDenegadoException.class,
                    () -> operaciones.actualizarRespuesta(INTENTO, PREGUNTA, VALOR));
            assertEquals(0, transacciones.abiertas.get());
        }
    }

    @Test
    @DisplayName("si M4 está caído la operación falla con su error técnico y no toca datos")
    void m4Caido() {
        TitularidadIntentos caida =
                (intento, estudiante) -> {
                    throw new ConsultaProgramacionNoDisponibleException();
                };
        try (var ctx =
                contexto(
                        INTOCABLE,
                        PARTICIPACIONES_INTOCABLE,
                        new TransaccionesContadas(),
                        identidad(Optional.of(ANA)),
                        caida)) {
            assertThrows(
                    ConsultaProgramacionNoDisponibleException.class,
                    () -> ctx.getBean(OperacionesParticipacion.class).consultarIntento(INTENTO));
        }
    }

    @Test
    @DisplayName("si M1 falla con un error inesperado, se propaga y no hay acceso")
    void m1FallaInesperadamente() {
        IdentidadEstudianteActual rota =
                () -> {
                    throw new IllegalStateException("proveedor de identidad roto");
                };
        try (var ctx =
                contexto(
                        INTOCABLE,
                        PARTICIPACIONES_INTOCABLE,
                        new TransaccionesContadas(),
                        rota,
                        titularidad(Titularidad.PROPIO))) {
            assertThrows(
                    IllegalStateException.class,
                    () -> ctx.getBean(OperacionesParticipacion.class).consultarIntento(INTENTO));
        }
    }

    @Test
    @DisplayName("el titular opera dentro de una transacción por caso de uso")
    void titularOperaEnTransaccion() {
        RepositorioIntentosEnMemoria repositorio = new RepositorioIntentosEnMemoria();
        repositorio.existente(INTENTO, Intento.iniciar());
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx =
                contexto(
                        repositorio,
                        new RepositorioParticipacionesEnMemoria(repositorio),
                        transacciones,
                        identidad(Optional.of(ANA)),
                        titularidad(Titularidad.PROPIO))) {
            OperacionesParticipacion operaciones = ctx.getBean(OperacionesParticipacion.class);
            operaciones.registrarRespuesta(INTENTO, PREGUNTA, VALOR);
            assertEquals(
                    EstadoIntento.FINALIZADO,
                    operaciones
                            .finalizarIntento(INTENTO, MotivoFinalizacion.ENTREGA_ESTUDIANTE)
                            .estado());
            assertEquals(2, transacciones.abiertas.get());
            assertEquals(2, transacciones.confirmadas.get());
            assertTrue(repositorio.guardados() >= 2);
        }
    }

    @Test
    @DisplayName("sin repositorio de participaciones el contexto no arranca")
    void sinParticipacionesNoArranca() {
        assertThrows(
                UnsatisfiedDependencyException.class,
                () ->
                        contexto(
                                INTOCABLE,
                                new TransaccionesContadas(),
                                identidad(Optional.of(ANA)),
                                titularidad(Titularidad.PROPIO)));
    }

    @Test
    @DisplayName(
            "con el proveedor real de M1 (DD-02 abierta) iniciar se deniega sin abrir transacción")
    void proveedorRealDeM1DeniegaIniciar() {
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx =
                contexto(
                        INTOCABLE,
                        PARTICIPACIONES_INTOCABLE,
                        transacciones,
                        new IdentidadEstudianteDesdeM1(new DenyingStudentAccessService()),
                        titularConvocatoria())) {
            assertThrows(
                    EstudianteNoIdentificadoException.class,
                    () ->
                            ctx.getBean(OperacionesParticipacion.class)
                                    .iniciarOReanudar(CONVOCATORIA));
            assertEquals(0, transacciones.abiertas.get());
        }
    }

    @Test
    @DisplayName("una titularidad que no resuelve convocatorias no da acceso para iniciar")
    void titularidadSinConvocatoriasDeniega() {
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx =
                contexto(
                        INTOCABLE,
                        PARTICIPACIONES_INTOCABLE,
                        transacciones,
                        identidad(Optional.of(ANA)),
                        titularidad(Titularidad.PROPIO))) {
            assertThrows(
                    ConvocatoriaNoEncontradaException.class,
                    () ->
                            ctx.getBean(OperacionesParticipacion.class)
                                    .iniciarOReanudar(CONVOCATORIA));
            assertEquals(0, transacciones.abiertas.get());
        }
    }

    @Test
    @DisplayName(
            "ensamblado productivo: el primer inicio falla por elegibilidad no ratificada, revierte"
                    + " y no escribe")
    void primerInicioBloqueadoEnProduccion() {
        RepositorioIntentosEnMemoria repositorio = new RepositorioIntentosEnMemoria();
        RepositorioParticipacionesEnMemoria participaciones =
                new RepositorioParticipacionesEnMemoria(repositorio);
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx =
                contexto(
                        repositorio,
                        participaciones,
                        transacciones,
                        identidad(Optional.of(ANA)),
                        titularConvocatoria())) {
            InicioNoDisponibleException error =
                    assertThrows(
                            InicioNoDisponibleException.class,
                            () ->
                                    ctx.getBean(OperacionesParticipacion.class)
                                            .iniciarOReanudar(CONVOCATORIA));
            assertEquals(DecisionPendiente.ELEGIBILIDAD_ACADEMICA, error.decision());
            assertEquals(1, transacciones.abiertas.get());
            assertEquals(1, transacciones.revertidas.get());
            assertEquals(0, transacciones.confirmadas.get());
            assertEquals(List.of(), participaciones.escrituras());
            assertTrue(participaciones.participacionDe(CONVOCATORIA).isEmpty());
        }
    }

    @Test
    @DisplayName("ensamblado productivo: la reanudación devuelve el intento en curso sin escribir")
    void reanudacionDisponibleEnProduccion() {
        RepositorioIntentosEnMemoria repositorio = new RepositorioIntentosEnMemoria();
        RepositorioParticipacionesEnMemoria participaciones =
                new RepositorioParticipacionesEnMemoria(repositorio);
        Instant inicio = Instant.parse("2026-11-01T14:00:00Z");
        Instant limite = Instant.parse("2026-11-01T15:00:00Z");
        RepositorioParticipacionesEnMemoria.FilaIntento existente =
                participaciones.intentoExistente(CONVOCATORIA, Intento.iniciar(), inicio, limite);
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx =
                contexto(
                        repositorio,
                        participaciones,
                        transacciones,
                        identidad(Optional.of(ANA)),
                        titularConvocatoria())) {
            ResultadoInicio resultado =
                    ctx.getBean(OperacionesParticipacion.class).iniciarOReanudar(CONVOCATORIA);
            assertTrue(resultado.reanudado());
            assertEquals(existente.referencia(), resultado.intento().referencia());
            assertEquals(inicio, resultado.inicio());
            assertEquals(limite, resultado.limiteTemporal());
            assertEquals(1, transacciones.confirmadas.get());
            assertEquals(List.of(), participaciones.escrituras());
        }
    }

    /** Titular de cualquier convocatoria; los intentos siguen siendo ajenos. */
    private static TitularidadIntentos titularConvocatoria() {
        return new TitularidadIntentos() {
            @Override
            public Titularidad de(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
                return Titularidad.AJENO;
            }

            @Override
            public Titularidad deConvocatoria(
                    ReferenciaConvocatoria convocatoria, IdentificadorEstudiante estudiante) {
                return Titularidad.PROPIO;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> void registrar(
            AnnotationConfigApplicationContext contexto, String nombre, T bean) {
        contexto.registerBean(nombre, (Class<T>) bean.getClass(), () -> bean);
    }

    private static IdentidadEstudianteActual identidad(Optional<IdentificadorEstudiante> actual) {
        return () -> actual;
    }

    private static TitularidadIntentos titularidad(Titularidad respuesta) {
        return (intento, estudiante) -> respuesta;
    }

    /** Gestor de transacciones sin recursos: solo cuenta aperturas y confirmaciones. */
    static final class TransaccionesContadas implements PlatformTransactionManager {
        final AtomicInteger abiertas = new AtomicInteger();
        final AtomicInteger confirmadas = new AtomicInteger();
        final AtomicInteger revertidas = new AtomicInteger();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definicion) {
            abiertas.incrementAndGet();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus estado) {
            confirmadas.incrementAndGet();
        }

        @Override
        public void rollback(TransactionStatus estado) {
            revertidas.incrementAndGet();
        }
    }
}
