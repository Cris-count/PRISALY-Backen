package com.prisaly.m3;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.presentation.*;
import com.prisaly.m3.presentation.dto.CasoContenidoResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CasoContenidoConsultaControllerTest {
    final UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    final String e1 = UUID.randomUUID().toString(), e2 = UUID.randomUUID().toString();
    final String p1 = UUID.randomUUID().toString(), p2 = UUID.randomUUID().toString();
    final String fingerprint = "m3-v2:" + "a".repeat(64), etag = '"' + fingerprint + '"';
    final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    final CasoUpdatePort versioned = mock(CasoUpdatePort.class);
    final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    final AutorizacionConsultaCasoPort read = mock(AutorizacionConsultaCasoPort.class);
    MockMvc mvc;

    @BeforeEach void setup() {
        when(identity.currentUserId()).thenReturn(owner);
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(read.verificarVisibilidad(eq(owner), eq(id), any(), anyBoolean())).thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        stored(owner, CasoEstado.BORRADOR);
        mvc = mvc(Optional.of(new CasoStrongEtagContract()));
    }
    MockMvc mvc(Optional<CasoPatchConcurrencyContract> codec) {
        return MockMvcBuilders.standaloneSetup(new CasoContenidoConsultaController(
                new com.prisaly.m3.application.service.CasoDocenteConsultaService(identity, read, repository, versioned), codec))
                .setControllerAdvice(new CasoGestionExceptionHandler()).build();
    }
    Caso caso(UUID teacher, CasoEstado state) {
        var o2 = new OpcionRespuesta(UUID.randomUUID().toString(), "B", false, "Por B", 2);
        var o1 = new OpcionRespuesta(UUID.randomUUID().toString(), "A", true, "Por A", 1);
        var q2 = new Pregunta(p2, "Q2", List.of(o2, o1), null, "Borrador", true, 2, null, null);
        var q1 = new Pregunta(p1, "Q1", List.of(), "Ayuda", "Borrador", true, 1, null, null);
        var es2 = new Escenario(e2, "Segundo", "C2", null, 2, true, List.of());
        var es1 = new Escenario(e1, "Primero", "C1", "I1", 1, true, List.of(q2, q1));
        return new Caso(id.toString(), "Caso", teacher.toString(), 300, state, List.of(es2, es1), null, false);
    }
    void stored(UUID teacher, CasoEstado state) {
        when(versioned.findById(id)).thenReturn(Optional.of(new CasoUpdateResult(caso(teacher, state), fingerprint)));
    }
    String path(String suffix) { return "/v1/casos/" + id + suffix; }

    @Test void contenidoCompletoOrdenadoConEtagYSinDatosInternos() throws Exception {
        mvc.perform(get(path("/contenido"))).andExpect(status().isOk())
                .andExpect(header().string("ETag", etag)).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.titulo").value("Caso"))
                .andExpect(jsonPath("$.escenarios", hasSize(2)))
                .andExpect(jsonPath("$.escenarios[0].idEscenario").value(e1))
                .andExpect(jsonPath("$.escenarios[0].preguntas[0].idPregunta").value(p1))
                .andExpect(jsonPath("$.escenarios[0].preguntas[1].opciones[0].texto").value("A"))
                .andExpect(jsonPath("$.escenarios[0].preguntas[1].opciones[0].esCorrecta").value(true))
                .andExpect(jsonPath("$.escenarios[0].preguntas[1].opciones[0].explicacion").value("Por A"))
                .andExpect(jsonPath("$.docenteId").doesNotExist()).andExpect(jsonPath("$.rubrica").doesNotExist())
                .andExpect(jsonPath("$.fingerprint").doesNotExist());
    }

    @Test void rutasDeElementosDelCaso() throws Exception {
        mvc.perform(get(path("/escenarios"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(header().string("ETag", etag));
        mvc.perform(get(path("/escenarios/" + e2))).andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Segundo"));
        mvc.perform(get(path("/preguntas/" + p2))).andExpect(status().isOk()).andExpect(jsonPath("$.enunciado").value("Q2"));
        mvc.perform(get(path("/preguntas/" + p2 + "/opciones"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].explicacion").value("Por B"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/escenarios/", "/preguntas/"})
    void elementoFueraDelCasoResponde404SinEtag(String ruta) throws Exception {
        mvc.perform(get(path(ruta + UUID.randomUUID()))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ELEMENT_NOT_IN_CASE")).andExpect(header().doesNotExist("ETag"));
    }

    @Test void opcionesDePreguntaAjenaResponde404() throws Exception {
        mvc.perform(get(path("/preguntas/" + UUID.randomUUID() + "/opciones"))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/escenarios/no-uuid", "/preguntas/no-uuid", "/preguntas/no-uuid/opciones"})
    void identificadorInvalidoResponde400(String ruta) throws Exception {
        mvc.perform(get(path(ruta))).andExpect(status().isBadRequest());
        verifyNoInteractions(versioned);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/contenido", "/escenarios", "/ESC", "/PRE", "/OPC"})
    void sinPermisoNiLeeElAgregadoNiEmiteEtag(String ruta) throws Exception {
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.DENEGADO);
        String destino = switch (ruta) {
            case "/ESC" -> "/escenarios/" + e1;
            case "/PRE" -> "/preguntas/" + p1;
            case "/OPC" -> "/preguntas/" + p1 + "/opciones";
            default -> ruta;
        };
        mvc.perform(get(path(destino))).andExpect(status().isForbidden()).andExpect(header().doesNotExist("ETag"));
        verifyNoInteractions(versioned);
    }

    @Test void propietarioDistintoResponde403ConElementoValido() throws Exception {
        stored(UUID.randomUUID(), CasoEstado.BORRADOR);
        mvc.perform(get(path("/preguntas/" + p2))).andExpect(status().isForbidden()).andExpect(header().doesNotExist("ETag"));
    }

    @Test void sinIdentidadResponde401() throws Exception {
        when(identity.currentUserId()).thenReturn(null);
        mvc.perform(get(path("/contenido"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(versioned);
    }

    @Test void estadoNoVisibleSeDeniega() throws Exception {
        when(read.verificarVisibilidad(eq(owner), eq(id), any(), anyBoolean())).thenReturn(AutorizacionConsultaCasoPort.Decision.DENEGADO);
        mvc.perform(get(path("/contenido"))).andExpect(status().isForbidden());
    }

    @Test void casoInexistenteResponde404() throws Exception {
        when(versioned.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get(path("/contenido"))).andExpect(status().isNotFound());
    }

    @Test void sinCodecFallaCerradoYNoLee() throws Exception {
        mvc(Optional.empty()).perform(get(path("/contenido"))).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(versioned);
    }

    @Test void conEtagVigenteElMarcoResponde304SinCuerpo() throws Exception {
        mvc.perform(get(path("/contenido")).header("If-None-Match", etag)).andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @ParameterizedTest
    @EnumSource(CasoEstado.class)
    void proyeccionConservaElEstado(CasoEstado estado) {
        assertEquals(estado.name(), CasoContenidoResponse.from(caso(owner, estado)).estado());
    }
}
