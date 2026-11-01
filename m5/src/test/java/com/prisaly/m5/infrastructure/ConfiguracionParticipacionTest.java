package com.prisaly.m5.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ParticipacionFacade;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.RepositorioIntentosEnMemoria;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.application.acceso.IdentidadEstudianteActual;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.application.port.RepositorioIntentos;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.Intento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.infrastructure.identidad.IdentidadEstudianteDesdeM1;

/**
 * Contexto de Spring de M5 sin base de datos: qué beans publica
 * {@link ConfiguracionParticipacion} y cómo se comportan ante fallos de M1 y M4.
 */
@DisplayName("ConfiguracionParticipacion (contexto Spring)")
class ConfiguracionParticipacionTest {

    private static final ReferenciaIntento INTENTO = new ReferenciaIntento(UUID.randomUUID().toString());
    private static final IdentificadorEstudiante ANA = new IdentificadorEstudiante(UUID.randomUUID().toString());
    private static final ReferenciaPregunta PREGUNTA = new ReferenciaPregunta("P-1");
    private static final ValorRespuesta VALOR = new ValorRespuesta("O-1");

    /** Repositorio que falla si alguien lo usa: prueba que una denegación no llega a la persistencia. */
    private static final RepositorioIntentos INTOCABLE = new RepositorioIntentos() {
        @Override
        public Optional<Intento> buscar(ReferenciaIntento referencia) {
            throw new AssertionError("una operación denegada no debe leer el intento");
        }

        @Override
        public void guardar(ReferenciaIntento referencia, Intento intento) {
            throw new AssertionError("una operación denegada no debe escribir");
        }
    };

    private AnnotationConfigApplicationContext contexto(Object... beans) {
        AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext();
        for (int i = 0; i < beans.length; i++) {
            registrar(contexto, "dependencia" + i, beans[i]);
        }
        contexto.register(ConfiguracionParticipacion.class);
        contexto.refresh();
        return contexto;
    }

    @Test
    @DisplayName("el único bean de operaciones es el proxy protegido; la fachada sin control no es un bean")
    void unicoBeanEsElProxy() {
        try (var ctx = contexto(INTOCABLE, new TransaccionesContadas(), identidad(Optional.of(ANA)),
                titularidad(Titularidad.PROPIO))) {
            Map<String, OperacionesParticipacion> operaciones = ctx.getBeansOfType(OperacionesParticipacion.class);
            assertEquals(1, operaciones.size(), operaciones.keySet().toString());
            assertInstanceOf(AccesoProtegidoParticipacion.class, operaciones.values().iterator().next());
            assertEquals(Map.of(), ctx.getBeansOfType(ParticipacionFacade.class));
            assertEquals(Map.of(), ctx.getBeansOfType(FachadaParticipacionTransaccional.class));
            assertEquals(Map.of(), ctx.getBeansOfType(ControlAccesoIntentos.class));
            // Un consumidor que pide la interfaz recibe el proxy, sin ambigüedad.
            assertInstanceOf(AccesoProtegidoParticipacion.class, ctx.getBean(OperacionesParticipacion.class));
        }
    }

    @Test
    @DisplayName("sin proveedor de identidad (M1) el contexto no arranca")
    void sinIdentidadNoArranca() {
        assertThrows(UnsatisfiedDependencyException.class,
                () -> contexto(INTOCABLE, new TransaccionesContadas(), titularidad(Titularidad.PROPIO)));
    }

    @Test
    @DisplayName("sin proveedor de titularidad (M4) el contexto no arranca")
    void sinTitularidadNoArranca() {
        assertThrows(UnsatisfiedDependencyException.class,
                () -> contexto(INTOCABLE, new TransaccionesContadas(), identidad(Optional.of(ANA))));
    }

    @Test
    @DisplayName("con el proveedor real de M1 (DD-02 abierta) toda operación se deniega sin tocar datos")
    void proveedorRealDeM1Deniega() {
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx = contexto(INTOCABLE, transacciones,
                new IdentidadEstudianteDesdeM1(new DenyingStudentAccessService()), titularidad(Titularidad.PROPIO))) {
            OperacionesParticipacion operaciones = ctx.getBean(OperacionesParticipacion.class);
            assertThrows(EstudianteNoIdentificadoException.class, () -> operaciones.consultarIntento(INTENTO));
            assertThrows(EstudianteNoIdentificadoException.class,
                    () -> operaciones.registrarRespuesta(INTENTO, PREGUNTA, VALOR));
            assertThrows(EstudianteNoIdentificadoException.class,
                    () -> operaciones.finalizarIntento(INTENTO, MotivoFinalizacion.ENTREGA_ESTUDIANTE));
            assertEquals(0, transacciones.abiertas.get());
        }
    }

    @Test
    @DisplayName("un intento ajeno no se modifica ni se abre transacción")
    void intentoAjeno() {
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx = contexto(INTOCABLE, transacciones, identidad(Optional.of(ANA)), titularidad(Titularidad.AJENO))) {
            OperacionesParticipacion operaciones = ctx.getBean(OperacionesParticipacion.class);
            assertThrows(AccesoIntentoDenegadoException.class,
                    () -> operaciones.actualizarRespuesta(INTENTO, PREGUNTA, VALOR));
            assertEquals(0, transacciones.abiertas.get());
        }
    }

    @Test
    @DisplayName("si M4 está caído la operación falla con su error técnico y no toca datos")
    void m4Caido() {
        TitularidadIntentos caida = (intento, estudiante) -> {
            throw new ConsultaProgramacionNoDisponibleException();
        };
        try (var ctx = contexto(INTOCABLE, new TransaccionesContadas(), identidad(Optional.of(ANA)), caida)) {
            assertThrows(ConsultaProgramacionNoDisponibleException.class,
                    () -> ctx.getBean(OperacionesParticipacion.class).consultarIntento(INTENTO));
        }
    }

    @Test
    @DisplayName("si M1 falla con un error inesperado, se propaga y no hay acceso")
    void m1FallaInesperadamente() {
        IdentidadEstudianteActual rota = () -> {
            throw new IllegalStateException("proveedor de identidad roto");
        };
        try (var ctx = contexto(INTOCABLE, new TransaccionesContadas(), rota, titularidad(Titularidad.PROPIO))) {
            assertThrows(IllegalStateException.class,
                    () -> ctx.getBean(OperacionesParticipacion.class).consultarIntento(INTENTO));
        }
    }

    @Test
    @DisplayName("el titular opera dentro de una transacción por caso de uso")
    void titularOperaEnTransaccion() {
        RepositorioIntentosEnMemoria repositorio = new RepositorioIntentosEnMemoria();
        repositorio.existente(INTENTO, Intento.iniciar());
        TransaccionesContadas transacciones = new TransaccionesContadas();
        try (var ctx = contexto(repositorio, transacciones, identidad(Optional.of(ANA)), titularidad(Titularidad.PROPIO))) {
            OperacionesParticipacion operaciones = ctx.getBean(OperacionesParticipacion.class);
            operaciones.registrarRespuesta(INTENTO, PREGUNTA, VALOR);
            assertEquals(EstadoIntento.FINALIZADO,
                    operaciones.finalizarIntento(INTENTO, MotivoFinalizacion.ENTREGA_ESTUDIANTE).estado());
            assertEquals(2, transacciones.abiertas.get());
            assertEquals(2, transacciones.confirmadas.get());
            assertTrue(repositorio.guardados() >= 2);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void registrar(AnnotationConfigApplicationContext contexto, String nombre, T bean) {
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
