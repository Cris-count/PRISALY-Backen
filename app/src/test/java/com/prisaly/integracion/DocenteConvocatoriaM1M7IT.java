package com.prisaly.integracion;

import com.prisaly.m1.domain.model.Usuario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.application.port.StudentAccessPort;
import com.prisaly.m1.application.service.DenyingStudentAccessService;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.infrastructure.persistence.PermisoEntity;
import com.prisaly.m1.infrastructure.persistence.PermisoRepository;
import com.prisaly.m4.application.port.IdentidadPort;
import com.prisaly.m4.application.port.NotificacionPort;
import com.prisaly.m4.application.service.SesionAcademicaService;
import com.prisaly.m4.domain.rule.ReglaNegocioException;
import com.prisaly.m4.infrastructure.adapter.IdentidadM1Adapter;
import com.prisaly.m7.application.ProcesarEnviosPendientesService;
import com.prisaly.m7.domain.EstadoEnvioCorreo;
import com.prisaly.m7.infrastructure.NotificacionM4Adapter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Smoke IT: personal auth (M1) → sesión/convocatoria (M4) → aviso + cola Pendiente (M7).
 * Does not enable SMTP worker or student identity (DD-02 remains open).
 */
@SpringBootTest
@Testcontainers
class DocenteConvocatoriaM1M7IT {

    private static final String PERMISO_GESTIONAR_SESION = "GESTIONAR_SESION";

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
        registry.add("prisaly.m7.worker.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private IdentityCatalogService identityCatalogService;

    @Autowired
    private PermisoRepository permisoRepository;

    @Autowired
    private IdentityAccessPort identityAccessPort;

    @Autowired
    private StudentAccessPort studentAccessPort;

    @Autowired
    private IdentidadPort identidadPort;

    @Autowired
    private SesionAcademicaService sesionAcademicaService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID docenteId;
    private UUID estudianteId;
    private UUID casoId;
    private Integer permisoGestionarSesion;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String roleName = "DocenteEstab-" + suffix;
        identityCatalogService.createRole(roleName, "Docente estabilización");
        Usuario docente = identityCatalogService.registerUser(
                "Docente Estab",
                "docente.estab." + suffix + "@prisaly.test",
                "Secret#Estab1",
                roleName);
        docenteId = docente.getIdUsuario();
        permisoGestionarSesion = permisoRepository.findByCodigo(PERMISO_GESTIONAR_SESION).map(PermisoEntity::getIdPermiso)
                .orElseGet(() -> identityCatalogService.createPermission(
                        PERMISO_GESTIONAR_SESION, "Gestionar sesión", null).getIdPermiso());
        identityCatalogService.assignPermission(
                docenteId, permisoGestionarSesion, null);

        UUID usuarioEstudiante = identityCatalogService
                .registerUser(
                        "Estudiante Estab",
                        "estudiante.estab." + suffix + "@prisaly.test",
                        "Secret#Estab1",
                        roleName)
                .getIdUsuario();
        estudianteId = UUID.randomUUID();
        casoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"Estudiante\" (\"idEstudiante\",\"idUsuario\") VALUES (?,?)",
                estudianteId,
                usuarioEstudiante);
        jdbc.update(
                """
                INSERT INTO "Caso" ("idCaso","idDocente","nombre","tiempoEstimadoSegundos","estado")
                VALUES (?,?,'Caso estabilización',60,'Publicado')
                """,
                casoId,
                docenteId);
        autenticar(docenteId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void monolitoUsaProveedoresProductivosM1yM7() {
        assertEquals(IdentidadM1Adapter.class, AopUtils.getTargetClass(identidadPort));
        Map<String, NotificacionPort> providers = context.getBeansOfType(NotificacionPort.class);
        assertEquals(1, providers.size());
        assertEquals(
                NotificacionM4Adapter.class,
                AopUtils.getTargetClass(providers.get("notificacionM7")));
        assertTrue(studentAccessPort instanceof DenyingStudentAccessService);
        assertEquals(0, context.getBeanNamesForType(ProcesarEnviosPendientesService.class).length);
    }

    @Test
    void docenteAutorizado_convocaYPersisteAvisoConCorreoPendiente() {
        Instant inicio = Instant.now().plusSeconds(86_400);
        var sesion = sesionAcademicaService.crearSesion(
                docenteId,
                casoId,
                "Sesión estabilización",
                null,
                inicio,
                inicio.plusSeconds(3_600),
                600);

        var convocatoria = sesionAcademicaService.convocarEstudiante(
                docenteId, sesion.getId(), estudianteId);

        assertEquals(1, countRowsForConvocatoria(convocatoria.getId()));
        assertEquals(
                EstadoEnvioCorreo.PENDIENTE,
                jdbc.queryForObject(
                        "SELECT \"estadoEnvio\" FROM \"EnvioCorreo\" WHERE \"idConvocatoria\" = ?",
                        String.class,
                        convocatoria.getId()));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM "Aviso"
                        WHERE "claveEvento" = ?
                        """,
                        Integer.class,
                        "convocatoria:" + convocatoria.getId()));
    }

    @Test
    void docenteSinPermiso_noCreaSesionNiNotifica() {
        identityCatalogService.disablePermissionAssignment(
                docenteId, permisoGestionarSesion);
        Instant inicio = Instant.now().plusSeconds(86_400);
        int sesionesAntes = count("Sesion");
        int avisosAntes = count("Aviso");
        int enviosAntes = count("EnvioCorreo");

        ReglaNegocioException error = assertThrows(
                ReglaNegocioException.class,
                () -> sesionAcademicaService.crearSesion(
                        docenteId,
                        casoId,
                        "Sin permiso",
                        null,
                        inicio,
                        inicio.plusSeconds(3_600),
                        600));

        assertEquals("M4-SIN-PERMISO", error.getCodigo());
        assertEquals(sesionesAntes, count("Sesion"));
        assertEquals(avisosAntes, count("Aviso"));
        assertEquals(enviosAntes, count("EnvioCorreo"));
    }

    @Test
    void identidadAusente_bloqueaGestionDeSesion() {
        SecurityContextHolder.clearContext();
        Instant inicio = Instant.now().plusSeconds(86_400);

        ReglaNegocioException error = assertThrows(
                ReglaNegocioException.class,
                () -> sesionAcademicaService.crearSesion(
                        docenteId,
                        casoId,
                        "Sin identidad",
                        null,
                        inicio,
                        inicio.plusSeconds(3_600),
                        600));

        assertEquals("M4-NO-AUTENTICADO", error.getCodigo());
    }

    @Test
    void accesoEstudiantil_permaneceDenegadoMientrasDd02Abierta() {
        IdentityDomainException error = assertThrows(
                IdentityDomainException.class,
                studentAccessPort::requireCurrentStudent);

        assertEquals(
                DenyingStudentAccessService.CODE_STUDENT_IDENTITY_UNAVAILABLE,
                error.code());
        assertTrue(identityAccessPort.hasPermission(docenteId, PERMISO_GESTIONAR_SESION));
    }

    private static void autenticar(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM \"" + table + "\"", Integer.class);
    }

    private int countRowsForConvocatoria(UUID convocatoriaId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM \"Convocatoria\" WHERE \"idConvocatoria\" = ?",
                Integer.class,
                convocatoriaId);
    }
}
