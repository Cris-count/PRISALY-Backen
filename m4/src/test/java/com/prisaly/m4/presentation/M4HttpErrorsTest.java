package com.prisaly.m4.presentation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;
import com.prisaly.m4.presentation.controller.SesionController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLException;

class M4HttpErrorsTest {
    private static final String ROOT = "/m4/sesiones";
    private static final String DOCENTE = "92d59425-fbc4-4e0a-9f5f-fc46159edba1";
    private static final String SESION = "99f4b085-2b4e-43e5-8da0-d9bc5fac90f8";
    private static final String VALID_JSON =
            """
{"casoId":"790a1820-97ba-4190-8e37-110bc110f7fb","titulo":"Sesión",
 "fechaInicio":"2030-11-05T09:00:00Z","fechaFin":"2030-11-05T11:00:00Z","tiempoMaximoParticipacionSegundos":1800}
""";
    private SesionAcademicaService service;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        service = mock(SesionAcademicaService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new SesionController(service))
                        .setControllerAdvice(new M4ExceptionHandler())
                        .build();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"/no-es-uuid", "?docenteId=invalido", "/" + SESION + "/acceso/invalido"})
    void uuidInvalidoResponde400(String ruta) throws Exception {
        mvc.perform(get(ROOT + ruta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M4-SOLICITUD-INVALIDA"));
        verifyNoInteractions(service);
    }

    @Test
    void sinIdentidadAutenticadaResponde401AunqueSeOmitaCabeceraOpcional() throws Exception {
        when(service.resolverDocenteAutenticado(null))
                .thenThrow(
                        new ReglaNegocioException(
                                "M4-NO-AUTENTICADO", "Se requiere autenticación"));
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("M4-NO-AUTENTICADO"));
        verify(service).resolverDocenteAutenticado(null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void cabeceraUuidInvalidaResponde400() throws Exception {
        mvc.perform(
                        post(ROOT)
                                .header("X-Docente-Id", "invalido")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"casoId\":\"invalido\"}"})
    void cuerpoInvalidoResponde400(String body) throws Exception {
        mvc.perform(
                        post(ROOT)
                                .header("X-Docente-Id", DOCENTE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M4-SOLICITUD-INVALIDA"));
        verifyNoInteractions(service);
    }

    @Test
    void fechaInvalidaResponde400() throws Exception {
        mvc.perform(
                        post(ROOT)
                                .header("X-Docente-Id", DOCENTE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        VALID_JSON.replace(
                                                "2030-11-05T09:00:00Z", "fecha-invalida")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void enumDesconocidoResponde400() throws Exception {
        mvc.perform(
                        patch(ROOT + "/" + SESION + "/estado")
                                .header("X-Docente-Id", DOCENTE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nuevoEstado\":\"INVENTADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M4-SOLICITUD-INVALIDA"));
        verifyNoInteractions(service);
    }

    @Test
    void validacionDeCamposConservaCodigo() throws Exception {
        mvc.perform(
                        post(ROOT)
                                .header("X-Docente-Id", DOCENTE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M4-VALIDACION"));
        verifyNoInteractions(service);
    }

    @Test
    void metodoNoPermitidoConservaCabeceraAllow() throws Exception {
        mvc.perform(delete(ROOT + "/" + SESION))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
        verifyNoInteractions(service);
    }

    @Test
    void tipoDeContenidoNoSoportadoResponde415() throws Exception {
        mvc.perform(
                        post(ROOT)
                                .header("X-Docente-Id", DOCENTE)
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(VALID_JSON))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("M4-TIPO-CONTENIDO-NO-SOPORTADO"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({
        "uq_sesion_caso_docente_fechas,M4-SESION-DUPLICADA",
        "uq_convocatoria_sesion_estudiante,M4-CONVOCATORIA-DUPLICADA",
        "uq_convocatoria_idx1,M4-CONVOCATORIA-DUPLICADA"
    })
    void duplicadosConocidosResponden409(String constraint, String codigo) throws Exception {
        when(service.consultarSesionAutorizada(any())).thenThrow(integridad("23505", constraint));
        mvc.perform(get(ROOT + "/" + SESION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(codigo))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "SQL-PRIVADO"))));
    }

    @ParameterizedTest
    @CsvSource({"23505,restriccion_desconocida", "23514,uq_sesion_caso_docente_fechas"})
    void integridadDesconocidaNoSeDisfrazaDeDuplicado(String state, String constraint)
            throws Exception {
        when(service.consultarSesionAutorizada(any())).thenThrow(integridad(state, constraint));
        mvc.perform(get(ROOT + "/" + SESION))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("M4-ERROR-INTERNO"))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "optimistaSpring",
                "optimistaJpa",
                "pesimistaSpring",
                "pesimistaJpa",
                "timeoutJpa"
            })
    void conflictosDeConcurrenciaResponden409(String tipo) throws Exception {
        RuntimeException error =
                switch (tipo) {
                    case "optimistaSpring" -> new OptimisticLockingFailureException("SQL-PRIVADO");
                    case "optimistaJpa" ->
                            new jakarta.persistence.OptimisticLockException("SQL-PRIVADO");
                    case "pesimistaSpring" -> new PessimisticLockingFailureException("SQL-PRIVADO");
                    case "pesimistaJpa" ->
                            new jakarta.persistence.PessimisticLockException("SQL-PRIVADO");
                    default -> new jakarta.persistence.LockTimeoutException("SQL-PRIVADO");
                };
        when(service.consultarSesionAutorizada(any())).thenThrow(error);
        mvc.perform(get(ROOT + "/" + SESION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("M4-CONFLICTO-CONCURRENCIA"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "SQL-PRIVADO"))));
    }

    @ParameterizedTest
    @CsvSource({
        "M4-SIN-PERMISO,403",
        "M4-SESION-NO-ENCONTRADA,404",
        "M4-SESION-DUPLICADA,409",
        "M4-PROVEEDOR-NO-DISPONIBLE,503",
        "M4-FECHA-INVALIDA,400"
    })
    void erroresDeDominioConservanSuEstado(String codigo, int http) throws Exception {
        when(service.consultarSesionAutorizada(any()))
                .thenThrow(new ReglaNegocioException(codigo, "Regla rechazada"));
        mvc.perform(get(ROOT + "/" + SESION))
                .andExpect(status().is(http))
                .andExpect(jsonPath("$.code").value(codigo));
    }

    @Test
    void falloInesperadoNoRevelaDetalleInterno() throws Exception {
        when(service.consultarSesionAutorizada(any()))
                .thenThrow(new IllegalStateException("SQL-PRIVADO"));
        mvc.perform(get(ROOT + "/" + SESION))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error interno del servidor"))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    private DataIntegrityViolationException integridad(String state, String constraint) {
        return new DataIntegrityViolationException(
                "SQL-PRIVADO",
                new org.hibernate.exception.ConstraintViolationException(
                        "SQL-PRIVADO", new SQLException("SQL-PRIVADO", state), constraint));
    }
}
