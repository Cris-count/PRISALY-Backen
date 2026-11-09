package com.prisaly.integracion;

import com.prisaly.m1.domain.model.Usuario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prisaly.PrisalyApplication;
import com.prisaly.config.M3CaseAuthorizationAdapter;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m1.presentation.PersonalAuthorizationController;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-M3-M1-RES-03 — PATCH readiness with a SIMULATED write policy (test-only {@code @Primary}
 * bean) and the productive strong ETag codec. Identity, ownership, M3 state rules, the
 * transactional fingerprint check and PostgreSQL 16.15 are real.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(ComposicionM3EdicionPreparadaIT.PoliticasSimuladas.class)
class ComposicionM3EdicionPreparadaIT {

    private static final String LECTURA_FIXTURE = "PERMISO_LECTURA_FIXTURE";
    private static final String EDICION_FIXTURE = "PERMISO_EDICION_FIXTURE";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("prisaly.cors.allowed-origins", () -> "http://localhost:4200");
        registry.add("prisaly.m3.lectura.permiso", () -> LECTURA_FIXTURE);
        registry.add("prisaly.m3.lectura.estados-visibles", () -> "BORRADOR,PUBLICADO,ARCHIVADO");
        registry.add("prisaly.m3.lectura.requiere-activo", () -> "true");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PoliticasSimuladas {
        // Simulated write policy: authenticated owner-candidate with the edit fixture permission.
        // Creation and publication keep the productive adapter's decisions.
        @Bean
        @Primary
        CaseAuthorizationPort politicaEscrituraSimulada(M3CaseAuthorizationAdapter real, IdentityAccessPort identity) {
            return new CaseAuthorizationPort() {
                @Override
                public boolean canCreateCase(String userId, String docenteId) {
                    return real.canCreateCase(userId, docenteId);
                }

                @Override
                public boolean canModifyCase(String userId, String caseId) {
                    AuthenticatedUser current = identity.currentUser();
                    return current.userId().toString().equalsIgnoreCase(userId)
                            && identity.hasPermission(current.userId(), EDICION_FIXTURE);
                }

                @Override
                public boolean canPublishCase(String userId, String caseId) {
                    return real.canPublishCase(userId, caseId);
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private IdentityCatalogService catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void conEtagVigenteElPropietarioEditaYLaVersionCambia() throws Exception {
        String token = docente("editor.res03@prisaly.test", true, true);
        UUID caso = crearCaso(token, "Original");
        HttpResponse<String> lectura = get(caso, token);
        assertEquals(200, lectura.statusCode());
        String etag = lectura.headers().firstValue("ETag").orElseThrow();
        assertTrue(etag.matches("\"m3-v2:[0-9a-f]{64}\""), etag);

        assertEquals(204, patch(caso, token, etag, "{\"titulo\":\"Editado\"}").statusCode());
        assertEquals("Editado", titulo(caso));
        assertNotEquals(etag, get(caso, token).headers().firstValue("ETag").orElseThrow());

        // The previous version is now stale: no second write.
        HttpResponse<String> obsoleta = patch(caso, token, etag, "{\"titulo\":\"Otra vez\"}");
        assertEquals(412, obsoleta.statusCode());
        assertTrue(obsoleta.body().contains("STALE_CASE"), obsoleta.body());
        assertEquals("Editado", titulo(caso));
    }

    @Test
    void ifMatchAusenteDebilComodinOMultipleSeRechazaSinEscribir() throws Exception {
        String token = docente("precondicion.res03@prisaly.test", true, true);
        UUID caso = crearCaso(token, "Intacto");
        String etag = get(caso, token).headers().firstValue("ETag").orElseThrow();

        for (String precondicion : new String[] {null, "W/" + etag, "*", etag + ", " + etag, "\"m3-v2:corto\""}) {
            HttpResponse<String> respuesta = patch(caso, token, precondicion, "{\"titulo\":\"Cambio\"}");
            assertEquals(precondicion == null ? 428 : 400, respuesta.statusCode(), "If-Match=" + precondicion);
        }
        assertEquals("Intacto", titulo(caso));
    }

    @Test
    void ajenoOSinPermisoDeEdicionSon403SinEscribir() throws Exception {
        String propietario = docente("duenio.res03@prisaly.test", true, false);
        String ajeno = docente("ajeno.edicion.res03@prisaly.test", true, true);
        UUID caso = crearCaso(propietario, "Protegido");
        String etag = get(caso, propietario).headers().firstValue("ETag").orElseThrow();

        assertEquals(403, patch(caso, propietario, etag, "{\"titulo\":\"Sin permiso\"}").statusCode());
        assertEquals(403, patch(caso, ajeno, etag, "{\"titulo\":\"Ajeno\"}").statusCode());
        assertEquals("Protegido", titulo(caso));
    }

    @Test
    void unCasoArchivadoNoSeEdita() throws Exception {
        String token = docente("archivo.res03@prisaly.test", true, true);
        UUID caso = crearCaso(token, "Archivado");
        jdbc.update("UPDATE \"Caso\" SET \"estado\" = 'Archivado' WHERE \"idCaso\" = ?", caso);
        String etag = get(caso, token).headers().firstValue("ETag").orElseThrow();

        HttpResponse<String> respuesta = patch(caso, token, etag, "{\"titulo\":\"Cambio\"}");
        assertEquals(409, respuesta.statusCode());
        assertTrue(respuesta.body().contains("ARCHIVED_CASE_OPERATION_PENDING"), respuesta.body());
        assertEquals("Archivado", titulo(caso));
    }

    @Test
    void unCasoPublicadoNoSeEditaAunqueLaVersionSeaVigente() throws Exception {
        String token = docente("publicado.qb01@prisaly.test", true, true);
        UUID caso = crearCaso(token, "Publicado");
        jdbc.update("UPDATE \"Caso\" SET \"estado\" = 'Publicado' WHERE \"idCaso\" = ?", caso);
        String etag = get(caso, token).headers().firstValue("ETag").orElseThrow();

        HttpResponse<String> respuesta = patch(caso, token, etag, "{\"titulo\":\"Cambio\"}");
        assertEquals(409, respuesta.statusCode(), respuesta.body());
        assertTrue(respuesta.body().contains("CASE_NOT_EDITABLE"), respuesta.body());
        assertEquals("Publicado", titulo(caso));
    }

    @Test
    void dosEdicionesConcurrentesConLaMismaVersionTienenUnSoloGanador() throws Exception {
        String token = docente("concurrente.res03@prisaly.test", true, true);
        UUID caso = crearCaso(token, "Inicial");
        String etag = get(caso, token).headers().firstValue("ETag").orElseThrow();

        CompletableFuture<HttpResponse<String>> uno = patchAsync(caso, token, etag, "{\"titulo\":\"Uno\"}");
        CompletableFuture<HttpResponse<String>> dos = patchAsync(caso, token, etag, "{\"titulo\":\"Dos\"}");
        Set<Integer> estados = List.of(uno.get(), dos.get()).stream()
                .map(HttpResponse::statusCode).collect(Collectors.toSet());
        assertEquals(Set.of(204, 412), estados);
        assertTrue(Set.of("Uno", "Dos").contains(titulo(caso)));
    }

    private String titulo(UUID caso) {
        return jdbc.queryForObject("SELECT \"nombre\" FROM \"Caso\" WHERE \"idCaso\" = ?", String.class, caso);
    }

    private HttpResponse<String> get(UUID caso, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/v1/casos/" + caso))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest patchRequest(UUID caso, String token, String ifMatch, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/v1/casos/" + caso))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return request.build();
    }

    private HttpResponse<String> patch(UUID caso, String token, String ifMatch, String body) throws Exception {
        return http.send(patchRequest(caso, token, ifMatch, body), HttpResponse.BodyHandlers.ofString());
    }

    private CompletableFuture<HttpResponse<String>> patchAsync(UUID caso, String token, String ifMatch, String body) {
        return http.sendAsync(patchRequest(caso, token, ifMatch, body), HttpResponse.BodyHandlers.ofString());
    }

    private UUID crearCaso(String token, String titulo) throws Exception {
        UUID caso = UUID.randomUUID();
        HttpResponse<String> creado = http.send(HttpRequest.newBuilder(uri("/v1/casos"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"idCaso\":\"" + caso + "\",\"titulo\":\"" + titulo + "\",\"tiempoEstimadoSegundos\":300}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, creado.statusCode(), creado.body());
        return caso;
    }

    private String docente(String correo, boolean lectura, boolean edicion) throws Exception {
        String rol = "Rol-" + UUID.randomUUID().toString().substring(0, 8);
        catalog.createRole(rol, "Docente");
        Usuario user = catalog.registerUser("Docente", correo, "Secret#1", rol);
        catalog.assignPermission(user.getIdUsuario(), idPermiso(PersonalAuthorizationController.PERMISSION_CREAR_CASO), null);
        if (lectura) {
            catalog.assignPermission(user.getIdUsuario(), idPermiso(LECTURA_FIXTURE), null);
        }
        if (edicion) {
            catalog.assignPermission(user.getIdUsuario(), idPermiso(EDICION_FIXTURE), null);
        }
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(uri("/v1/personal/autenticar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"Secret#1\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return objectMapper.readTree(login.body()).get("accessToken").asText();
    }

    private Integer idPermiso(String codigo) {
        var existentes = jdbc.queryForList("SELECT \"idPermiso\" FROM \"Permiso\" WHERE \"codigo\" = ?", Integer.class, codigo);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        return catalog.createPermission(codigo, codigo, null).getIdPermiso();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api" + path);
    }
}
