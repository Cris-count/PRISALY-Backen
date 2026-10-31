package com.prisaly.m5.presentation;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;

import com.prisaly.m5.application.DatoAplicacionInvalidoException;
import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;
import com.prisaly.m5.domain.intento.MotivoFinalizacionRequeridoException;
import com.prisaly.m5.domain.intento.RespuestaDuplicadaException;
import com.prisaly.m5.domain.intento.RespuestaNoRegistradaException;
import com.prisaly.m5.domain.intento.TransicionIntentoInvalidaException;
import com.prisaly.m5.domain.respuesta.DatoRespuestaInvalidoException;
import com.prisaly.m5.infrastructure.persistencia.PersistenciaIntentoException;

/**
 * Traducción de las excepciones de M5 a estado HTTP, código y mensaje.
 *
 * <p>Independiente de Spring MVC para poder probarse sin servidor. Los
 * códigos son una PROPUESTA: ARQ-008.3 no está en el repositorio y deberá
 * ratificarlos. Los errores internos y de la base devuelven mensajes
 * genéricos para no exponer detalles de persistencia.
 */
public final class TraductorErroresM5 {

    /** Error traducido: estado HTTP y cuerpo. */
    public record ErrorHttp(int estado, ErrorResponse cuerpo) {
    }

    static final String MENSAJE_CONFLICTO_DATOS = "La operación viola una restricción de los datos";
    static final String MENSAJE_INTERNO = "No fue posible completar la operación sobre el intento";

    private TraductorErroresM5() {
    }

    /** Traducción de una excepción conocida de M5; vacío si no corresponde a M5. */
    public static Optional<ErrorHttp> traducir(Throwable error) {
        if (error instanceof IntentoNoEncontradoException e) {
            return error(404, "INTENTO_NO_ENCONTRADO", e.getMessage());
        }
        if (error instanceof DatoAplicacionInvalidoException
                || error instanceof DatoRespuestaInvalidoException
                || error instanceof MotivoFinalizacionRequeridoException) {
            return error(400, "DATOS_INVALIDOS", error.getMessage());
        }
        if (error instanceof IntentoFinalizadoException e) {
            return error(409, "INTENTO_FINALIZADO", e.getMessage());
        }
        if (error instanceof TransicionIntentoInvalidaException e) {
            return error(409, "TRANSICION_INVALIDA", e.getMessage());
        }
        if (error instanceof RespuestaDuplicadaException e) {
            return error(409, "RESPUESTA_DUPLICADA", e.getMessage());
        }
        if (error instanceof RespuestaNoRegistradaException e) {
            return error(409, "RESPUESTA_NO_REGISTRADA", e.getMessage());
        }
        if (error instanceof DataIntegrityViolationException) {
            return error(409, "CONFLICTO_DATOS", MENSAJE_CONFLICTO_DATOS);
        }
        if (error instanceof PersistenciaIntentoException) {
            return error(500, "ERROR_INTERNO", MENSAJE_INTERNO);
        }
        return Optional.empty();
    }

    private static Optional<ErrorHttp> error(int estado, String codigo, String mensaje) {
        return Optional.of(new ErrorHttp(estado, new ErrorResponse(codigo, mensaje)));
    }
}
