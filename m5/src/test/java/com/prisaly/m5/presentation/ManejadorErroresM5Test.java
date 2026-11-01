package com.prisaly.m5.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @Test
    @DisplayName("un cuerpo mal formado responde 400 sin el mensaje del conversor")
    void cuerpoMalFormado() {
        HttpMessageNotReadableException error = new HttpMessageNotReadableException(
                "JSON parse error: Cannot deserialize com.prisaly.m5.Interna from {\"secreto\":1}",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> respuesta = manejador.entradaMalFormada(error);

        assertEquals(400, respuesta.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", respuesta.getBody().code());
        assertFalse(respuesta.getBody().message().contains("secreto"));
        assertFalse(respuesta.getBody().message().contains("com.prisaly"));
    }

    @Test
    @DisplayName("un parámetro con tipo incorrecto responde 400 sin revelar el valor ni el tipo interno")
    void tipoIncorrecto() throws NoSuchMethodException {
        MethodParameter parametro = new MethodParameter(
                ManejadorErroresM5Test.class.getDeclaredMethod("metodoDePrueba", Object.class), 0);
        MethodArgumentTypeMismatchException error = new MethodArgumentTypeMismatchException(
                "<script>", UUID.class, "intento", parametro, new IllegalArgumentException("uuid"));

        ResponseEntity<ErrorResponse> respuesta = manejador.entradaMalFormada(error);

        assertEquals(400, respuesta.getStatusCode().value());
        assertFalse(respuesta.getBody().message().contains("<script>"));
    }

    @SuppressWarnings("unused")
    private void metodoDePrueba(Object solicitud) {
    }
}
