package com.prisaly.m5.presentation;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m5.application.ParticipacionAplicacionException;
import com.prisaly.m5.domain.ParticipacionDominioException;
import com.prisaly.m5.infrastructure.persistencia.PersistenciaIntentoException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejo centralizado de errores HTTP de M5, limitado a los controladores del paquete {@code
 * com.prisaly.m5} (mismo criterio que M1 y M4).
 *
 * <p>Hoy M5 no tiene controladores: los endpoints de estudiante esperan ARQ-008.3, DD-02 y el
 * contrato de titularidad (m5/docs). Este manejador deja preparada la traducción para cuando
 * existan.
 */
@RestControllerAdvice(basePackages = "com.prisaly.m5")
public class ManejadorErroresM5 {

    @ExceptionHandler({
        ConsultaProgramacionNoDisponibleException.class,
        ParticipacionDominioException.class,
        ParticipacionAplicacionException.class,
        PersistenciaIntentoException.class,
        DataIntegrityViolationException.class
    })
    public ResponseEntity<ErrorResponse> manejar(RuntimeException error) {
        TraductorErroresM5.ErrorHttp traducido =
                TraductorErroresM5.traducir(error).orElseThrow(() -> error);
        return ResponseEntity.status(traducido.estado()).body(traducido.cuerpo());
    }

    /** Mismo código que M1 para datos de entrada inválidos. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException error) {
        return entradaInvalida();
    }

    /**
     * Cuerpo mal formado o un parámetro de ruta con tipo incorrecto: el mismo 400
     * {@code VALIDATION_ERROR}, sin el mensaje del conversor, que puede contener
     * nombres de clases internas o fragmentos del cuerpo recibido.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> entradaMalFormada(Exception error) {
        return entradaInvalida();
    }

    private static ResponseEntity<ErrorResponse> entradaInvalida() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed"));
    }
}
