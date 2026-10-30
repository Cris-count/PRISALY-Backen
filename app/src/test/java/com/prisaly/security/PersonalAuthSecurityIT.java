package com.prisaly.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class PersonalAuthSecurityIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private IdentityCatalogService identityCatalogService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void validCredentials_returnTokenAndAllowMe() throws Exception {
        identityCatalogService.createRole("DocenteOk", "Docente");
        identityCatalogService.registerUser("Ana", "ana.sec@prisaly.test", "Secret#1", "DocenteOk");

        MvcResult login = mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"ana.sec@prisaly.test","password":"Secret#1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.authPrototype").value("TD-M1-AUTH-PROVISIONAL"))
                .andReturn();

        String token = readToken(login);

        mockMvc.perform(get("/v1/personal/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("ana.sec@prisaly.test"));
    }

    @Test
    void wrongPassword_returns401() throws Exception {
        identityCatalogService.createRole("DocenteBadPwd", "Docente");
        identityCatalogService.registerUser("Bob", "bob.sec@prisaly.test", "Secret#1", "DocenteBadPwd");

        mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"bob.sec@prisaly.test","password":"bad"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    void unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"ghost.sec@prisaly.test","password":"x"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    void inactiveAccount_rejectsLoginWith401() throws Exception {
        identityCatalogService.createRole("DocenteInactive", "Docente");
        UsuarioEntity user = identityCatalogService.registerUser(
                "Inactive", "inactive.sec@prisaly.test", "Secret#1", "DocenteInactive");
        identityCatalogService.deactivateUser(user.getIdUsuario());

        mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"inactive.sec@prisaly.test","password":"Secret#1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    void unauthenticatedMe_returns401() throws Exception {
        mockMvc.perform(get("/v1/personal/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void revokedPermission_returns403() throws Exception {
        identityCatalogService.createRole("DocentePerm", "Docente");
        UsuarioEntity user = identityCatalogService.registerUser(
                "Perm", "perm.sec@prisaly.test", "Secret#1", "DocentePerm");
        PermisoEntity permiso = identityCatalogService.createPermission(
                PersonalAuthorizationController.PERMISSION_CREAR_CASO, "Crear caso", null);
        identityCatalogService.assignPermission(user.getIdUsuario(), permiso.getIdPermiso(), null);

        MvcResult login = mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"perm.sec@prisaly.test","password":"Secret#1"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String token = readToken(login);

        mockMvc.perform(get("/v1/personal/autorizacion/crear-caso")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        identityCatalogService.disablePermissionAssignment(user.getIdUsuario(), permiso.getIdPermiso());

        mockMvc.perform(get("/v1/personal/autorizacion/crear-caso")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String readToken(MvcResult login) throws Exception {
        JsonNode node = objectMapper.readTree(login.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }
}
