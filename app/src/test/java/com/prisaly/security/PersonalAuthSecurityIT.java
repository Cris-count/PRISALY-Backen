package com.prisaly.security;

import com.prisaly.m1.domain.model.Usuario;
import com.prisaly.m1.domain.model.Permiso;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.PrisalyApplication;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

@SpringBootTest(classes = PrisalyApplication.class)
@Testcontainers
@Import(StudentAccessProbeTestController.class)
class PersonalAuthSecurityIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15")
            .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw"))
            .withStartupTimeout(java.time.Duration.ofSeconds(120));

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
        identityCatalogService.registerUser("Ana", "ana.sec@prisaly.test", "Fixture#Clave-2026", "DocenteOk");

        MvcResult login = mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"ana.sec@prisaly.test","password":"Fixture#Clave-2026"}
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
        identityCatalogService.registerUser("Bob", "bob.sec@prisaly.test", "Fixture#Clave-2026", "DocenteBadPwd");

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
        Usuario user = identityCatalogService.registerUser(
                "Inactive", "inactive.sec@prisaly.test", "Fixture#Clave-2026", "DocenteInactive");
        identityCatalogService.deactivateUser(user.getIdUsuario());

        mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"inactive.sec@prisaly.test","password":"Fixture#Clave-2026"}
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
    void studentAccessProbe_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/v1/test/student-access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void studentAccessProbe_withPersonnelToken_returns403() throws Exception {
        identityCatalogService.createRole("DocenteStudentProbe", "Docente");
        identityCatalogService.registerUser(
                "Probe", "probe.student@prisaly.test", "Fixture#Clave-2026", "DocenteStudentProbe");

        MvcResult login = mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"probe.student@prisaly.test","password":"Fixture#Clave-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String token = readToken(login);

        mockMvc.perform(get("/v1/test/student-access").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(DenyingStudentAccessService.CODE_STUDENT_IDENTITY_UNAVAILABLE));
    }

    @Test
    void estudianteValidar_remains501WhileStudentAccessDisabled() throws Exception {
        mockMvc.perform(post("/v1/acceso-estudiante/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"challenge-ausente","codigo":"12345678"}
                                """))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("ESTUDIANTE_ACCESS_PENDING_CONTRACT"));
    }

    @Test
    void revokedPermission_returns403() throws Exception {
        identityCatalogService.createRole("DocentePerm", "Docente");
        Usuario user = identityCatalogService.registerUser(
                "Perm", "perm.sec@prisaly.test", "Fixture#Clave-2026", "DocentePerm");
        Permiso permiso = identityCatalogService.createPermission(
                PersonalAuthorizationController.PERMISSION_CREAR_CASO, "Crear caso", null);
        java.util.UUID admin = administradorFixture(); // DM1-02: setup only
        identityCatalogService.assignPermission(user.getIdUsuario(), permiso.getIdPermiso(), admin);

        MvcResult login = mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"perm.sec@prisaly.test","password":"Fixture#Clave-2026"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String token = readToken(login);

        mockMvc.perform(get("/v1/personal/autorizacion/crear-caso")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        identityCatalogService.disablePermissionAssignment(user.getIdUsuario(), permiso.getIdPermiso(), admin);

        mockMvc.perform(get("/v1/personal/autorizacion/crear-caso")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String readToken(MvcResult login) throws Exception {
        JsonNode node = objectMapper.readTree(login.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }
    /** DM1-02 (DC-M1-03): assignments need an active ADMINISTRADOR actor. Fixture setup only. */
    private java.util.UUID administradorFixture() {
        if (!identityCatalogService.existeRolAdministrador()) {
            identityCatalogService.createRole(IdentityCatalogService.ROL_ADMINISTRADOR, "Fixture");
        }
        return identityCatalogService.registerUser("Administrador fixture", java.util.UUID.randomUUID() + "@admin.prisaly.test",
                java.util.UUID.randomUUID().toString(), IdentityCatalogService.ROL_ADMINISTRADOR).getIdUsuario();
    }
}
