package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.presentation.CasoCreationController;
import com.prisaly.m3.presentation.CasoCreationExceptionHandler;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP/unit wiring tests. These do not claim a real commit or real M1/PostgreSQL integration. */
class M3CasoCreationTest {
    private final UUID currentId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private IdentityAccessPort identity;
    private CasoRepositoryPort repository;
    private PlatformTransactionManager manager;
    private MockMvc mvc;
    private AtomicBoolean committed;

    @BeforeEach
    void prepare() {
        identity = mock(IdentityAccessPort.class);
        when(identity.currentUser()).thenReturn(new AuthenticatedUser(currentId, "docente@prisaly.test",
                "Docente fixture", "Docente", "Activo", Set.of("CREAR_CASO")));
        when(identity.hasPermission(currentId, "CREAR_CASO")).thenReturn(true);
        repository = mock(CasoRepositoryPort.class);
        manager = mock(PlatformTransactionManager.class);
        committed = new AtomicBoolean();
        when(manager.getTransaction(any())).thenAnswer(call -> {
            TransactionDefinition definition = call.getArgument(0);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior());
            return new SimpleTransactionStatus();
        });
        doAnswer(call -> { committed.set(true); return null; }).when(manager).commit(any());
        var authorization = new M3CaseAuthorizationAdapter(identity);
        var transactions = new TransactionInterceptor();
        transactions.setTransactionManager(manager);
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new CasoApplicationService(authorization, repository));
        proxy.addAdvice(transactions);
        var controller = new CasoCreationController((CasoApplicationService) proxy.getProxy(), authorization);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CasoCreationExceptionHandler()).build();
    }

    @Test
    void createsOnlyForAuthenticatedOwnerAndReturns201AfterCommitCallback() throws Exception {
        doAnswer(call -> {
            Caso draft = call.getArgument(0);
            assertFalse(committed.get());
            assertEquals(currentId.toString(), draft.docenteId());
            assertEquals(caseId.toString(), draft.id());
            assertEquals(CasoEstado.BORRADOR, draft.estado());
            assertTrue(draft.escenarios().isEmpty());
            assertNull(draft.rubrica());
            assertNull(draft.fechaCreacion());
            return null;
        }).when(repository).insert(any());
        mvc.perform(post("/api/v1/casos").contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCaso").value(caseId.toString()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.docenteId").doesNotExist());
        assertTrue(committed.get());
        verify(repository).insert(any());
        verify(manager).commit(any());
        verify(manager, never()).rollback(any());
    }

    @Test
    void unauthenticatedIs401AndDeniedPermissionIs403WithoutInsert() throws Exception {
        when(identity.currentUser()).thenThrow(new IdentityDomainException("UNAUTHENTICATED", "fixture"));
        mvc.perform(post("/v1/casos").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, manager);
        doReturn(new AuthenticatedUser(currentId, "docente@prisaly.test",
                "Docente fixture", "Docente", "Activo", Set.of())).when(identity).currentUser();
        when(identity.hasPermission(currentId, "CREAR_CASO")).thenReturn(false);
        mvc.perform(post("/v1/casos").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
        verify(manager).rollback(any());
        assertFalse(committed.get());
    }

    @Test
    void invalidJsonAndUnacceptedOwnerAre400BeforeOpeningTransaction() throws Exception {
        for (String invalid : new String[] {"{", "{}", body().replace("300", "1.5"),
                body().replace("}", ",\"docenteId\":\"" + currentId + "\"}")}) {
            mvc.perform(post("/v1/casos").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository, manager);
    }

    @Test
    void duplicateIs409WhileForeignKeyAndTechnicalFailuresStay500WithoutSqlDetails() throws Exception {
        for (String sqlState : new String[] {"23505", "23503", "08006"}) {
            doThrow(new IllegalStateException(new SQLException("secret SQL fixture", sqlState)))
                    .when(repository).insert(any());
            mvc.perform(post("/v1/casos").contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().is(sqlState.equals("23505") ? 409 : 500))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        }
        verify(manager, times(3)).rollback(any());
        assertFalse(committed.get());
    }

    @Test
    void commitFailureCannotReturn201() throws Exception {
        doThrow(new TransactionSystemException("secret commit failure")).when(manager).commit(any());
        mvc.perform(post("/v1/casos").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CASE_CREATION_FAILED"));
        assertFalse(committed.get());
    }

    @Test
    void explicitConfigurationWiresRealBridgeAndDoesNotRegisterPublication() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IdentityAccessPort.class, () -> identity);
            context.registerBean(CasoRepositoryPort.class, () -> repository);
            // Discover typed authorization factories instead of selecting only the known good config.
            // M3CasoCreationIT complements this focused context with the full application scan.
            var scanner = new org.springframework.context.annotation
                    .ClassPathScanningCandidateComponentProvider(true);
            for (var candidate : scanner.findCandidateComponents("com.prisaly.config")) {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                boolean suppliesAuthorization = CaseAuthorizationPort.class.isAssignableFrom(type)
                        || java.util.Arrays.stream(type.getDeclaredMethods()).anyMatch(method ->
                            method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)
                            && CaseAuthorizationPort.class.isAssignableFrom(method.getReturnType()));
                if (suppliesAuthorization) {
                    context.register(type);
                }
            }
            context.refresh();
            assertEquals(1, context.getBeansOfType(CaseAuthorizationPort.class).size());
            assertEquals(1, context.getBeansOfType(CasoCurrentUserPort.class).size());
            assertNotNull(context.getBean(CasoApplicationService.class));
            var authorization = context.getBean(CaseAuthorizationPort.class);
            assertSame(context.getBean(M3CaseAuthorizationAdapter.class), authorization);
            assertSame(authorization, context.getBean(CasoCurrentUserPort.class));
            assertFalse(context.containsBean("caseAuthorizationFailClosed"));
            assertFalse(authorization.canModifyCase(currentId.toString(), caseId.toString()));
            assertFalse(authorization.canPublishCase(currentId.toString(), caseId.toString()));
            assertTrue(context.getBeansOfType(CasoPublicationPort.class).isEmpty());
        }
    }

    private String body() {
        return "{\"idCaso\":\"" + caseId + "\",\"titulo\":\"Borrador de prueba\",\"tiempoEstimadoSegundos\":300}";
    }
}
