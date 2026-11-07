package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort.DecisionAcceso;
import com.prisaly.m6.application.service.PrepararEvaluacionProtegida;
import com.prisaly.m6.domain.strategy.SolicitudEvaluacion;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

class M6EvaluacionAuthorizationAdapterTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SECRET = "jdbc-password-ficticia";

    private IdentityAccessPort identity;
    private M6EvaluacionAuthorizationAdapter adapter;
    private SolicitudEvaluacion solicitud;
    private ListAppender<ILoggingEvent> logs;
    private Logger root;
    private Level nivelPrevio;

    @BeforeEach
    void setUp() {
        identity = mock(IdentityAccessPort.class);
        adapter = new M6EvaluacionAuthorizationAdapter(identity);
        solicitud = new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID());
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        nivelPrevio = root.getLevel();
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        root.setLevel(Level.TRACE);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(logs);
        root.setLevel(nivelPrevio);
    }

    @Test
    void ausenciaDeIdentidadDeniega() {
        when(identity.currentUser()).thenThrow(new IdentityDomainException(
                IdentityAccessService.CODE_UNAUTHENTICATED, "No authenticated user in request context"));
        assertEquals(DecisionAcceso.DENEGADO, adapter.verificarAcceso(solicitud));
        assertNotEquals(DecisionAcceso.AUTORIZADO, adapter.verificarAcceso(solicitud));
    }

    @Test
    void cuentaInactivaDeniegaAunqueElPuertoDevuelvaElUsuario() {
        when(identity.currentUser()).thenReturn(usuario("Inactivo", Set.of("CREAR_CASO")));
        assertEquals(DecisionAcceso.DENEGADO, adapter.verificarAcceso(solicitud));
        when(identity.currentUser()).thenThrow(new IdentityDomainException(
                IdentityAccessService.CODE_USER_INACTIVE, "User account is inactive"));
        assertEquals(DecisionAcceso.DENEGADO, adapter.verificarAcceso(solicitud));
    }

    @Test
    void usuarioInexistenteDeniega() {
        when(identity.currentUser()).thenThrow(new IdentityDomainException(
                IdentityAccessService.CODE_USER_NOT_FOUND, "User not found"));
        assertEquals(DecisionAcceso.DENEGADO, adapter.verificarAcceso(solicitud));
    }

    @Test
    void sinPoliticaRatificadaDeniegaAunqueTengaCrearCaso() {
        when(identity.currentUser()).thenReturn(usuario("Activo", Set.of("CREAR_CASO")));
        SolicitudEvaluacion otra = new SolicitudEvaluacion(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(DecisionAcceso.DENEGADO, adapter.verificarAcceso(solicitud));
        assertEquals(DecisionAcceso.DENEGADO, adapter.verificarAcceso(otra));
        verify(identity, never()).hasPermission(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(identity, never()).hasRole(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void falloTecnicoDeIdentidadQuedaNoDisponibleYNoRegistraElSecreto() {
        when(identity.currentUser()).thenThrow(new IllegalStateException("connection failed " + SECRET));
        assertEquals(DecisionAcceso.NO_DISPONIBLE, adapter.verificarAcceso(solicitud));
        for (ILoggingEvent event : logs.list) {
            assertFalse(event.getFormattedMessage().contains(SECRET), event.getFormattedMessage());
        }
    }

    @Test
    void solicitudNulaDeniegaSinConsultarIdentidad() {
        assertEquals(DecisionAcceso.DENEGADO, adapter.verificarAcceso(null));
        verify(identity, never()).currentUser();
    }

    @Test
    void ningunaIdentidadActivaProduceAutorizado() {
        when(identity.currentUser()).thenReturn(usuario("Activo", Set.of("CREAR_CASO", "GESTIONAR_SESION")));
        assertNotEquals(DecisionAcceso.AUTORIZADO, adapter.verificarAcceso(solicitud));
    }

    @Test
    void ensamblajeRegistraUnSoloProveedor() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IdentityAccessPort.class, () -> identity);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(
                    org.springframework.context.annotation.Configuration.class));
            for (var candidate : scanner.findCandidateComponents("com.prisaly.config")) {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                boolean supplies = java.util.Arrays.stream(type.getDeclaredMethods()).anyMatch(method ->
                        method.isAnnotationPresent(Bean.class)
                                && AutorizacionEvaluacionPort.class.isAssignableFrom(method.getReturnType()));
                if (supplies) {
                    context.register(type);
                }
            }
            context.refresh();
            assertEquals(1, context.getBeansOfType(AutorizacionEvaluacionPort.class).size());
            assertSame(
                    context.getBean(M6EvaluacionAuthorizationAdapter.class),
                    context.getBean(AutorizacionEvaluacionPort.class));
            assertFalse(context.getBeanFactory().containsBean("prepararEvaluacionProtegida"));
            assertEquals(0, context.getBeansOfType(PrepararEvaluacionProtegida.class).size());
            when(identity.currentUser()).thenReturn(usuario("Activo", Set.of("CREAR_CASO")));
            assertEquals(DecisionAcceso.DENEGADO,
                    context.getBean(AutorizacionEvaluacionPort.class).verificarAcceso(solicitud));
        }
    }

    private static AuthenticatedUser usuario(String estado, Set<String> permisos) {
        return new AuthenticatedUser(USER_ID, "ana.sec@prisaly.test", "Ana", "Docente", estado, permisos);
    }
}
