package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.PrisalyApplication;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.UsuarioEntity;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.presentation.CasoPatchConcurrencyContract;
import com.prisaly.m3.presentation.CasoStrongEtagContract;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
 * DC-M1-POSTCIERRE-01: readiness of the M1/APP side for M3 GET once a read policy and the
 * If-Match/ETag transport are ratified. The two TEST-ONLY beans below stand in for those pending
 * decisions; production registers neither (see ComposicionInstitucionalM1IT). Identity, ownership,
 * persistence and the productive write policy (still denying) are the real ones.
 */
@SpringBootTest(classes = PrisalyApplication.class)
@Testcontainers
@Import(ComposicionM3LecturaPreparadaIT.PoliticasPendientesDeRatificacion.class)
class ComposicionM3LecturaPreparadaIT {

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

    @TestConfiguration(proxyBeanMethods = false)
    static class PoliticasPendientesDeRatificacion {
        @Bean
        AutorizacionConsultaCasoPort politicaLecturaDePrueba() {
            return new AutorizacionConsultaCasoPort() {
                @Override
                public Decision verificarAcceso(UUID usuario, UUID caso) {
                    return Decision.AUTORIZADO;
                }

                @Override
                public Decision verificarVisibilidad(UUID usuario, UUID caso,
                        com.prisaly.m3.domain.model.CasoEstado estado, boolean activo) {
                    return Decision.AUTORIZADO;
                }
            };
        }

        @Bean
        CasoPatchConcurrencyContract transporteEtagDePrueba() {
            return new CasoStrongEtagContract();
        }
    }

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
    void elDocentePropietarioLeeSuCasoConEtagFuerteYSinContenidoProtegido() throws Exception {
        String token = docente("lector.m3@prisaly.test");
        UUID caso = crearCaso(token, "Caso legible");

        MvcResult lectura = mockMvc.perform(get("/v1/casos/{id}", caso).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.idCaso").value(caso.toString()))
                .andExpect(jsonPath("$.titulo").value("Caso legible"))
                .andReturn();
        String etag = lectura.getResponse().getHeader(HttpHeaders.ETAG);
        assertTrue(etag != null && etag.matches("\"m3-v2:[0-9a-f]{64}\""), etag);
        assertEquals(4, objectMapper.readTree(lectura.getResponse().getContentAsString()).size());
    }

    @Test
    void laPoliticaDeLecturaNoSustituyeLaTitularidadNiLaIdentidad() throws Exception {
        String propietario = docente("propietario.m3@prisaly.test");
        String ajeno = docente("ajeno.m3@prisaly.test");
        UUID caso = crearCaso(propietario, "Caso ajeno");

        mockMvc.perform(get("/v1/casos/{id}", caso).header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/casos/{id}", caso).header("X-Docente-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/casos/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + propietario))
                .andExpect(status().isNotFound());
    }

    @Test
    void conEtagValidoElPatchSigueDenegadoPorLaPoliticaDeEscrituraProductiva() throws Exception {
        String token = docente("escritor.m3@prisaly.test");
        UUID caso = crearCaso(token, "Caso sin edicion");
        String etag = mockMvc.perform(get("/v1/casos/{id}", caso).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(patch("/v1/casos/{id}", caso).header("Authorization", "Bearer " + token)
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Editado\"}"))
                .andExpect(status().isForbidden());
        assertEquals("Caso sin edicion", jdbc.queryForObject(
                "SELECT \"nombre\" FROM \"Caso\" WHERE \"idCaso\" = ?", String.class, caso));
    }

    private UUID crearCaso(String token, String titulo) throws Exception {
        UUID caso = UUID.randomUUID();
        mockMvc.perform(post("/v1/casos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCaso\":\"" + caso + "\",\"titulo\":\"" + titulo + "\",\"tiempoEstimadoSegundos\":300}"))
                .andExpect(status().isCreated());
        return caso;
    }

    private String docente(String correo) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
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

    private Integer idPermisoCrearCaso() {
        var existentes = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?",
                Integer.class, PersonalAuthorizationController.PERMISSION_CREAR_CASO);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        return catalog.createPermission(PersonalAuthorizationController.PERMISSION_CREAR_CASO, "Crear caso", null)
                .getIdPermiso();
    }
}
