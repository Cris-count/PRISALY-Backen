package com.prisaly.m3.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m3.application.port.*;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.domain.model.Caso;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CasoCreationIdentityTest {
    private final UUID owner = UUID.randomUUID(), caseId = UUID.randomUUID();
    private final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    private final CaseAuthorizationPort authorization = mock(CaseAuthorizationPort.class);
    private final CasoRepositoryPort repository = mock(CasoRepositoryPort.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CasoCreationController(
            new CasoApplicationService(authorization, repository), identity))
            .setControllerAdvice(new CasoCreationExceptionHandler()).build();

    private String body() {
        return "{\"idCaso\":\"" + caseId + "\",\"titulo\":\"Draft\",\"tiempoEstimadoSegundos\":300}";
    }

    @Test
    void missingIdentityIs401BeforeParsingOrCallingAuthorizationAndPersistence() throws Exception {
        for (String request : new String[] {body(), "{"}) {
            mvc.perform(post("/api/v1/casos").contextPath("/api").contentType("application/json").content(request))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        verifyNoInteractions(authorization, repository);
    }

    @Test
    void technicalIdentityFailureRemainsSanitized500WithoutGrantingAccess() throws Exception {
        when(identity.currentUserId()).thenThrow(new IllegalStateException("secret identity backend"));
        mvc.perform(post("/v1/casos").contentType("application/json").content(body()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CASE_CREATION_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        verifyNoInteractions(authorization, repository);
    }

    @Test
    void validIdentityWithDeniedAuthorizationRemains403WithoutPersistence() throws Exception {
        when(identity.currentUserId()).thenReturn(owner);
        when(authorization.canCreateCase(owner.toString(), owner.toString())).thenReturn(false);
        mvc.perform(post("/v1/casos").contentType("application/json").content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(authorization).canCreateCase(owner.toString(), owner.toString());
        verifyNoInteractions(repository);
    }

    @Test
    void validIdentityStillUsesExistingAuthorizationAndOwnerWithoutChangingPostResponse() throws Exception {
        // Controlled policy; this is not an institutional permission provider or a real SQL commit test.
        when(identity.currentUserId()).thenReturn(owner);
        when(authorization.canCreateCase(owner.toString(), owner.toString())).thenReturn(true);
        mvc.perform(post("/v1/casos").contentType("application/json").content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.idCaso").value(caseId.toString()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
        var saved = org.mockito.ArgumentCaptor.forClass(Caso.class);
        verify(repository).insert(saved.capture());
        assertEquals(owner.toString(), saved.getValue().docenteId());
        assertEquals(caseId.toString(), saved.getValue().id());
    }
}
