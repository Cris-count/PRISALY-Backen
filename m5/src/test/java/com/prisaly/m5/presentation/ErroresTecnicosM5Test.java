package com.prisaly.m5.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.presentation.rest.IntentosEstudianteM5Controlador;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

/** DC-M5M6-MACRO-02: a technical persistence failure is a generic 500, never an unhandled error or a detail leak. */
class ErroresTecnicosM5Test {
    private static final String DETALLE = "jdbc:postgresql://interno/secreto";

    @Test void fallosTecnicosDeDatosYTransaccionSeTraducenSinDetalle() {
        for (RuntimeException fallo : List.of(new DataAccessResourceFailureException(DETALLE),
                new CannotAcquireLockException(DETALLE), new CannotCreateTransactionException(DETALLE),
                new TransactionSystemException(DETALLE))) {
            var http = TraductorErroresM5.traducir(fallo);
            assertTrue(http.isPresent(), fallo.getClass().getName());
            assertEquals(500, http.get().estado());
            assertEquals("ERROR_INTERNO", http.get().cuerpo().code());
            assertFalse(http.get().cuerpo().message().contains("secreto"));
        }
    }

    @Test void integridadSigueSiendoConflictoYNoErrorInterno() {
        assertEquals(409, TraductorErroresM5.traducir(new DataIntegrityViolationException(DETALLE)).orElseThrow().estado());
    }

    @Test void elDespachoMvcRespondeJsonGenericoAnteCaidaDeLaBase() throws Exception {
        var titularidad = mock(TitularidadIntentos.class);
        when(titularidad.de(any(), any())).thenReturn(Titularidad.PROPIO);
        var operaciones = mock(OperacionesParticipacion.class);
        when(operaciones.consultarIntento(any())).thenThrow(new CannotCreateTransactionException(DETALLE));
        var control = new ControlAccesoIntentos(() -> Optional.of(new IdentificadorEstudiante(UUID.randomUUID().toString())), titularidad);
        var mvc = MockMvcBuilders.standaloneSetup(new IntentosEstudianteM5Controlador(new AccesoProtegidoParticipacion(control, operaciones)))
                .setControllerAdvice(new ManejadorErroresM5()).build();
        mvc.perform(get("/v1/estudiantes/intentos/" + UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERROR_INTERNO"));
    }
}
