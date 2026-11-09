package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.application.update.RubricaDefinicionCodec;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.*;
import com.prisaly.m3.presentation.dto.CasoRubricaDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RubricaGestionTest {
    final UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    final String fingerprint = "m3-v2:" + "a".repeat(64), etag = '"' + fingerprint + '"';
    final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    final CasoUpdatePort updates = mock(CasoUpdatePort.class);
    final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    final AutorizacionConsultaCasoPort read = mock(AutorizacionConsultaCasoPort.class);
    final CaseAuthorizationPort write = mock(CaseAuthorizationPort.class);
    final String valido = "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c1\",\"descripcion\":\"D\",\"puntaje\":3,\"ponderacion\":0.40}]}";
    MockMvc mvc;

    @BeforeEach void setup() {
        when(identity.currentUserId()).thenReturn(owner);
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(read.verificarVisibilidad(eq(owner), eq(id), any(), anyBoolean())).thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(write.canModifyCase(owner.toString(), id.toString())).thenReturn(true);
        stored(owner, CasoEstado.BORRADOR, rubrica());
        when(updates.update(eq(id), eq(fingerprint), any())).thenReturn(new CasoUpdateResult(caso(owner, CasoEstado.BORRADOR, rubrica()), fingerprint));
        mvc = MockMvcBuilders.standaloneSetup(new CasoRubricaController(
                new com.prisaly.m3.application.service.CasoDocenteConsultaService(identity, read, repository, updates),
                new com.prisaly.m3.application.service.CasoContenidoService(identity, write, updates),
                Optional.of(new CasoStrongEtagContract()))).setControllerAdvice(new CasoGestionExceptionHandler()).build();
    }
    Rubrica rubrica() {
        var c = List.of(new CriterioRubrica("c1", "D", 3, new BigDecimal("0.40")));
        return new Rubrica(UUID.randomUUID().toString(), c, id.toString(), "R", null, null, RubricaDefinicionCodec.escribir(c), null, Set.of());
    }
    Caso caso(UUID teacher, CasoEstado e, Rubrica r) { return new Caso(id.toString(), "Caso", teacher.toString(), 300, e, List.of(), r, false); }
    void stored(UUID teacher, CasoEstado e, Rubrica r) {
        when(updates.findById(id)).thenReturn(Optional.of(new CasoUpdateResult(caso(teacher, e, r), fingerprint)));
    }
    ResultActions put_(String body, String ifMatch) throws Exception {
        var request = put("/v1/casos/" + id + "/rubrica").contentType("application/json").content(body);
        if (ifMatch != null) { request.header("If-Match", ifMatch); }
        return mvc.perform(request);
    }
    void sinEscritura() { verify(updates, never()).update(any(), anyString(), any()); }

    @Test void codecConservaCriteriosYPonderacion() {
        var criterios = List.of(new CriterioRubrica("a\"b", "Desc ñ", 5, new BigDecimal("12.50")), new CriterioRubrica("c", "D", 1));
        assertEquals(criterios, RubricaDefinicionCodec.leer(RubricaDefinicionCodec.escribir(criterios)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[]", "1", "{\"a\":{\"b\":1}}", "{\"version\":2,\"criterios\":[]}",
            "{\"version\":1,\"criterios\":[{\"id\":\"\",\"descripcion\":\"D\",\"puntaje\":1}]}",
            "{\"version\":1,\"criterios\":[{\"id\":\"x\",\"descripcion\":\"D\",\"puntaje\":0}]}", "no-json"})
    void documentoNoReconocidoNoProduceCriterios(String json) {
        assertEquals(List.of(), RubricaDefinicionCodec.leer(json));
    }

    @Test void rubricaSinCriteriosNoEstaCompleta() {
        assertFalse(new Rubrica(UUID.randomUUID().toString(), List.of(), id.toString(), "R", null, null, null, null, Set.of()).isComplete());
        assertTrue(rubrica().isComplete());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "{}", "{\"nombre\":\" \"}", "{\"nombre\":\"R\",\"x\":1}", "{\"nombre\":\"R\",\"nombre\":\"S\"}",
            "{\"nombre\":\"R\",\"criterios\":{}}", "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":0}]}",
            "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":1.5}]}",
            "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":1,\"ponderacion\":-1}]}",
            "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":1,\"ponderacion\":0.001}]}",
            "{\"nombre\":\"R\",\"criterios\":[{\"id\":\"c\",\"descripcion\":\"D\",\"puntaje\":1},{\"id\":\"c\",\"descripcion\":\"E\",\"puntaje\":2}]}",
            "{\"nombre\":\"R\",\"puntajeTotal\":0}", "{\"nombre\":\"R\",\"preguntas\":[{\"idPregunta\":\"x\",\"valorPregunta\":1}]}",
            "{\"nombre\":\"R\",\"preguntas\":[{\"idPregunta\":\"11111111-1111-1111-1111-111111111111\",\"valorPregunta\":0}]}",
            "{\"nombre\":\"R\",\"preguntas\":[{\"idPregunta\":\"11111111-1111-1111-1111-111111111111\",\"valorPregunta\":1},{\"idPregunta\":\"11111111-1111-1111-1111-111111111111\",\"valorPregunta\":2}]}"})
    void cuerposInvalidosNoEscriben(String body) throws Exception {
        put_(body, etag).andExpect(status().isBadRequest());
        sinEscritura();
    }

    @Test void sinCriteriosEsValidoComoBorrador() {
        assertEquals(0, CasoRubricaDto.comando("{\"nombre\":\"R\"}").criterios() .size());
    }

    @Test void guardarDevuelveRubricaYEtag() throws Exception {
        put_(valido, etag).andExpect(status().isOk()).andExpect(header().string("ETag", etag))
                .andExpect(jsonPath("$.completa").value(true)).andExpect(jsonPath("$.criterios[0].ponderacion").value(0.40));
    }

    @Test void consultarDevuelveRubricaSinDatosInternos() throws Exception {
        mvc.perform(get("/v1/casos/" + id + "/rubrica")).andExpect(status().isOk()).andExpect(header().string("ETag", etag))
                .andExpect(jsonPath("$.criterios[0].id").value("c1")).andExpect(jsonPath("$.docenteId").doesNotExist())
                .andExpect(jsonPath("$.definicionEvaluacion").doesNotExist());
    }

    @Test void casoSinRubricaResponde404() throws Exception {
        stored(owner, CasoEstado.BORRADOR, null);
        mvc.perform(get("/v1/casos/" + id + "/rubrica")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUBRIC_NOT_FOUND"));
    }

    @Test void precondicionesDeEscritura() throws Exception {
        put_(valido, null).andExpect(status().isPreconditionRequired());
        put_(valido, "*").andExpect(status().isBadRequest());
        sinEscritura();
        when(updates.update(eq(id), eq(fingerprint), any())).thenThrow(new CasoDomainException("STALE_CASE", "x"));
        put_(valido, etag).andExpect(status().isPreconditionFailed());
    }

    @Test void soloBorradorYPropietario() throws Exception {
        stored(owner, CasoEstado.PUBLICADO, rubrica());
        put_(valido, etag).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CASE_NOT_EDITABLE"));
        stored(UUID.randomUUID(), CasoEstado.BORRADOR, rubrica());
        put_(valido, etag).andExpect(status().isForbidden());
        when(write.canModifyCase(owner.toString(), id.toString())).thenReturn(false);
        put_(valido, etag).andExpect(status().isForbidden());
        sinEscritura();
    }

    @Test void lecturaSinPermisoNoExponeNada() throws Exception {
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.DENEGADO);
        mvc.perform(get("/v1/casos/" + id + "/rubrica")).andExpect(status().isForbidden()).andExpect(header().doesNotExist("ETag"));
    }

    @Test void preguntaAjenaResponde404() throws Exception {
        when(updates.update(eq(id), eq(fingerprint), any())).thenThrow(new CasoDomainException("ELEMENT_NOT_IN_CASE", "x"));
        put_(valido, etag).andExpect(status().isNotFound());
    }
}
