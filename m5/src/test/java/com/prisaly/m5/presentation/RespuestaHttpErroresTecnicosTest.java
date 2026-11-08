package com.prisaly.m5.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.prisaly.m5.application.OperacionesParticipacion;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.application.acceso.ControlAccesoIntentos;
import com.prisaly.m5.application.acceso.IdentificadorEstudiante;
import com.prisaly.m5.application.acceso.Titularidad;
import com.prisaly.m5.application.acceso.TitularidadIntentos;
import com.prisaly.m5.presentation.rest.IntentosEstudianteM5Controlador;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.UnexpectedRollbackException;

/** Review of DC-M5M6-MACRO-02: the full HTTP body carries a fixed message; integrity stays 409. */
class RespuestaHttpErroresTecnicosTest {
    private static final String SENSIBLE = "jdbc:postgresql://db-interna:5432/prisaly?password=hunter2 SELECT \"Intento\"";

    @Test void cuerpoCompletoDeFalloDeDatosNoContieneInformacionSensible() throws Exception {
        verificarGenerico(respuesta(new DataAccessResourceFailureException(SENSIBLE, new IllegalStateException(SENSIBLE))));
    }

    @Test void cuerpoCompletoDeFalloTransaccionalNoContieneInformacionSensible() throws Exception {
        verificarGenerico(respuesta(new CannotCreateTransactionException(SENSIBLE, new IllegalStateException(SENSIBLE))));
        verificarGenerico(respuesta(new UnexpectedRollbackException(SENSIBLE)));
    }

    @Test void integridadConservaEl409SinDetalle() throws Exception {
        for (RuntimeException error : new RuntimeException[] {new DataIntegrityViolationException(SENSIBLE), new DuplicateKeyException(SENSIBLE)}) {
            var r = respuesta(error);
            assertEquals(409, r.getStatus());
            assertTrue(r.getContentAsString().contains("CONFLICTO_DATOS"));
            assertSinFiltrar(r.getContentAsString());
        }
    }

    private static void verificarGenerico(MockHttpServletResponse r) throws Exception {
        assertEquals(500, r.getStatus());
        assertTrue(r.getContentAsString().contains("ERROR_INTERNO"));
        assertSinFiltrar(r.getContentAsString());
    }

    private static void assertSinFiltrar(String cuerpo) {
        for (String fragmento : new String[] {"jdbc", "hunter2", "SELECT", "Intento\"", "Exception", "org.springframework"}) {
            assertFalse(cuerpo.contains(fragmento), fragmento + " in " + cuerpo);
        }
    }

    private static MockHttpServletResponse respuesta(RuntimeException fallo) throws Exception {
        var titularidad = mock(TitularidadIntentos.class);
        when(titularidad.de(any(), any())).thenReturn(Titularidad.PROPIO);
        var operaciones = mock(OperacionesParticipacion.class);
        when(operaciones.consultarIntento(any())).thenThrow(fallo);
        var control = new ControlAccesoIntentos(() -> Optional.of(new IdentificadorEstudiante(UUID.randomUUID().toString())), titularidad);
        return MockMvcBuilders.standaloneSetup(new IntentosEstudianteM5Controlador(new AccesoProtegidoParticipacion(control, operaciones)))
                .setControllerAdvice(new ManejadorErroresM5()).build()
                .perform(get("/v1/estudiantes/intentos/" + UUID.randomUUID())).andReturn().getResponse();
    }
}
