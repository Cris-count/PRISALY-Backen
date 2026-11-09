package com.prisaly.m3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CasoContenidoControllerTest {
    final UUID id = UUID.randomUUID(), owner = UUID.randomUUID(), parent = UUID.randomUUID(), element = UUID.randomUUID();
    final String fingerprint = "m3-v2:" + "a".repeat(64), next = "m3-v2:" + "b".repeat(64);
    final String etag = '"' + fingerprint + '"';
    final CasoUpdatePort updates = mock(CasoUpdatePort.class);
    final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    final CaseAuthorizationPort write = mock(CaseAuthorizationPort.class);
    MockMvc mvc;

    @BeforeEach void setup() {
        when(identity.currentUserId()).thenReturn(owner);
        when(write.canModifyCase(owner.toString(), id.toString())).thenReturn(true);
        stored(owner, CasoEstado.BORRADOR, false);
        when(updates.update(eq(id), eq(fingerprint), any())).thenReturn(new CasoUpdateResult(caso(owner, CasoEstado.BORRADOR, false), next));
        mvc = MockMvcBuilders.standaloneSetup(new CasoContenidoController(new com.prisaly.m3.application.service.CasoContenidoService(identity, write, updates),
                Optional.of(new CasoStrongEtagContract())))
                .setControllerAdvice(new CasoGestionExceptionHandler()).build();
    }
    Caso caso(UUID teacher, CasoEstado state, boolean locked) {
        return new Caso(id.toString(), "Case", teacher.toString(), 300, state, List.of(), null, locked);
    }
    void stored(UUID teacher, CasoEstado state, boolean locked) {
        when(updates.findById(id)).thenReturn(Optional.of(new CasoUpdateResult(caso(teacher, state, locked), fingerprint)));
    }
    String escenario() { return "{\"idEscenario\":\"" + element + "\",\"titulo\":\"E\",\"contexto\":\"C\",\"orden\":1}"; }
    String opcion(String extra) {
        return "{\"idOpcion\":\"" + element + "\",\"texto\":\"T\",\"esCorrecta\":true,\"orden\":1" + extra + "}";
    }
    org.springframework.test.web.servlet.ResultActions send(String path, String body, String ifMatch) throws Exception {
        var request = post("/v1/casos/" + id + path).contentType("application/json").content(body);
        if (ifMatch != null) { request.header("If-Match", ifMatch); }
        return mvc.perform(request);
    }
    void neverWrites() { verify(updates, never()).update(any(), anyString(), any()); }

    @Test void createsScenarioAndReturnsNextValidator() throws Exception {
        send("/escenarios", escenario(), etag).andExpect(status().isCreated())
                .andExpect(header().string("ETag", '"' + next + '"'))
                .andExpect(jsonPath("$.id").value(element.toString()));
        verify(updates).update(eq(id), eq(fingerprint), any());
    }

    @Test void createsQuestionUnderTheGivenScenario() throws Exception {
        send("/escenarios/" + parent + "/preguntas", "{\"idPregunta\":\"" + element + "\",\"enunciado\":\"Q\",\"orden\":1}", etag)
                .andExpect(status().isCreated());
    }

    @Test void createsOptionWithExplanation() throws Exception {
        send("/preguntas/" + parent + "/opciones", opcion(",\"explicacion\":\"Why\""), etag).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"explicacion\":null", ",\"explicacion\":\"  \""})
    void optionWithoutExplanationIsRejected(String extra) throws Exception {
        send("/preguntas/" + parent + "/opciones", opcion(extra), etag).andExpect(status().isBadRequest());
        neverWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "{\"idEscenario\":\"bad\",\"orden\":1}", "{\"orden\":1}",
            "{\"idEscenario\":\"11111111-1111-1111-1111-111111111111\",\"orden\":0}",
            "{\"idEscenario\":\"11111111-1111-1111-1111-111111111111\",\"orden\":\"1\"}",
            "{\"idEscenario\":\"11111111-1111-1111-1111-111111111111\",\"orden\":1,\"activo\":false}",
            "{\"idEscenario\":\"11111111-1111-1111-1111-111111111111\",\"orden\":1,\"orden\":2}"})
    void invalidBodiesAreRejected(String body) throws Exception {
        send("/escenarios", body, etag).andExpect(status().isBadRequest());
        neverWrites();
    }

    @Test void missingIfMatchIs428AndMalformedIs400() throws Exception {
        send("/escenarios", escenario(), null).andExpect(status().isPreconditionRequired());
        send("/escenarios", escenario(), "*").andExpect(status().isBadRequest());
        neverWrites();
    }

    @Test void staleValidatorIs412() throws Exception {
        when(updates.update(eq(id), eq(fingerprint), any())).thenThrow(new CasoDomainException("STALE_CASE", "x"));
        send("/escenarios", escenario(), etag).andExpect(status().isPreconditionFailed());
    }

    @Test void deniedPermissionForeignOwnerAndMissingCaseNeverWrite() throws Exception {
        stored(UUID.randomUUID(), CasoEstado.BORRADOR, false);
        send("/escenarios", escenario(), etag).andExpect(status().isForbidden());
        when(updates.findById(id)).thenReturn(Optional.empty());
        send("/escenarios", escenario(), etag).andExpect(status().isNotFound());
        when(write.canModifyCase(owner.toString(), id.toString())).thenReturn(false);
        send("/escenarios", escenario(), etag).andExpect(status().isForbidden());
        neverWrites();
    }

    @Test void unauthenticatedIs401() throws Exception {
        when(identity.currentUserId()).thenReturn(null);
        send("/escenarios", escenario(), etag).andExpect(status().isUnauthorized());
        neverWrites();
    }

    @Test void onlyDraftsAcceptContent() throws Exception {
        stored(owner, CasoEstado.PUBLICADO, false);
        send("/escenarios", escenario(), etag).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_NOT_EDITABLE"));
        stored(owner, CasoEstado.BORRADOR, true);
        send("/escenarios", escenario(), etag).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUABLE_CONTENT_LOCKED"));
        neverWrites();
    }

    @Test void elementFromAnotherCaseIs404AndDuplicateIs409() throws Exception {
        when(updates.update(eq(id), eq(fingerprint), any())).thenThrow(new CasoDomainException("ELEMENT_NOT_IN_CASE", "x"));
        send("/preguntas/" + parent + "/opciones", opcion(",\"explicacion\":\"W\""), etag).andExpect(status().isNotFound());
        doThrow(new CasoDomainException("SCENARIO_ORDER_IN_USE", "x")).when(updates).update(eq(id), eq(fingerprint), any());
        send("/escenarios", escenario(), etag).andExpect(status().isConflict());
    }

    @Test void withoutCodecFailsClosed() throws Exception {
        mvc = MockMvcBuilders.standaloneSetup(new CasoContenidoController(new com.prisaly.m3.application.service.CasoContenidoService(identity, write, updates),
                Optional.empty())).setControllerAdvice(new CasoGestionExceptionHandler()).build();
        send("/escenarios", escenario(), etag).andExpect(status().isServiceUnavailable());
        neverWrites();
    }
}
