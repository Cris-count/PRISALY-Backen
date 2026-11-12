package com.prisaly.m3;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(OutputCaptureExtension.class)
class CasoPublicacionControllerTest {
    final UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    final String fingerprint = "m3-v2:" + "a".repeat(64), etag = '"' + fingerprint + '"', next = "m3-v2:" + "b".repeat(64);
    final CasoCurrentUserPort identity = mock(CasoCurrentUserPort.class);
    final CaseAuthorizationPort auth = mock(CaseAuthorizationPort.class);
    // N1/F4-H1: the service calls the lazy overload; its default delegates to the stubbed eager method.
    final CasoPublicationPort port = mock(CasoPublicationPort.class, CALLS_REAL_METHODS);
    MockMvc mvc;

    @BeforeEach void setup() {
        when(identity.currentUserId()).thenReturn(owner);
        when(auth.canPublishCase(owner.toString(), id.toString())).thenReturn(true);
        when(auth.canArchiveCase(owner.toString(), id.toString())).thenReturn(true);
        when(port.publish(owner.toString(), id, fingerprint)).thenReturn(resultado(CasoEstado.PUBLICADO));
        when(port.archive(owner.toString(), id, fingerprint)).thenReturn(resultado(CasoEstado.ARCHIVADO));
        mvc = mvc(Optional.of(port), Optional.of(new CasoStrongEtagContract()));
    }
    MockMvc mvc(Optional<CasoPublicationPort> p, Optional<CasoPatchConcurrencyContract> c) {
        return MockMvcBuilders.standaloneSetup(new CasoPublicacionController(
                new com.prisaly.m3.application.service.CasoTransicionService(identity, auth, p.orElse(null)), c))
                .setControllerAdvice(new CasoGestionExceptionHandler()).build();
    }
    CasoUpdateResult resultado(CasoEstado e) {
        return new CasoUpdateResult(new Caso(id.toString(), "Caso", owner.toString(), 300, e, List.of(), null, false), next);
    }
    ResultActions send(String accion, String ifMatch) throws Exception {
        var r = post("/v1/casos/" + id + "/" + accion);
        if (ifMatch != null) { r.header("If-Match", ifMatch); }
        return mvc.perform(r);
    }

    @Test void publicarDevuelveEstadoYEtagNueva() throws Exception {
        send("publicar", etag).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(header().string("ETag", '"' + next + '"'));
    }

    @Test void archivarDevuelveEstadoYEtagNueva() throws Exception {
        send("archivar", etag).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ARCHIVADO"));
    }

    @Test void losPermisosSonIndependientes() throws Exception {
        when(auth.canArchiveCase(owner.toString(), id.toString())).thenReturn(false);
        send("archivar", etag).andExpect(status().isForbidden());
        verifyNoInteractions(port);
        send("publicar", etag).andExpect(status().isOk());
        when(auth.canPublishCase(owner.toString(), id.toString())).thenReturn(false);
        when(auth.canArchiveCase(owner.toString(), id.toString())).thenReturn(true);
        clearInvocations(port);
        send("publicar", etag).andExpect(status().isForbidden());
        verifyNoInteractions(port);
    }

    @Test void puertoDeAutorizacionPorDefectoNoConcedeArchivar() {
        var porDefecto = new CaseAuthorizationPort() {
            @Override public boolean canCreateCase(String u, String d) { return true; }
            @Override public boolean canModifyCase(String u, String c) { return true; }
            @Override public boolean canPublishCase(String u, String c) { return true; }
        };
        org.junit.jupiter.api.Assertions.assertFalse(porDefecto.canArchiveCase("u", "c"));
    }

    @Test void precondiciones() throws Exception {
        send("publicar", null).andExpect(status().isPreconditionRequired());
        send("archivar", "*").andExpect(status().isBadRequest());
        // The validator is resolved only after permission; no effective transition is ever reached.
        verify(port, never()).publish(any(), any(), anyString());
        verify(port, never()).archive(any(), any(), anyString());
    }

    @Test void sinIdentidad401() throws Exception {
        when(identity.currentUserId()).thenReturn(null);
        send("publicar", etag).andExpect(status().isUnauthorized());
        verifyNoInteractions(port);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALREADY_PUBLISHED", "ARCHIVED_CASE_OPERATION_PENDING", "CASE_INCOMPLETE",
            "INCOMPLETE_RUBRIC", "INCONSISTENT_OPTIONS", "INVALID_QUESTION", "EVALUABLE_CONTENT_LOCKED", "CASE_NOT_PUBLISHED"})
    void transicionNoPermitidaEs409(String codigo) throws Exception {
        when(port.publish(owner.toString(), id, fingerprint)).thenThrow(new CasoDomainException(codigo, "x"));
        send("publicar", etag).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(codigo))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test void etagObsoletaEs412() throws Exception {
        when(port.archive(owner.toString(), id, fingerprint)).thenThrow(new CasoDomainException("STALE_CASE", "x"));
        send("archivar", etag).andExpect(status().isPreconditionFailed());
    }

    @Test void sinPuertoOCodecFallaCerrado() throws Exception {
        mvc = mvc(Optional.of(port), Optional.empty());
        send("publicar", etag).andExpect(status().isServiceUnavailable());
        mvc = mvc(Optional.empty(), Optional.of(new CasoStrongEtagContract()));
        send("publicar", etag).andExpect(status().isInternalServerError());
    }

    @Test void registraQuienEjecutoYElResultado(CapturedOutput salida) throws Exception {
        send("publicar", etag).andExpect(status().isOk());
        when(auth.canArchiveCase(owner.toString(), id.toString())).thenReturn(false);
        send("archivar", etag).andExpect(status().isForbidden());
        String base = " caso=" + id + " usuario=" + owner + " fecha=";
        assertTrue(salida.getOut().contains("AUDITORIA_CASO accion=PUBLICAR" + base), salida.getOut());
        assertTrue(salida.getOut().contains("resultado=EXITOSO"), salida.getOut());
        assertTrue(salida.getOut().contains("AUDITORIA_CASO accion=ARCHIVAR" + base), salida.getOut());
        assertTrue(salida.getOut().contains("resultado=RECHAZADO:FORBIDDEN"), salida.getOut());
    }
}
