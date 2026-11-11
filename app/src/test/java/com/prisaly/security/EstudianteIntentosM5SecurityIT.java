package com.prisaly.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prisaly.PrisalyApplication;
import java.util.List;
import java.util.UUID;

import com.prisaly.m1.application.service.IdentityCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * DC-DD02-01: GET y PUT de M5 registrados en la aplicación real (contexto completo, cadena de
 * Spring Security de {@code HttpApiConfig}, filtro Bearer provisional de M1 y PostgreSQL 16.15).
 * Solo casos negativos: mientras DD-02 siga abierta ninguna credencial produce identidad
 * estudiantil. Las denegaciones no deben escribir en las tablas de participación de BD-005.2.
 */
@SpringBootTest(classes = PrisalyApplication.class)
@Testcontainers
class EstudianteIntentosM5SecurityIT {

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

    private static final List<String> TABLAS = List.of("Participacion", "Intento", "Respuesta");
    private static final String INTENTO = UUID.randomUUID().toString();
    private static final String PREGUNTA = UUID.randomUUID().toString();
    private static final String GET_URL = "/v1/estudiantes/intentos/" + INTENTO;
    private static final String PUT_URL = GET_URL + "/respuestas/" + PREGUNTA;
    private static final String CUERPO =
            "{\"idOpcionSeleccionada\":\"" + UUID.randomUUID() + "\",\"revisionEsperada\":0}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private IdentityCatalogService identityCatalogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    private List<Long> conteos() {
        return TABLAS.stream()
                .map(t -> jdbc.queryForObject("select count(*) from \"" + t + "\"", Long.class)).toList();
    }

    private String tokenPersonal(String sufijo) throws Exception {
        String correo = "m5.dd02." + sufijo + "@prisaly.test";
        identityCatalogService.createRole("DocenteM5" + sufijo, "Docente");
        identityCatalogService.registerUser("Docente M5", correo, "Fixture#Clave-2026", "DocenteM5" + sufijo);
        MvcResult login = mockMvc.perform(post("/v1/personal/autenticar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"password\":\"Fixture#Clave-2026\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static void sinDatosPrivados(MvcResult r) throws Exception {
        String cuerpo = r.getResponse().getContentAsString();
        assertFalse(cuerpo.contains(INTENTO), cuerpo);
        assertFalse(cuerpo.contains(PREGUNTA), cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("exception"), cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("sql"), cuerpo);
    }

    @Test
    void sinCredencial_getYPut401() throws Exception {
        List<Long> antes = conteos();
        sinDatosPrivados(mockMvc.perform(get(GET_URL))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn());
        sinDatosPrivados(mockMvc.perform(put(PUT_URL).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn());
        assertEquals(antes, conteos());
    }

    @Test
    void credencialInvalida_getYPut401() throws Exception {
        List<Long> antes = conteos();
        mockMvc.perform(get(GET_URL).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(PUT_URL).header("Authorization", "Bearer token-invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(GET_URL).header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
        assertEquals(antes, conteos());
    }

    @Test
    void bearerDePersonal_noEsIdentidadEstudiantil_403() throws Exception {
        String token = tokenPersonal("ident");
        List<Long> antes = conteos();
        sinDatosPrivados(mockMvc.perform(get(GET_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_IDENTITY_UNAVAILABLE")).andReturn());
        sinDatosPrivados(mockMvc.perform(put(PUT_URL).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_IDENTITY_UNAVAILABLE")).andReturn());
        assertEquals(antes, conteos());
    }

    @Test
    void cabecerasDeIdentidadManipuladas_noConcedenAcceso() throws Exception {
        String token = tokenPersonal("cabecera");
        String otro = UUID.randomUUID().toString();
        mockMvc.perform(get(GET_URL).header("Authorization", "Bearer " + token)
                        .header("X-Student-Id", otro).header("X-Estudiante-Id", otro).param("idEstudiante", otro))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_IDENTITY_UNAVAILABLE"));
    }

    @Test
    void uuidInvalido_conBearerDePersonal400_ySinCredencial401() throws Exception {
        String token = tokenPersonal("uuid");
        List<Long> antes = conteos();
        mockMvc.perform(get("/v1/estudiantes/intentos/no-es-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/v1/estudiantes/intentos/no-es-uuid/respuestas/" + PREGUNTA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/v1/estudiantes/intentos/no-es-uuid"))
                .andExpect(status().isUnauthorized());
        assertEquals(antes, conteos());
    }

    @Test
    void postDeM5NoRegistrados() throws Exception {
        String token = tokenPersonal("post");
        List<Long> antes = conteos();
        int iniciar = mockMvc.perform(post("/v1/estudiantes/sesiones/" + UUID.randomUUID() + "/intentos")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
        int finalizar = mockMvc.perform(post(GET_URL + "/finalizar")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
        // Sin handler: 404 o 405 según el mapeo de GET con el mismo patrón; nunca 2xx ni 403 de M5.
        assertFalse(iniciar >= 200 && iniciar < 300, "iniciar respondió " + iniciar);
        assertFalse(finalizar >= 200 && finalizar < 300, "finalizar respondió " + finalizar);
        assertEquals(antes, conteos());
    }
}
