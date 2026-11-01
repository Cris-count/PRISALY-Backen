package com.prisaly.m5.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.prisaly.m5.application.IntentoNoEncontradoException;
import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.domain.intento.IntentoFinalizadoException;

@DisplayName("ManejadorErroresM5")
class ManejadorErroresM5Test {

    private final ManejadorErroresM5 manejador = new ManejadorErroresM5();

    @Test
    @DisplayName("solo aplica a los controladores de M5")
    void limitadoAlPaqueteDeM5() {
        RestControllerAdvice anotacion = ManejadorErroresM5.class.getAnnotation(RestControllerAdvice.class);

        assertEquals(1, anotacion.basePackages().length);
        assertEquals("com.prisaly.m5", anotacion.basePackages()[0]);
    }

    @Test
    @DisplayName("responde 404 para un intento inexistente")
    void respondeNoEncontrado() {
        ResponseEntity<ErrorResponse> respuesta =
                manejador.manejar(new IntentoNoEncontradoException(new ReferenciaIntento("I-1")));

        assertEquals(404, respuesta.getStatusCode().value());
        assertEquals("INTENTO_NO_ENCONTRADO", respuesta.getBody().code());
    }

    @Test
    @DisplayName("responde 409 para un intento finalizado")
    void respondeConflicto() {
        ResponseEntity<ErrorResponse> respuesta =
                manejador.manejar(new IntentoFinalizadoException("registrar una respuesta"));

        assertEquals(409, respuesta.getStatusCode().value());
        assertEquals("INTENTO_FINALIZADO", respuesta.getBody().code());
    }

    @Test
    @DisplayName("relanza una excepción que no sabe traducir en lugar de inventar una respuesta")
    void relanzaExcepcionesNoTraducibles() {
        IllegalStateException ajena = new IllegalStateException("ajena");

        assertSame(ajena, assertThrows(IllegalStateException.class, () -> manejador.manejar(ajena)));
    }

    @Test
    @DisplayName("responde 400 VALIDATION_ERROR, igual que M1, ante datos de entrada inválidos")
    void respondeValidacion() throws NoSuchMethodException {
        MethodParameter parametro = new MethodParameter(
                ManejadorErroresM5Test.class.getDeclaredMethod("metodoDePrueba", Object.class), 0);
        MethodArgumentNotValidException error = new MethodArgumentNotValidException(
                parametro, new BeanPropertyBindingResult(new Object(), "solicitud"));

        ResponseEntity<ErrorResponse> respuesta = manejador.validacion(error);

        assertEquals(400, respuesta.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", respuesta.getBody().code());
    }

    @SuppressWarnings("unused")
    private void metodoDePrueba(Object solicitud) {
    }
}
