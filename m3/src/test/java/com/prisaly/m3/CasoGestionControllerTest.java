package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.prisaly.m3.application.port.*;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Controlled policies and proposed test-only transport; not institutional authorization. */
class CasoGestionControllerTest {
    final UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    final String fingerprint = "m3-v2:" + "a".repeat(64);
    final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    final CaseAuthorizationPort write = mock(CaseAuthorizationPort.class);
    final AutorizacionConsultaCasoPort read = mock(AutorizacionConsultaCasoPort.class);
    MockMvc mvc;

    MockMvc mvc(Optional<AutorizacionConsultaCasoPort> readPolicy, Optional<CaseAuthorizationPort> writePolicy,
            Optional<CasoPatchConcurrencyContract> contract) {
        return MockMvcBuilders.standaloneSetup(new CasoGestionController(identity, repository, readPolicy, writePolicy, contract))
                .setControllerAdvice(new CasoGestionExceptionHandler()).build();
    }
    @BeforeEach void setup() {
        when(identity.currentUserId()).thenReturn(owner);
        when(write.canModifyCase(owner.toString(), id.toString())).thenReturn(true);
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(read.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true))
                .thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(owner, false)));
        mvc = mvc(Optional.of(read), Optional.of(write), Optional.of(h -> h.getFirst("X-Test-Fingerprint")));
    }
    Caso caso(UUID teacher, boolean lock) {
        return new Caso(id.toString(), "Original", teacher.toString(), 300, CasoEstado.BORRADOR, List.of(), null, lock);
    }
    String path() { return "/v1/casos/" + id; }

    @Test void getReturnsExactlyFourSafeFields() throws Exception {
        mvc.perform(get(path())).andExpect(status().isOk())
                .andExpect(jsonPath("$.idCaso").value(id.toString())).andExpect(jsonPath("$.titulo").value("Original"))
                .andExpect(jsonPath("$.tiempoEstimadoSegundos").value(300)).andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(4)));
    }
    @Test void getForeignOwnerIsDenied() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(UUID.randomUUID(), false)));
        mvc.perform(get(path())).andExpect(status().isForbidden());
    }
    @Test void getMissingCase() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.empty());
        mvc.perform(get(path())).andExpect(status().isNotFound());
    }
    @Test void malformedIdsDoNotAccessRepository() throws Exception {
        mvc.perform(get("/v1/casos/invalid")).andExpect(status().isBadRequest());
        mvc.perform(patch("/v1/casos/1-1-1-1-1").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void absentIdentityDeniesBothOperations() throws Exception {
        when(identity.currentUserId()).thenReturn(null);
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(patch(path()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
    @Test void noPolicyDeniesBothBeforeRepository() throws Exception {
        mvc = mvc(Optional.empty(), Optional.empty(), Optional.empty());
        mvc.perform(get(path())).andExpect(status().isForbidden());
        mvc.perform(patch(path()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void deniedReadAndVisibilityCannotLeakContent() throws Exception {
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.DENEGADO);
        mvc.perform(get(path())).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(read.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true))
                .thenReturn(AutorizacionConsultaCasoPort.Decision.DENEGADO);
        mvc.perform(get(path())).andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Original"))));
    }
    @Test void technicalFailureIsSanitized() throws Exception {
        when(repository.findById(id.toString())).thenThrow(new IllegalStateException("secret sql"));
        mvc.perform(get(path())).andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test void technicalIdentityFailureIs500ForBothRoutesWithoutReadingOrWriting() throws Exception {
        when(identity.currentUserId()).thenThrow(new IllegalArgumentException("secret institutional backend"));
        mvc.perform(get(path())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CASE_OPERATION_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        mvc.perform(patch(path()).contentType("application/json").content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CASE_OPERATION_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        verifyNoInteractions(read, write, repository);
    }

    @Test void institutionalIdentityDenialRemains403ForBothRoutesWithoutPersistence() throws Exception {
        when(identity.currentUserId()).thenThrow(new CasoDomainException("FORBIDDEN", "secret inactive account"));
        mvc.perform(get(path())).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("ETag"));
        mvc.perform(patch(path()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(read, write, repository);
    }
    @Test void unavailableReadAuthorizationIs500WithoutReadingOrWriting() throws Exception {
        for (var decision : new AutorizacionConsultaCasoPort.Decision[] {
                AutorizacionConsultaCasoPort.Decision.NO_DISPONIBLE, null}) {
            when(read.verificarAcceso(owner, id)).thenReturn(decision);
            assertSanitizedFailure(get(path()));
        }
        when(read.verificarAcceso(owner, id)).thenThrow(new IllegalArgumentException("secret policy"));
        assertSanitizedFailure(get(path()));
        verifyNoInteractions(repository);
    }

    @Test void unavailableVisibilityCannotReturnCaseOrEtag() throws Exception {
        when(read.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true))
                .thenReturn(AutorizacionConsultaCasoPort.Decision.NO_DISPONIBLE);
        assertSanitizedFailure(get(path()));
        when(read.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true))
                .thenThrow(new IllegalArgumentException("secret visibility"));
        assertSanitizedFailure(get(path()));
        verify(repository, never()).update(any(), anyString());
    }

    @Test void technicalWriteAuthorizationIs500BeforePersistenceAndPreconditions() throws Exception {
        when(write.canModifyCase(owner.toString(), id.toString()))
                .thenThrow(new IllegalArgumentException("secret write policy"));
        assertSanitizedFailure(patch(path()).contentType("application/json").content("{}"));
        verifyNoInteractions(repository);
    }

    @Test void explicitWriteDenialRemains403BeforePersistence() throws Exception {
        when(write.canModifyCase(owner.toString(), id.toString()))
                .thenThrow(new CasoDomainException("FORBIDDEN", "secret denial"));
        mvc.perform(patch(path()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        verifyNoInteractions(repository);
    }

    private void assertSanitizedFailure(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CASE_OPERATION_FAILED"))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Original"))));
    }

    @Test void patchPreservesAllOmittedAndProtectedValuesAndForwardsPrecondition() throws Exception {
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint)
                .content("{\"titulo\":\"Nuevo\",\"historia\":null}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var captor = org.mockito.ArgumentCaptor.forClass(Caso.class);
        verify(repository).update(captor.capture(), eq(fingerprint));
        var expected = caso(owner, false);
        var result = captor.getValue();
        assertEquals("Nuevo", result.titulo());
        assertEquals(expected.docenteId(), result.docenteId());
        assertEquals(expected.id(), result.id());
        assertEquals(expected.escenarios(), result.escenarios());
        assertEquals(expected.estado(), result.estado());
        assertEquals(expected.activo(), result.activo());
        assertEquals(expected.tiempoEstimadoSegundos(), result.tiempoEstimadoSegundos());
        assertNull(result.historia());
    }
    @Test void patchForeignOwnerAndLockNeverWrite() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(UUID.randomUUID(), false)));
        mvc.perform(patch(path()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(owner, true)));
        mvc.perform(patch(path()).contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
        verify(repository, never()).update(any(), anyString());
    }
    @Test void noRatifiedTransportCannotWrite() throws Exception {
        mvc = mvc(Optional.of(read), Optional.of(write), Optional.empty());
        mvc.perform(patch(path()).contentType("application/json").content("{\"titulo\":\"Nuevo\"}"))
                .andExpect(status().isServiceUnavailable());
        verify(repository, never()).update(any(), anyString());
    }
    @Test void missingOrMalformedFingerprintCannotWrite() throws Exception {
        for (String header : List.of("", "m3-v1:" + "a".repeat(64), "invalid")) {
            mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", header)
                    .content("{\"titulo\":\"Nuevo\"}")).andExpect(status().isBadRequest());
        }
        mvc.perform(patch(path()).contentType("application/json").content("{\"titulo\":\"Nuevo\"}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).update(any(), anyString());
    }
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"docenteId\":\"fake\"}", "{\"estado\":\"PUBLICADO\"}",
            "{\"activo\":false}", "{\"escenarios\":[]}", "{\"rubrica\":null}",
            "{\"titulo\":null}", "{\"titulo\":\"a\",\"titulo\":\"b\"}",
            "{\"tiempoEstimadoSegundos\":1.5}", "{\"historia\":{}}", "{\"titulo\":\"\"}",
            "{\"titulo\":\"x\"}{}", "{\"historia\":\"\\u0000\"}"})
    void invalidOrForbiddenFieldsCannotWrite(String body) throws Exception {
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint).content(body))
                .andExpect(status().isBadRequest());
        verify(repository, never()).update(any(), anyString());
    }
    @Test void staleFingerprintIsConflictAndTechnicalWriteFailureIsNotSuccess() throws Exception {
        doThrow(new CasoDomainException("STALE_CASE", "private detail")).when(repository).update(any(), anyString());
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint)
                .content("{\"titulo\":\"Nuevo\"}")).andExpect(status().isPreconditionFailed());
        doThrow(new IllegalStateException("secret sql")).when(repository).update(any(), anyString());
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint)
                .content("{\"titulo\":\"Nuevo\"}")).andExpect(status().isInternalServerError());
    }

    @Test void patchMissingCaseAndDeniedPermissionNeverWrite() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.empty());
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint)
                .content("{\"titulo\":\"Nuevo\"}")).andExpect(status().isNotFound());
        clearInvocations(repository);
        when(write.canModifyCase(owner.toString(), id.toString())).thenReturn(false);
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint)
                .content("{\"titulo\":\"Nuevo\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test void archivedCaseIsControlledConflict() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.of(new Caso(id.toString(), "Archived",
                owner.toString(), 300, CasoEstado.ARCHIVADO, List.of(), null, false)));
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint)
                .content("{\"titulo\":\"Nuevo\"}")).andExpect(status().isConflict());
        verify(repository, never()).update(any(), anyString());
    }

    @Test void springRegistersBothRoutesWithoutOptionalPolicyProvidersAndDeniesAccess() throws Exception {
        try (var context = new org.springframework.web.context.support.AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new org.springframework.mock.web.MockServletContext());
            context.register(WebConfig.class, CasoGestionController.class, CasoGestionExceptionHandler.class);
            context.refresh();
            var realMvc = MockMvcBuilders.webAppContextSetup(context).build();
            realMvc.perform(get(path())).andExpect(status().isForbidden());
            realMvc.perform(patch(path()).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class WebConfig {
        @org.springframework.context.annotation.Bean
        CasoCurrentUserPort identity() { return UUID::randomUUID; }
        @org.springframework.context.annotation.Bean
        CasoRepositoryPort repository() { return mock(CasoRepositoryPort.class); }
    }
}
