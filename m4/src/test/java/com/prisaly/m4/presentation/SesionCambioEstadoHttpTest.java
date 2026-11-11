package com.prisaly.m4.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.EstadoSesion;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;
import com.prisaly.m4.presentation.controller.SesionController;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * A-05 (DC-AUD-01): MVC contract of {@code PATCH /m4/sesiones/{id}/estado}, which had no JUnit coverage.
 * The service is a mock: this suite checks the HTTP mapping (body, enum values of V002, error codes), not the
 * state machine itself (covered by {@code SesionAcademicaDomainTest} and {@code SesionAcademicaServiceTest}).
 */
class SesionCambioEstadoHttpTest {
    private static final UUID DOCENTE = UUID.fromString("92d59425-fbc4-4e0a-9f5f-fc46159edba1");
    private static final UUID SESION = UUID.fromString("99f4b085-2b4e-43e5-8da0-d9bc5fac90f8");
    private static final String RUTA = "/m4/sesiones/" + SESION + "/estado";

    private final SesionAcademicaService service = mock(SesionAcademicaService.class);
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new SesionController(service))
                .setControllerAdvice(new M4ExceptionHandler()).build();
        when(service.resolverDocenteAutenticado(any())).thenReturn(DOCENTE);
    }

    private static SesionAcademica sesionEn(EstadoSesion destino) {
        SesionAcademica s = SesionAcademica.crear(UUID.randomUUID(), DOCENTE, "Sesión", null,
                Instant.parse("2030-11-05T09:00:00Z"), Instant.parse("2030-11-05T11:00:00Z"), 1800);
        if (destino != EstadoSesion.SIN_INICIAR) {
            s.iniciar();
        }
        if (destino == EstadoSesion.FINALIZADO) {
            s.cerrar();
        }
        return s;
    }

    private static String cuerpo(String estado) {
        return "{\"nuevoEstado\":\"" + estado + "\"}";
    }

    @ParameterizedTest
    @CsvSource({"EnCurso,EN_CURSO", "Finalizado,FINALIZADO"})
    void transicionPermitidaResponde200ConElEstadoNuevo(String json, EstadoSesion estado) throws Exception {
        when(service.cambiarEstado(DOCENTE, SESION, estado)).thenReturn(sesionEn(estado));
        mvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo(json)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(json));
        verify(service).cambiarEstado(DOCENTE, SESION, estado);
    }

    @Test
    void transicionDirectaNoPermitidaResponde409() throws Exception {
        when(service.cambiarEstado(DOCENTE, SESION, EstadoSesion.SIN_INICIAR))
                .thenThrow(new ReglaNegocioException("M4-ESTADO-INVALIDO", "No se puede transicionar"));
        mvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo("SinIniciar")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("M4-ESTADO-INVALIDO"));
    }

    @Test
    void sinNuevoEstadoResponde400SinLlamarAlServicio() throws Exception {
        mvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("M4-VALIDACION"));
        verify(service, org.mockito.Mockito.never()).cambiarEstado(any(), any(), any());
    }

    @Test
    void estadoFueraDelCatalogoDeV002Responde400() throws Exception {
        // The constant name is not accepted: the contract uses the V002 values (EnCurso, Finalizado, SinIniciar).
        for (String invalido : new String[] {"EN_CURSO", "Pausado", ""}) {
            mvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo(invalido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("M4-SOLICITUD-INVALIDA"));
        }
        verify(service, org.mockito.Mockito.never()).cambiarEstado(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
        "M4-SESION-AJENA,403",
        "M4-SESION-NO-ENCONTRADA,404",
        "M4-NO-AUTENTICADO,401",
    })
    void erroresDelServicioSeTraducenAlCodigoHttp(String codigo, int http) throws Exception {
        when(service.cambiarEstado(eq(DOCENTE), eq(SESION), any()))
                .thenThrow(new ReglaNegocioException(codigo, "x"));
        mvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo("EnCurso")))
                .andExpect(status().is(http))
                .andExpect(jsonPath("$.code").value(codigo));
    }

    @Test
    void sesionIdNoUuidResponde400() throws Exception {
        mvc.perform(patch("/m4/sesiones/no-uuid/estado").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("EnCurso")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
