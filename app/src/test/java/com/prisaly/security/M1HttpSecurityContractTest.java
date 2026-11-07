package com.prisaly.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.config.HttpApiConfig;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.application.service.PersonnelAuthenticationService;
import com.prisaly.m1.infrastructure.security.ProvisionalAccessTokenStore;
import com.prisaly.m1.infrastructure.security.ProvisionalBearerAuthenticationFilter;
import com.prisaly.m1.presentation.IdentityExceptionHandler;
import com.prisaly.m1.presentation.PersonalAuthController;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import com.prisaly.m1.presentation.PersonalSessionController;
import com.prisaly.m4.infrastructure.config.M4ProgramacionConsultaAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4ProviderAutoConfiguration;
import com.prisaly.m4.infrastructure.config.M4SchemaAutoConfiguration;
import com.prisaly.web.ApiStatusController;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Contrato HTTP de M1 sin PostgreSQL. Los códigos son los del filtro vigente:
 * 401 antes de MVC, 403 de permiso en el controlador, 404/405 solo con sesión.
 */
@SpringBootTest(
        classes = M1HttpSecurityContractTest.Slice.class,
        properties = "prisaly.cors.allowed-origins=http://localhost:4200")
class M1HttpSecurityContractTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOKEN = "secreto-ficticio";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @MockitoBean
    private ProvisionalAccessTokenStore tokenStore;

    @MockitoBean
    private IdentityAccessService identityAccessService;

    @MockitoBean
    private PersonnelAuthenticationService personnelAuthenticationService;

    private MockMvc mockMvc;

    @BeforeEach
    void usuarioActivoSinCrearCaso() {
        AuthenticatedUser user = new AuthenticatedUser(
                USER_ID, "ana.sec@prisaly.test", "Ana", "Docente", "ACTIVO", Set.of());
        when(tokenStore.resolveActiveUserId(TOKEN)).thenReturn(Optional.of(USER_ID));
        when(identityAccessService.requireUser(USER_ID)).thenReturn(user);
        when(identityAccessService.currentUser()).thenReturn(user);
        when(identityAccessService.hasPermission(USER_ID, "CREAR_CASO")).thenReturn(false);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @AfterEach
    void limpiaContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noCreaUsuarioEnMemoriaNiRegistraSuContrasena() {
        String excluded = environment.getProperty("spring.autoconfigure.exclude[0]");
        if (excluded == null) {
            excluded = environment.getProperty("spring.autoconfigure.exclude");
        }
        assertTrue(
                excluded != null
                        && excluded.contains(UserDetailsServiceAutoConfiguration.class.getName()),
                excluded);
        assertThrows(
                NoSuchBeanDefinitionException.class,
                () -> webApplicationContext.getBean(UserDetailsService.class));
        assertTrue(securityFilterChain.getFilters().stream()
                .anyMatch(ProvisionalBearerAuthenticationFilter.class::isInstance));
    }

    @Test
    void rutaInexistenteSinBearer_es401() throws Exception {
        mockMvc.perform(get("/v1/ruta-inexistente-m1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rutaInexistenteConBearerValido_es404() throws Exception {
        mockMvc.perform(get("/v1/ruta-inexistente-m1").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rutasProtegidasSinAutenticacion_son401() throws Exception {
        mockMvc.perform(get("/v1/personal/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/v1/personal/autorizacion/crear-caso"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void autenticadoSinPermisoCrearCaso_es403() throws Exception {
        mockMvc.perform(get("/v1/personal/autorizacion/crear-caso").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void metodoNoPermitido_sinBearer401_conBearer405() throws Exception {
        mockMvc.perform(get("/v1/personal/autenticar"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/v1/personal/autenticar").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/health").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isMethodNotAllowed());
    }

    private static String bearer() {
        return "Bearer " + TOKEN;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        M4ProgramacionConsultaAutoConfiguration.class,
        M4ProviderAutoConfiguration.class,
        M4SchemaAutoConfiguration.class
    })
    @Import({
        HttpApiConfig.class,
        ProvisionalBearerAuthenticationFilter.class,
        IdentityExceptionHandler.class,
        PersonalSessionController.class,
        PersonalAuthorizationController.class,
        PersonalAuthController.class,
        ApiStatusController.class
    })
    static class Slice {
    }
}
