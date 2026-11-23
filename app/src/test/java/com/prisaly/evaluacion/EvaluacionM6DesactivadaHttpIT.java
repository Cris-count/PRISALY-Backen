package com.prisaly.evaluacion;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.config.M6EvaluacionAuthorizationAdapter;
import com.prisaly.config.m6.EvaluacionDocenteM6;
import com.prisaly.m1.application.service.IdentityCatalogService;
import com.prisaly.m6.application.port.AutorizacionEvaluacionPort;
import com.prisaly.m6.application.port.EvaluacionCasoUso;
import com.prisaly.m6.application.port.SolicitudCapacidadContextoAcademicoPort;
import com.prisaly.web.evaluacion.EvaluacionDocenteController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * DC-55 with the shipped configuration: {@code prisaly.m6.evaluacion.enabled} is not set, so the M6 evaluation does
 * not exist. A real authenticated teacher gets 404 on both routes and nothing is written; the deny-only
 * {@link M6EvaluacionAuthorizationAdapter} remains the only {@link AutorizacionEvaluacionPort}.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EvaluacionM6DesactivadaHttpIT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ApplicationContext contexto;
    @Autowired IdentityCatalogService catalogo;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void interruptorApagadoNoHayRutasNiComposicionM6() throws Exception {
        assertEquals(0, contexto.getBeansOfType(EvaluacionDocenteM6.class).size());
        assertEquals(0, contexto.getBeansOfType(EvaluacionDocenteController.class).size());
        assertEquals(0, contexto.getBeansOfType(EvaluacionCasoUso.class).size());
        assertEquals(0, contexto.getBeansOfType(SolicitudCapacidadContextoAcademicoPort.class).size());
        assertEquals(1, contexto.getBeansOfType(AutorizacionEvaluacionPort.class).size());
        assertInstanceOf(M6EvaluacionAuthorizationAdapter.class, contexto.getBean(AutorizacionEvaluacionPort.class));

        String s = UUID.randomUUID().toString().substring(0, 8);
        catalogo.createRole("M6-55-off-" + s, "Fixture M6-55");
        String correo = "m6-55.off." + s + "@prisaly.test";
        catalogo.registerUser("Docente M6-55 off", correo, "Fixture#M6eval55", "M6-55-off-" + s);
        var http = HttpClient.newHttpClient();
        var login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/personal/autenticar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\",\"password\":\"Fixture#M6eval55\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        String token = json.readTree(login.body()).get("accessToken").asString();
        String ruta = "http://localhost:" + port + "/api/v1/evaluacion/intentos/" + UUID.randomUUID();
        var post = http.send(HttpRequest.newBuilder(URI.create(ruta)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idRubrica\":\"" + UUID.randomUUID() + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, post.statusCode(), post.body());
        var get = http.send(HttpRequest.newBuilder(URI.create(ruta)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, get.statusCode(), get.body());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"Calificacion\"", Integer.class));
    }
}
