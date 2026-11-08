package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.application.service.IdentityAccessService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.infrastructure.security.EstudianteSessionFilter;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.presentation.CasoPatchConcurrencyContract;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import tools.jackson.databind.ObjectMapper;

/**
 * DC-M1-CIERRE-02 (phases 4-5): real APP composition over PostgreSQL 16.15 with the authorized
 * schema. Checks one provider per institutional port and that M3 GET/PATCH stay denied with the
 * real M1 identity while no read/write policy is ratified. No mock replaces a production bean.
 */
@SpringBootTest(classes = PrisalyApplication.class)
@Testcontainers
class ComposicionInstitucionalM1IT {

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
    private ApplicationContext context;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private IdentityCatalogService catalog;

    @Autowired
    private JdbcTemplate jdbc;

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
    void cadaPuertoInstitucionalTieneUnSoloProveedorEsperado() {
        assertInstanceOf(IdentityAccessService.class, unico(IdentityAccessPort.class));
        // DD-02 not activated: the student identity provider is the denying one.
        assertInstanceOf(DenyingStudentAccessService.class, unico(StudentAccessPort.class));
        assertInstanceOf(M6EvaluacionAuthorizationAdapter.class, unico(AutorizacionEvaluacionPort.class));
        Object escrituraM3 = unico(CaseAuthorizationPort.class);
        assertInstanceOf(M3CaseAuthorizationAdapter.class, escrituraM3);
        assertSame(escrituraM3, unico(CasoCurrentUserPort.class));
        // DC-M3-RES-02: one institutional read provider, with the policy still pending (denies).
        assertInstanceOf(M3CaseReadAuthorizationAdapter.class, unico(AutorizacionConsultaCasoPort.class));
        // No ratified HTTP concurrency transport: no provider may exist.
        assertTrue(context.getBeansOfType(CasoPatchConcurrencyContract.class).isEmpty());
        assertTrue(context.getBeansOfType(EstudianteSessionFilter.class).isEmpty());
    }

    @Test
    void getYPatchDeCasoPropioSeDeneganSinPoliticaRatificadaYNoModificanDatos() throws Exception {
        String token = docenteConCrearCaso("propio.comp@prisaly.test");
        UUID caso = UUID.randomUUID();
        mockMvc.perform(post("/v1/casos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCaso\":\"" + caso + "\",\"titulo\":\"Original\",\"tiempoEstimadoSegundos\":300}"))
                .andExpect(status().isCreated());

        // CREAR_CASO does not grant reading nor modifying.
        mockMvc.perform(get("/v1/casos/{id}", caso).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(patch("/v1/casos/{id}", caso).header("Authorization", "Bearer " + token)
                        .header("If-Match", "\"m3-v2:" + "0".repeat(64) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Cambiado\"}"))
                .andExpect(status().isForbidden());

        assertEquals("Original", jdbc.queryForObject(
                "SELECT \"nombre\" FROM \"Caso\" WHERE \"idCaso\" = ?", String.class, caso));
    }

    @Test
    void sinIdentidadVerificableLasRutasDeCasoSon401() throws Exception {
        UUID caso = UUID.randomUUID();
        mockMvc.perform(get("/v1/casos/{id}", caso)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/v1/casos/{id}", caso).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cabecerasDeIdentidadYCookieEstudiantilNoCreanIdentidadDocente() throws Exception {
        String usuario = UUID.randomUUID().toString();
        mockMvc.perform(get("/v1/personal/me")
                        .header("X-Docente-Id", usuario)
                        .header("X-User-Id", usuario)
                        .cookie(new Cookie(EstudianteSessionFilter.COOKIE, "token-estudiante-inventado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/v1/casos")
                        .header("X-Docente-Id", usuario)
                        .cookie(new Cookie(EstudianteSessionFilter.COOKIE, "token-estudiante-inventado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCaso\":\"" + UUID.randomUUID() + "\",\"titulo\":\"X\",\"tiempoEstimadoSegundos\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPermiteIfMatchYExponeEtagSoloAlOrigenConfigurado() throws Exception {
        String origen = "http://localhost:4200";
        String caso = "/v1/casos/" + UUID.randomUUID();
        mockMvc.perform(options(caso)
                        .header("Origin", origen)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization, content-type, if-match"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origen))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("if-match")));
        mockMvc.perform(options(caso)
                        .header("Origin", "https://otro-origen.example")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options(caso)
                        .header("Origin", origen)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "x-docente-id"))
                .andExpect(status().isForbidden());
        // Actual (non-preflight) responses expose ETag to the browser even when denied.
        mockMvc.perform(get(caso).header("Origin", origen))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Expose-Headers", containsStringIgnoringCase("ETag")));
    }

    @Test
    void elTokenDocenteNoEsIdentidadEstudiantil() throws Exception {
        String token = docenteConCrearCaso("docente.m5.comp@prisaly.test");
        mockMvc.perform(get("/v1/estudiantes/intentos/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_IDENTITY_UNAVAILABLE"));
    }

    private Object unico(Class<?> tipo) {
        var beans = context.getBeansOfType(tipo);
        assertEquals(1, beans.size(), tipo.getSimpleName() + " providers: " + beans.keySet());
        return beans.values().iterator().next();
    }

    // The permission catalog is shared by the tests of this class (same container).
    private Integer idPermisoCrearCaso() {
        var existentes = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?",
                Integer.class, PersonalAuthorizationController.PERMISSION_CREAR_CASO);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        PermisoEntity creado = catalog.createPermission(PersonalAuthorizationController.PERMISSION_CREAR_CASO, "Crear caso", null);
        return creado.getIdPermiso();
    }

    private String docenteConCrearCaso(String correo) throws Exception {
        String rol = "Rol" + Math.abs(correo.hashCode());
        catalog.createRole(rol, "Docente");
        UsuarioEntity user = catalog.registerUser("Docente", correo, "Secret#1", rol);
        catalog.assignPermission(user.getIdUsuario(), idPermisoCrearCaso(), null);
        MvcResult login = mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"password\":\"Secret#1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
