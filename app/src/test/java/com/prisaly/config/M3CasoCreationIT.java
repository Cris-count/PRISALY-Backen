package com.prisaly.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.infrastructure.persistence.PermisoRepository;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.domain.model.CasoEstado;
import com.prisaly.m3.domain.builder.CasoBuilder;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.presentation.CasoCreationController;
import com.prisaly.m3.presentation.CasoGestionController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** Real app/M1/M3/JPA/Flyway security and commit verification; not Postman evidence. */
@SpringBootTest
@Testcontainers
class M3CasoCreationIT {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15")
            .withCommand("postgres", "-c", "lock_timeout=5s", "-c", "statement_timeout=10s");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "5000");
        registry.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
    }

    @Autowired WebApplicationContext web;
    @Autowired ApplicationContext context;
    @Autowired IdentityCatalogService catalog;
    @Autowired PermisoRepository permissions;
    @Autowired CasoRepositoryPort repository;
    @Autowired CaseAuthorizationPort authorization;
    @Autowired JdbcTemplate jdbc;
    @Autowired CasoApplicationService creationService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    private MockMvc mvc;
    private UUID owner;
    private String token;
    private int permissionId;

    @BeforeEach
    void prepareInstitutionalIdentity() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(web)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        String suffix = UUID.randomUUID().toString();
        String role = "M3Rest" + suffix.substring(0, 12);
        String email = suffix + "@m3-rest.prisaly.test";
        String password = UUID.randomUUID().toString(); // Ephemeral test credential, never evidence.
        catalog.createRole(role, "M3 REST integration fixture");
        owner = catalog.registerUser("Docente M3 REST", email, password, role).getIdUsuario();
        permissionId = permissions.findByCodigo("CREAR_CASO").map(com.prisaly.m1.infrastructure.persistence.PermisoEntity::getIdPermiso)
                .orElseGet(() -> catalog.createPermission("CREAR_CASO", "Crear caso", null).getIdPermiso());
        catalog.assignPermission(owner, permissionId, null);
        var login = mvc.perform(post("/api/v1/personal/autenticar").contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        token = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void fullApplicationHasExactlyOneInstitutionalAuthorizationProvider() {
        // Full app scan: no selected config or authorization replacement hides another provider.
        assertEquals(1, context.getBeansOfType(CaseAuthorizationPort.class).size());
        assertEquals(1, context.getBeansOfType(CasoCurrentUserPort.class).size());
        assertSame(context.getBean(M3CaseAuthorizationAdapter.class), authorization);
        assertSame(authorization, context.getBean(CasoCurrentUserPort.class));
        assertNotNull(context.getBean(com.prisaly.m3.application.service.CasoApplicationService.class));
        assertFalse(context.containsBean("caseAuthorizationFailClosed"));
        assertEquals(1, context.getBeansOfType(CasoPublicationPort.class).size());
        // D6: the real permission is consulted, which requires an authenticated caller.
        assertThrows(com.prisaly.m3.application.port.CasoAuthenticationException.class,
                () -> authorization.canModifyCase(owner.toString(), UUID.randomUUID().toString()));
        assertThrows(com.prisaly.m3.application.port.CasoAuthenticationException.class,
                () -> authorization.canPublishCase(owner.toString(), UUID.randomUUID().toString()));
    }

    @Test
    void authorizedPostCommitsAndCanBeReadIndependentlyWithTheM1Owner() throws Exception {
        UUID id = UUID.randomUUID();
        String title = "\uD83D\uDE00".repeat(150);
        mvc.perform(post("/api/v1/casos").contextPath("/api").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(id, title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCaso").value(id.toString()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
        // Reader opens its own REQUIRES_NEW transaction: visibility here requires the POST commit.
        var stored = repository.findById(id.toString()).orElseThrow();
        assertEquals(owner.toString(), stored.docenteId());
        assertEquals(title, stored.titulo());
        assertEquals(CasoEstado.BORRADOR, stored.estado());
        assertEquals(300, stored.tiempoEstimadoSegundos());
        assertEquals("Narrativa de prueba", stored.historia());
        assertNull(stored.desarrolloSituacional());
        assertNotNull(stored.fechaCreacion());
        assertNull(stored.fechaBloqueoAcademico());
        assertTrue(stored.escenarios().isEmpty());
        assertNull(stored.rubrica());
        assertThrows(com.prisaly.m3.application.port.CasoAuthenticationException.class,
                () -> authorization.canModifyCase(owner.toString(), id.toString()));
        assertThrows(com.prisaly.m3.application.port.CasoAuthenticationException.class,
                () -> authorization.canPublishCase(owner.toString(), id.toString()));
    }

    @Test
    void duplicateIdReturns409AndPreservesTheFirstCommittedCase() throws Exception {
        UUID id = UUID.randomUUID();
        create(id, "Primero", 201);
        create(id, "No sobrescribir", 409);
        assertEquals("Primero", repository.findById(id.toString()).orElseThrow().titulo());
        assertEquals(1, count(id));
    }

    @Test
    void unauthenticatedAndRevokedPermissionCannotInsert() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/casos").contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON).content(body(id, "No autenticado")))
                .andExpect(status().isUnauthorized());
        assertEquals(0, count(id));
        catalog.disablePermissionAssignment(owner, permissionId);
        create(id, "Permiso revocado", 403);
        assertEquals(0, count(id));
    }

    @Test
    void invalidBearerTokenCannotCreateOrInsert() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/casos").contextPath("/api")
                .header("Authorization", "Bearer invalid-dc-int-02-token")
                .contentType(MediaType.APPLICATION_JSON).content(body(id, "Token invalido")))
                .andExpect(status().isUnauthorized());
        assertEquals(0, count(id));
    }

    @Test
    void inactiveAccountCannotCreateWithAnExistingToken() throws Exception {
        UUID id = UUID.randomUUID();
        catalog.deactivateUser(owner);
        create(id, "Cuenta desactivada", 401);
        assertEquals(0, count(id));
    }

    @Test
    void invalidBodiesCannotInsertOrExposeSql() throws Exception {
        UUID id = UUID.randomUUID();
        String valid = body(id, "Validación");
        List<String> invalid = List.of("{", valid.replace("300", "0"), valid.replace("300", "1.5"),
                valid.replace(id.toString(), "not-a-uuid"),
                valid.substring(0, valid.length() - 1) + ",\"docenteId\":\"" + owner + "\"}",
                valid.substring(0, valid.length() - 1) + ",\"escenarios\":[]}",
                body(id, "\u0000"));
        for (String request : invalid) {
            mvc.perform(post("/api/v1/casos").contextPath("/api").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CASE_REQUEST"));
            assertEquals(0, count(id));
        }
    }

    @Test
    void concurrentPostsWithTheSameExplicitIdCannotOverwriteEachOther() throws Exception {
        UUID id = UUID.randomUUID();
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentCreate(start, id, "A"));
            var second = executor.submit(() -> concurrentCreate(start, id, "B"));
            var statuses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertTrue(statuses.contains(201));
            assertTrue(statuses.contains(409));
            assertEquals(1, count(id));
            assertTrue(Set.of("A", "B").contains(repository.findById(id.toString()).orElseThrow().titulo()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void onlyPostIsMappedAndPublicationHasNoBean() {
        var rest = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType() == CasoCreationController.class).toList();
        assertEquals(1, rest.size());
        assertEquals(Set.of("/v1/casos"), rest.getFirst().getKey().getPatternValues());
        assertEquals(Set.of(RequestMethod.POST), rest.getFirst().getKey().getMethodsCondition().getMethods());
        // Since PR #58 the item route exists, owned only by M3's CasoGestionController (GET/PATCH).
        var item = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getPatternValues().contains("/v1/casos/{idCaso}")).toList();
        assertTrue(item.stream().allMatch(entry -> entry.getValue().getBeanType() == CasoGestionController.class));
        assertEquals(Set.of(RequestMethod.GET, RequestMethod.PATCH), item.stream()
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream())
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, context.getBeansOfType(CasoPublicationPort.class).size());
    }

    private int concurrentCreate(CyclicBarrier start, UUID id, String title) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/v1/casos").contextPath("/api").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(id, title)))
                .andReturn().getResponse().getStatus();
    }

    private void create(UUID id, String title, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/casos").contextPath("/api").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(id, title)))
                .andExpect(status().is(expectedStatus));
    }

    private int count(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM public.\"Caso\" WHERE \"idCaso\" = ?", Integer.class, id);
    }

    private String body(UUID id, String title) throws Exception {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("idCaso", id.toString());
        fields.put("titulo", title);
        fields.put("tiempoEstimadoSegundos", 300);
        fields.put("historia", "Narrativa de prueba");
        fields.put("desarrolloSituacional", null);
        return json.writeValueAsString(fields);
    }

    @Test
    void applicationCreationCommitsEvenWhenAnOuterCallerRollsBack() {
        UUID id = UUID.randomUUID();
        asAuthenticatedOwner(() -> new TransactionTemplate(transactions).executeWithoutResult(outer -> {
            creationService.createDraft(owner.toString(), draft(id));
            assertTrue(repository.findById(id.toString()).isPresent(),
                    "Application creation must commit before returning, independently of the caller");
            outer.setRollbackOnly();
        }));
        assertEquals(1, count(id));
    }

    @Test
    void applicationCreationRejectsADifferentOwnerWithoutInserting() {
        UUID id = UUID.randomUUID();
        asAuthenticatedOwner(() -> {
            var failure = assertThrows(CasoDomainException.class, () -> creationService.createDraft(
                    owner.toString(), draft(id).withDocenteId(UUID.randomUUID().toString())));
            assertEquals("FORBIDDEN", failure.code());
        });
        assertEquals(0, count(id));
    }

    @Test
    void applicationCreationRollsBackRootAndEarlierChildrenOnDatabaseFailure() {
        UUID original = UUID.randomUUID();
        UUID existingScenario = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID earlierChild = UUID.randomUUID();
        asAuthenticatedOwner(() -> {
            creationService.createDraft(owner.toString(), draft(original)
                    .addEscenario(existingScenario.toString(), "Existing", null, null, 1, true).and());
            var candidate = draft(failed)
                    .addEscenario(earlierChild.toString(), "Inserted first", null, null, 1, true).and()
                    .addEscenario(existingScenario.toString(), "Collision", null, null, 2, true).and();
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> creationService.createDraft(owner.toString(), candidate));
            boolean duplicate = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                    duplicate = true;
                }
            }
            assertTrue(duplicate, "The fixture must fail on the actual database uniqueness constraint");
        });
        assertEquals(0, count(failed));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.\"Escenario\" WHERE \"idEscenario\" = ?", Integer.class, earlierChild));
        assertEquals(1, count(original));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM public.\"Escenario\" WHERE \"idEscenario\" = ?", Integer.class, existingScenario));
    }

    private CasoBuilder draft(UUID id) {
        return new CasoBuilder().withId(id.toString()).withDocenteId(owner.toString())
                .withTitulo("Application transaction regression").withTiempoEstimadoSegundos(300);
    }

    private void asAuthenticatedOwner(Runnable action) {
        // Direct application invocation: M1 still reloads the real account and effective permission.
        // The HTTP tests above separately exercise the real Bearer filter.
        var previous = SecurityContextHolder.getContext();
        var current = SecurityContextHolder.createEmptyContext();
        current.setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()));
        SecurityContextHolder.setContext(current);
        try {
            action.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
