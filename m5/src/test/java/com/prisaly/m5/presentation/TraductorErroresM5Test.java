package com.prisaly.m5.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoIntentoDenegadoException;
import com.prisaly.m5.application.acceso.EstudianteNoIdentificadoException;
import com.prisaly.m5.domain.intento.EstadoIntento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacionRequeridoException;
import com.prisaly.m5.domain.intento.RespuestaDuplicadaException;
import com.prisaly.m5.domain.intento.RespuestaNoRegistradaException;
import com.prisaly.m5.domain.intento.TransicionIntentoInvalidaException;
import com.prisaly.m5.domain.respuesta.DatoRespuestaInvalidoException;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.infrastructure.persistencia.PersistenciaIntentoException;

@DisplayName("TraductorErroresM5: excepciones de M5 → HTTP")
class TraductorErroresM5Test {

    private static final ReferenciaPregunta PREGUNTA = new ReferenciaPregunta("P-1");

    static Stream<Arguments> excepcionesDeM5() {
        return Stream.of(
                Arguments.of(new EstudianteNoIdentificadoException(), 403, "STUDENT_IDENTITY_UNAVAILABLE"),
                Arguments.of(new AccesoIntentoDenegadoException(), 403, "FORBIDDEN"),
                Arguments.of(new IntentoNoEncontradoException(new ReferenciaIntento("I-1")), 404, "INTENTO_NO_ENCONTRADO"),
                Arguments.of(new DatoAplicacionInvalidoException("x"), 400, "DATOS_INVALIDOS"),
                Arguments.of(new DatoRespuestaInvalidoException("x"), 400, "DATOS_INVALIDOS"),
                Arguments.of(new MotivoFinalizacionRequeridoException(), 400, "DATOS_INVALIDOS"),
                Arguments.of(new IntentoFinalizadoException("registrar una respuesta"), 409, "INTENTO_FINALIZADO"),
                Arguments.of(new TransicionIntentoInvalidaException(EstadoIntento.FINALIZADO, EstadoIntento.FINALIZADO),
                        409, "TRANSICION_INVALIDA"),
                Arguments.of(new RespuestaDuplicadaException(PREGUNTA), 409, "RESPUESTA_DUPLICADA"),
                Arguments.of(new RespuestaNoRegistradaException(PREGUNTA), 409, "RESPUESTA_NO_REGISTRADA"),
                Arguments.of(new DataIntegrityViolationException("detalle SQL"), 409, "CONFLICTO_DATOS"),
                Arguments.of(new PersistenciaIntentoException("detalle interno"), 500, "ERROR_INTERNO"));
    }

    @ParameterizedTest(name = "{0} → {1} {2}")
    @MethodSource("excepcionesDeM5")
    void traduceCadaExcepcion(RuntimeException error, int estado, String codigo) {
        TraductorErroresM5.ErrorHttp traducido = TraductorErroresM5.traducir(error).orElseThrow();

        assertEquals(estado, traducido.estado());
        assertEquals(codigo, traducido.cuerpo().code());
    }

    @Test
    @DisplayName("los errores de reglas conservan el mensaje del dominio")
    void conservaMensajeDeDominio() {
        IntentoFinalizadoException error = new IntentoFinalizadoException("registrar una respuesta");

        assertEquals(error.getMessage(), TraductorErroresM5.traducir(error).orElseThrow().cuerpo().message());
    }

    @Test
    @DisplayName("los errores de la base e internos no exponen detalles")
    void noExponeDetallesInternos() {
        List<RuntimeException> internos = List.of(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_respuesta_idx1\""),
                new PersistenciaIntentoException("La base contiene respuestas que el intento no conoce"));

        for (RuntimeException error : internos) {
            String mensaje = TraductorErroresM5.traducir(error).orElseThrow().cuerpo().message();
            assertFalse(mensaje.contains("uq_respuesta"), mensaje);
            assertFalse(mensaje.contains("respuestas que el intento"), mensaje);
        }
    }

    @Test
    @DisplayName("no traduce excepciones ajenas a M5")
    void ignoraExcepcionesAjenas() {
        assertEquals(Optional.empty(), TraductorErroresM5.traducir(new IllegalStateException("otro")));
    }

    @Test
    @DisplayName("todos los códigos tienen estado 4xx o 5xx")
    void estadosDeError() {
        excepcionesDeM5().forEach(argumentos -> {
            int estado = TraductorErroresM5.traducir((Throwable) argumentos.get()[0]).orElseThrow().estado();
            assertTrue(estado >= 400 && estado < 600);
        });
    }
}
