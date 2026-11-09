package com.prisaly.m4.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;
import com.prisaly.m4.presentation.controller.SesionController;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SesionLugarFisicoHttpTest {
    private static final UUID SESION = UUID.fromString("99f4b085-2b4e-43e5-8da0-d9bc5fac90f8");
    private static final String FECHAS = "\"fechaInicio\":\"2030-11-05T09:00:00Z\",\"fechaFin\":\"2030-11-05T11:00:00Z\"";
    private static final String CREAR = "{\"casoId\":\"790a1820-97ba-4190-8e37-110bc110f7fb\",\"titulo\":\"Sesión\"," + FECHAS
            + ",\"tiempoMaximoParticipacionSegundos\":1800";
    private final SesionAcademicaService service = mock(SesionAcademicaService.class);
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new SesionController(service))
                .setControllerAdvice(new M4ExceptionHandler()).build();
    }

    private SesionAcademica sesion(String lugar) {
        var s = SesionAcademica.crear(UUID.randomUUID(), UUID.randomUUID(), "Sesión", null,
                Instant.parse("2030-11-05T09:00:00Z"), Instant.parse("2030-11-05T11:00:00Z"), 1800);
        s.setLugarFisico(lugar);
        return s;
    }

    @Test
    void postRegistraYDevuelveElLugar() throws Exception {
        when(service.crearSesion(any(), any(), any(), any(), any(), any(), anyInt(), eq("Aula 3"))).thenReturn(sesion("Aula 3"));
        mvc.perform(post("/m4/sesiones").contentType(MediaType.APPLICATION_JSON)
                        .content(CREAR + ",\"lugarFisico\":\"Aula 3\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lugarFisico").value("Aula 3"));
    }

    @Test
    void postSinLugarSigueSiendoValido() throws Exception {
        when(service.crearSesion(any(), any(), any(), any(), any(), any(), anyInt(), isNull())).thenReturn(sesion(null));
        mvc.perform(post("/m4/sesiones").contentType(MediaType.APPLICATION_JSON).content(CREAR + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void lugarDeMasDe200CaracteresSeRechaza() throws Exception {
        mvc.perform(post("/m4/sesiones").contentType(MediaType.APPLICATION_JSON)
                        .content(CREAR + ",\"lugarFisico\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void putSinCampoEnviaNuloParaConservarElValor() throws Exception {
        when(service.actualizarSesion(any(), eq(SESION), any(), any(), any(), any(), isNull())).thenReturn(sesion("Aula 3"));
        mvc.perform(put("/m4/sesiones/" + SESION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Cambio\"," + FECHAS + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lugarFisico").value("Aula 3"));
    }
}
