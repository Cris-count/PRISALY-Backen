package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.service.CasoDocenteConsultaService;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.*;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Explicit candidate transport plus controlled policies, never a productive authorization grant. */
class CasoEtagControllerTest {
    final UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    final String fingerprint = "m3-v2:" + "a".repeat(64);
    final String etag = '"' + fingerprint + '"';
    final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    final CasoUpdatePort versioned = mock(CasoUpdatePort.class);
    final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    final CaseAuthorizationPort write = mock(CaseAuthorizationPort.class);
    final AutorizacionConsultaCasoPort read = mock(AutorizacionConsultaCasoPort.class);
    final CasoStrongEtagContract codec = new CasoStrongEtagContract();
    MockMvc mvc;

    @BeforeEach void setup() {
        when(identity.currentUserId()).thenReturn(owner);
        when(write.canModifyCase(owner.toString(), id.toString())).thenReturn(true);
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(read.verificarVisibilidad(owner, id, CasoEstado.BORRADOR, true))
                .thenReturn(AutorizacionConsultaCasoPort.Decision.AUTORIZADO);
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(owner, false)));
        when(versioned.findById(id)).thenReturn(Optional.of(new CasoUpdateResult(caso(owner, false), fingerprint)));
        mvc = MockMvcBuilders.standaloneSetup(new CasoGestionController(
                new CasoDocenteConsultaService(identity, read, repository, versioned),
                new com.prisaly.m3.application.service.CasoPersistentUpdateService(identity, write, repository),
                Optional.of(codec)))
                .setControllerAdvice(new CasoGestionExceptionHandler()).build();
    }
    Caso caso(UUID teacher, boolean locked) {
        return new Caso(id.toString(), "Snapshot title", teacher.toString(), 300, CasoEstado.BORRADOR,
                List.of(), null, locked);
    }
    String path() { return "/v1/casos/" + id; }

    @Test void getProjectsAndTagsExactlyOneSnapshotWithoutRepositoryReread() throws Exception {
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.titulo").value("Snapshot title"))
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.fingerprint").doesNotExist()).andExpect(jsonPath("$.rubrica").doesNotExist());
        verify(versioned).findById(id);
        verifyNoMoreInteractions(versioned);
        verifyNoInteractions(repository);
    }

    @Test void etagFromGetIsAcceptedUnchangedByPatch() throws Exception {
        String actual = mvc.perform(get(path())).andReturn().getResponse().getHeader("ETag");
        mvc.perform(patch(path()).contentType("application/json").header("If-Match", actual)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isNoContent());
        verify(repository).update(argThat(c -> c.titulo().equals("Changed")), eq(fingerprint));
    }

    @Test void missingIfMatchAndTestOnlyHeaderAreRejected() throws Exception {
        mvc.perform(patch(path()).contentType("application/json").header("X-Test-Fingerprint", fingerprint)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
        verify(repository, never()).update(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "W/\"m3-v2:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
            "m3-v2:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "\"bad\"",
            "\"m3-v1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
            "\"m3-v2:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"",
            "\"m3-v2:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"other\""})
    void malformedOrUnsupportedIfMatchCannotWrite(String value) throws Exception {
        mvc.perform(patch(path()).contentType("application/json").header("If-Match", value)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isBadRequest());
        verify(repository, never()).update(any(), anyString());
    }

    @Test void multipleHeaderLinesCannotWrite() throws Exception {
        mvc.perform(patch(path()).contentType("application/json").header("If-Match", etag, etag)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isBadRequest());
        verify(repository, never()).update(any(), anyString());
    }

    @Test void onlyHttpOptionalWhitespaceIsAcceptedWithoutWeakeningValidator() {
        var headers = new HttpHeaders();
        headers.setIfMatch(" \t" + etag + "\t ");
        assertEquals(fingerprint, codec.expectedFingerprint(headers));
        headers.setIfMatch("\n" + etag);
        assertThrows(IllegalArgumentException.class, () -> codec.expectedFingerprint(headers));
    }

    @Test void staleIfMatchPreservesConflictContractAndNeverReportsSuccess() throws Exception {
        doThrow(new CasoDomainException("STALE_CASE", "private snapshot")).when(repository).update(any(), anyString());
        mvc.perform(patch(path()).contentType("application/json").header("If-Match", etag)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("STALE_CASE"))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test void publishedCaseIsConflictEvenWithCurrentIfMatchAndNeverWrites() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.of(new Caso(id.toString(), "Published",
                owner.toString(), 300, CasoEstado.PUBLICADO, List.of(), null, false)));
        mvc.perform(patch(path()).contentType("application/json").header("If-Match", etag)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_NOT_EDITABLE"));
        verify(repository, never()).update(any(), anyString());
    }

    @Test void publishedCaseWithoutIfMatchReportsStateBeforePrecondition() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.of(new Caso(id.toString(), "Published",
                owner.toString(), 300, CasoEstado.PUBLICADO, List.of(), null, false)));
        mvc.perform(patch(path()).contentType("application/json")
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isConflict());
        verify(repository, never()).update(any(), anyString());
    }

    /** Documents the framework behaviour; D8 did not select a 304 contract. */
    @Test void ifNoneMatchFollowsSpringConditionalGet() throws Exception {
        mvc.perform(get(path()).header("If-None-Match", etag)).andExpect(status().isNotModified())
                .andExpect(content().string(""));
        mvc.perform(get(path()).header("If-None-Match", "\"m3-v2:" + "b".repeat(64) + "\""))
                .andExpect(status().isOk()).andExpect(header().string("ETag", etag));
    }

    @Test void foreignOwnerNeverReceivesEtagOrWrites() throws Exception {
        when(versioned.findById(id)).thenReturn(Optional.of(new CasoUpdateResult(caso(UUID.randomUUID(), false), fingerprint)));
        mvc.perform(get(path())).andExpect(status().isForbidden()).andExpect(header().doesNotExist("ETag"));
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(UUID.randomUUID(), false)));
        mvc.perform(patch(path()).contentType("application/json").header("If-Match", etag)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isForbidden());
        verify(repository, never()).update(any(), anyString());
    }

    @Test void deniedAndMissingIdentityNeverReadSnapshotOrExposeEtag() throws Exception {
        when(read.verificarAcceso(owner, id)).thenReturn(AutorizacionConsultaCasoPort.Decision.DENEGADO);
        mvc.perform(get(path())).andExpect(status().isForbidden()).andExpect(header().doesNotExist("ETag"));
        when(identity.currentUserId()).thenReturn(null);
        mvc.perform(get(path())).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("ETag"));
        verifyNoInteractions(versioned, repository);
    }

    @Test void missingInvalidOrMismatchedSnapshotCannotLeakEtag() throws Exception {
        when(versioned.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get(path())).andExpect(status().isNotFound()).andExpect(header().doesNotExist("ETag"));
        when(versioned.findById(id)).thenReturn(Optional.of(new CasoUpdateResult(caso(owner, false), "invalid")));
        mvc.perform(get(path())).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("ETag"));
        var foreign = new Caso(UUID.randomUUID().toString(), "Foreign", owner.toString(), 300,
                CasoEstado.BORRADOR, List.of(), null, false);
        when(versioned.findById(id)).thenReturn(Optional.of(new CasoUpdateResult(foreign, fingerprint)));
        mvc.perform(get(path())).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("ETag"));
    }

    @Test void missingSnapshotProviderFailsWithoutIndependentRepositoryFallback() {
        var service = new CasoDocenteConsultaService(identity, read, repository);
        assertThrows(IllegalStateException.class, () -> service.consultarConVersion(id.toString()));
        verifyNoInteractions(repository);
    }

    @Test void lockedContentCannotBeEditedEvenWithCurrentIfMatch() throws Exception {
        when(repository.findById(id.toString())).thenReturn(Optional.of(caso(owner, true)));
        mvc.perform(patch(path()).contentType("application/json").header("If-Match", etag)
                .content("{\"titulo\":\"Changed\"}")).andExpect(status().isConflict());
        verify(repository, never()).update(any(), anyString());
    }
}
