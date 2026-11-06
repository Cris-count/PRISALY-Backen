package com.prisaly.m5.presentation.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.ResumenIntento;
import com.prisaly.m5.application.ResumenIntento.TiemposPersistidos;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.domain.intento.EstadoIntento;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntentosM5DtoTest {
    private static final UUID ID = UUID.randomUUID();
    private static final Instant INICIO = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant LIMITE = INICIO.plusSeconds(1800);

    private ResumenIntento resumen() {
        return new ResumenIntento(new ReferenciaIntento(ID.toString()), EstadoIntento.EN_CURSO,
                Optional.empty(), List.of(), Optional.of(new TiemposPersistidos(INICIO, LIMITE, null)));
    }

    @Test void horaHttpSeCalculaSinCambiarElResumen() {
        var primero = resumen();
        var antes = Instant.now();
        var dto = IntentosM5Dto.Intento.de(primero);
        var despues = Instant.now();
        assertFalse(dto.ahoraServidor().isBefore(antes));
        assertFalse(dto.ahoraServidor().isAfter(despues));
        assertEquals(INICIO, dto.inicio());
        assertEquals(LIMITE, dto.limiteTemporal());
        assertEquals(primero, resumen());
        assertEquals(primero.hashCode(), resumen().hashCode());
    }

    @Test void jsonConservaCamposYTiposDelContrato() throws Exception {
        var acceso = mock(AccesoProtegidoParticipacion.class);
        when(acceso.consultarIntento(new ReferenciaIntento(ID.toString()))).thenReturn(resumen());
        var mvc = MockMvcBuilders.standaloneSetup(new IntentosEstudianteM5Controlador(acceso)).build();
        mvc.perform(get("/v1/estudiantes/intentos/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$.idIntento").value(ID.toString()))
                .andExpect(jsonPath("$.estado").value("EnCurso"))
                .andExpect(jsonPath("$.inicio").value(INICIO.toString()))
                .andExpect(jsonPath("$.limiteTemporal").value(LIMITE.toString()))
                .andExpect(jsonPath("$.motivoFinalizacion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.respuestas").isArray())
                .andExpect(jsonPath("$.ahoraServidor").isString())
                .andExpect(jsonPath("$.ahoraServidor").value(
                        org.hamcrest.Matchers.matchesPattern(".*T.*Z")));
    }
}
