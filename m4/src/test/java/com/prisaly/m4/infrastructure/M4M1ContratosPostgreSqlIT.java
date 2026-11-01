package com.prisaly.m4.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.contracttests.m4.M1ContractsTestConfiguration;
import com.prisaly.m4.application.port.*;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.model.SesionAcademica;
import com.prisaly.m4.infrastructure.adapter.IdentidadM1Adapter;
import com.prisaly.m4.presentation.controller.M4ExceptionHandler;
import com.prisaly.m4.presentation.controller.SesionController;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M1 real (servicio/repositorios/PostgreSQL) + adaptador/servicio/controlador M4. La autenticación
 * se coloca en SecurityContext como fixture confiable; no implementa ni prueba login estudiantil.
 * Persistencia M4 y M3/M7 son dobles.
 */
@EnabledIfSystemProperty(named = "m4.baseline.migrations", matches = ".+")
@SpringBootTest(
        classes = M1ContractsTestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class M4M1ContratosPostgreSqlIT {
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @BeforeAll
    static void iniciar() {
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + System.getProperty("m4.baseline.migrations"))
                .load()
                .migrate();
    }

    @AfterAll
    static void terminar() {
        postgres.stop();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired IdentidadPort identidad;
    @Autowired SesionAcademicaService servicio;
    @Autowired SesionRepository sesiones;
    @Autowired CasoAcademicoPort casos;
    private UUID docente;
    private UUID otro;
    private SesionAcademica propia;
    private SesionAcademica ajena;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        reset(sesiones, casos);
        jdbc.execute(
                "TRUNCATE TABLE \"UsuarioPermiso\",\"Usuario\",\"Permiso\",\"Rol\" RESTART IDENTITY"
                    + " CASCADE");
        docente = UUID.randomUUID();
        otro = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"Rol\" (\"idRol\",\"nombre\",\"descripcion\") VALUES"
                    + " (1,'Docente','Fixture')");
        jdbc.update(
                "INSERT INTO \"Permiso\" (\"idPermiso\",\"codigo\",\"nombre\") VALUES"
                    + " (1,'GESTIONAR_SESION','Fixture M4')");
        jdbc.update(
                "INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES"
                    + " (?,1,'Docente','uno@example.invalid')",
                docente);
        jdbc.update(
                "INSERT INTO \"Usuario\" (\"idUsuario\",\"idRol\",\"nombre\",\"correo\") VALUES"
                    + " (?,1,'Otro','dos@example.invalid')",
                otro);
        jdbc.update(
                "INSERT INTO \"UsuarioPermiso\" (\"idUsuario\",\"idPermiso\",\"habilitado\") VALUES"
                    + " (?,1,true)",
                docente);
        var inicio = Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS);
        propia =
                SesionAcademica.crear(
                        UUID.randomUUID(),
                        docente,
                        "Propia",
                        null,
                        inicio,
                        inicio.plus(1, java.time.temporal.ChronoUnit.HOURS),
                        1800);
        ajena =
                SesionAcademica.crear(
                        UUID.randomUUID(),
                        otro,
                        "Ajena",
                        null,
                        inicio,
                        inicio.plus(1, java.time.temporal.ChronoUnit.HOURS),
                        1800);
        when(sesiones.buscarPorId(propia.getId())).thenReturn(Optional.of(propia));
        when(sesiones.buscarParaActualizar(propia.getId())).thenReturn(Optional.of(propia));
        when(sesiones.buscarPorId(ajena.getId())).thenReturn(Optional.of(ajena));
        when(sesiones.buscarParaActualizar(ajena.getId())).thenReturn(Optional.of(ajena));
        when(sesiones.listarPorDocente(docente)).thenReturn(List.of(propia));
        when(sesiones.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(casos.estaPublicado(any())).thenReturn(true);
        mvc =
                MockMvcBuilders.standaloneSetup(new SesionController(servicio))
                        .setControllerAdvice(new M4ExceptionHandler())
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(docente, null, List.of()));
    }

    @AfterEach
    void limpiarIdentidad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lecturaPropiaYListadoUsanIdentidadYPermisoPersistidosM1() throws Exception {
        assertInstanceOf(IdentidadM1Adapter.class, identidad);
        mvc.perform(get("/m4/sesiones/" + propia.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docenteId").value(docente.toString()));
        mvc.perform(get("/m4/sesiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(propia.getId().toString()));
        verify(sesiones, never()).listarTodas();
    }

    @Test
    void lecturaAjenaYFiltroAjenoSeRechazan() throws Exception {
        mvc.perform(get("/m4/sesiones/" + ajena.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/m4/sesiones").param("docenteId", otro.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cabeceraDeOtroDocenteNoSuplantaLaIdentidadReal() throws Exception {
        clearInvocations(sesiones, casos);
        mvc.perform(
                        post("/m4/sesiones")
                                .header("X-Docente-Id", otro)
                                .contentType("application/json")
                                .content(creacion()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sesiones, casos);
    }

    @Test
    void creacionSinCabeceraObtieneDocenteDeM1() throws Exception {
        mvc.perform(post("/m4/sesiones").contentType("application/json").content(creacion()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.docenteId").value(docente.toString()));
        verify(sesiones).guardar(argThat(sesion -> docente.equals(sesion.getDocenteId())));
    }

    @Test
    void permisoRevocadoEnPostgresqlImpideLectura() throws Exception {
        jdbc.update(
                "UPDATE \"UsuarioPermiso\" SET \"habilitado\"=false WHERE \"idUsuario\"=?",
                docente);
        mvc.perform(get("/m4/sesiones/" + propia.getId())).andExpect(status().isForbidden());
    }

    @Test
    void sinAutenticacionNoHayLecturaYAccesoLegadoNoAutoriza() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/m4/sesiones/" + propia.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/m4/sesiones/" + propia.getId() + "/acceso/" + UUID.randomUUID()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("M4-ACCESO-LEGADO-RETIRADO"));
    }

    private String creacion() {
        return "{\"casoId\":\""
                + UUID.randomUUID()
                + "\",\"titulo\":\"Nueva\","
                + "\"fechaInicio\":\"2030-11-05T09:00:00Z\",\"fechaFin\":\"2030-11-05T11:00:00Z\",\"tiempoMaximoParticipacionSegundos\":1800}";
    }
}
