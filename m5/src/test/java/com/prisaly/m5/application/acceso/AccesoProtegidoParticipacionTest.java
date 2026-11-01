package com.prisaly.m5.application.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.MotivoFinalizacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;

@DisplayName("AccesoProtegidoParticipacion (proxy de protección)")
class AccesoProtegidoParticipacionTest {

    private static final ReferenciaIntento PROPIO = new ReferenciaIntento("I-ana");
    private static final ReferenciaIntento AJENO = new ReferenciaIntento("I-luis");
    private static final ReferenciaIntento INEXISTENTE = new ReferenciaIntento("I-x");
    private static final ReferenciaPregunta PREGUNTA = new ReferenciaPregunta("P-1");
    private static final ValorRespuesta VALOR = new ValorRespuesta("O-1");
    private static final IdentificadorEstudiante ANA = new IdentificadorEstudiante("E-ana");

    private Optional<IdentificadorEstudiante> autenticado = Optional.of(ANA);
    private final OperacionesRegistradas operaciones = new OperacionesRegistradas();

    private Titularidad relacion(ReferenciaIntento intento, IdentificadorEstudiante estudiante) {
        if (INEXISTENTE.equals(intento)) {
            return Titularidad.INEXISTENTE;
        }
        return PROPIO.equals(intento) && ANA.equals(estudiante) ? Titularidad.PROPIO : Titularidad.AJENO;
    }

    private final AccesoProtegidoParticipacion acceso = new AccesoProtegidoParticipacion(
            new ControlAccesoIntentos(() -> autenticado, this::relacion), operaciones);

    private List<Executable> lasCuatroOperaciones(ReferenciaIntento intento) {
        return List.of(
                () -> acceso.consultarIntento(intento),
                () -> acceso.registrarRespuesta(intento, PREGUNTA, VALOR),
                () -> acceso.actualizarRespuesta(intento, PREGUNTA, VALOR),
                () -> acceso.finalizarIntento(intento, MotivoFinalizacion.ENTREGA_ESTUDIANTE));
    }

    @Test
    @DisplayName("el titular autenticado llega a las cuatro operaciones")
    void titularDelega() throws Throwable {
        for (Executable operacion : lasCuatroOperaciones(PROPIO)) {
            operacion.execute();
        }
        assertEquals(List.of("consultar", "registrar", "actualizar", "finalizar"), operaciones.llamadas);
    }

    @Test
    @DisplayName("un intento ajeno se deniega en las cuatro operaciones sin delegar")
    void intentoAjenoSeDeniega() {
        for (Executable operacion : lasCuatroOperaciones(AJENO)) {
            assertThrows(AccesoIntentoDenegadoException.class, operacion::execute);
        }
        assertEquals(List.of(), operaciones.llamadas);
    }

    @Test
    @DisplayName("sin identidad no se delega ninguna operación")
    void sinIdentidadSeDeniega() {
        autenticado = Optional.empty();
        for (Executable operacion : lasCuatroOperaciones(PROPIO)) {
            assertThrows(EstudianteNoIdentificadoException.class, operacion::execute);
        }
        assertEquals(List.of(), operaciones.llamadas);
    }

    @Test
    @DisplayName("un intento inexistente se informa sin delegar")
    void intentoInexistente() {
        for (Executable operacion : lasCuatroOperaciones(INEXISTENTE)) {
            assertThrows(IntentoNoEncontradoException.class, operacion::execute);
        }
        assertEquals(List.of(), operaciones.llamadas);
    }

    @Test
    @DisplayName("la respuesta de la operación delegada se devuelve sin cambios")
    void devuelveElResumenDelegado() {
        assertSame(operaciones.resumen, acceso.consultarIntento(PROPIO));
    }

    @Test
    @DisplayName("ninguna operación recibe la identidad del estudiante como parámetro")
    void identidadNoLlegaDelCliente() {
        for (Method metodo : AccesoProtegidoParticipacion.class.getDeclaredMethods()) {
            assertEquals(List.of(), Arrays.stream(metodo.getParameterTypes())
                    .filter(IdentificadorEstudiante.class::equals).toList(), metodo.getName());
        }
    }

    @Test
    @DisplayName("rechaza dependencias nulas")
    void rechazaNulos() {
        assertThrows(DatoAplicacionInvalidoException.class, () -> new AccesoProtegidoParticipacion(null, operaciones));
    }

    /** Doble de las operaciones: registra qué se delegó. */
    private static final class OperacionesRegistradas implements OperacionesParticipacion {
        private final List<String> llamadas = new ArrayList<>();
        private final ResumenIntento resumen = new ResumenIntento(PROPIO, EstadoIntento.EN_CURSO, Optional.empty(), List.of());

        @Override
        public ResumenIntento consultarIntento(ReferenciaIntento intento) {
            llamadas.add("consultar");
            return resumen;
        }

        @Override
        public ResumenIntento registrarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
            llamadas.add("registrar");
            return resumen;
        }

        @Override
        public ResumenIntento actualizarRespuesta(ReferenciaIntento intento, ReferenciaPregunta pregunta, ValorRespuesta valor) {
            llamadas.add("actualizar");
            return resumen;
        }

        @Override
        public ResumenIntento finalizarIntento(ReferenciaIntento intento, MotivoFinalizacion motivo) {
            llamadas.add("finalizar");
            return resumen;
        }
    }
}
