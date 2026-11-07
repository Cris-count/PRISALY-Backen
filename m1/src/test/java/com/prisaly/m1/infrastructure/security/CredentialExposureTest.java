package com.prisaly.m1.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.application.service.PersonnelAuthenticationService;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioRepository;
import com.prisaly.m1.presentation.IdentityExceptionHandler;
import com.prisaly.m1.presentation.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CredentialExposureTest {

    private static final String TOKEN = "secreto-ficticio";
    private static final String PASSWORD = "clave-ficticia";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ProvisionalAccessTokenStore tokenStore;
    @Mock
    private IdentityAccessService identityAccessService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private IdentityCatalogService identityCatalogService;

    private ListAppender<ILoggingEvent> appender;
    private Logger root;
    private Level nivelPrevio;

    @BeforeEach
    void capturaLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        nivelPrevio = root.getLevel();
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.TRACE);
    }

    @AfterEach
    void cierra() {
        root.detachAppender(appender);
        root.setLevel(nivelPrevio);
        SecurityContextHolder.clearContext();
    }

    @Test
    void filtroYManejadorNoRegistranTokenNiContrasena() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                USER_ID, "ana.sec@prisaly.test", "Ana", "Docente", "ACTIVO", Set.of());
        when(tokenStore.resolveActiveUserId(TOKEN)).thenReturn(Optional.of(USER_ID));
        when(identityAccessService.requireUser(USER_ID)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/personal/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
        };
        new ProvisionalBearerAuthenticationFilter(tokenStore, identityAccessService)
                .doFilter(request, response, chain);

        when(identityAccessService.requireUser(USER_ID)).thenThrow(
                new IdentityDomainException("USER_INACTIVE", "User account is inactive"));
        MockHttpServletResponse denied = new MockHttpServletResponse();
        new ProvisionalBearerAuthenticationFilter(tokenStore, identityAccessService)
                .doFilter(request, denied, chain);

        ResponseEntity<ErrorResponse> error = new IdentityExceptionHandler().handleDomain(
                new IdentityDomainException("AUTH_FAILED", "Authentication failed"));

        UsuarioEntity usuario = org.mockito.Mockito.mock(UsuarioEntity.class);
        when(usuarioRepository.findByCorreoIgnoreCase("ana.sec@prisaly.test")).thenReturn(Optional.of(usuario));
        when(usuario.getPasswordHash()).thenReturn("hash");
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(false);
        PersonnelAuthenticationService service = new PersonnelAuthenticationService(
                usuarioRepository, passwordEncoder, tokenStore, identityAccessService, identityCatalogService);
        IdentityDomainException authFailed = assertThrows(
                IdentityDomainException.class,
                () -> service.authenticate("ana.sec@prisaly.test", PASSWORD));

        assertFalse(response.getContentAsString().contains(TOKEN));
        assertFalse(denied.getContentAsString().contains(TOKEN));
        assertFalse(error.getBody().message().contains(PASSWORD));
        assertFalse(error.getBody().message().contains(TOKEN));
        assertFalse(authFailed.getMessage().contains(PASSWORD));
        assertFalse(authFailed.getMessage().contains(TOKEN));
        for (ILoggingEvent event : appender.list) {
            String formatted = event.getFormattedMessage();
            assertFalse(formatted.contains(TOKEN), formatted);
            assertFalse(formatted.contains(PASSWORD), formatted);
            assertFalse(formatted.contains("Authorization"), formatted);
        }
    }
}
