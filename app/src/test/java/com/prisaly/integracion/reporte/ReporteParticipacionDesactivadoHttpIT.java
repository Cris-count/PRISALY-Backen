package com.prisaly.integracion.reporte;

import static org.junit.jupiter.api.Assertions.*;

import com.prisaly.PrisalyApplication;
import com.prisaly.web.reporte.ReporteParticipacionController;
import com.prisaly.web.reporte.ReporteParticipacionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 with the SHIPPED configuration ({@code prisaly.m7.reportes.participacion.enabled=false}):
 * no bean and no route; the owner teacher with GESTIONAR_SESION gets 404 and no CSV.
 */
@SpringBootTest(classes = PrisalyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReporteParticipacionDesactivadoHttpIT extends ReporteParticipacionHttpFixture {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15");
    static { postgres.start(); }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ApplicationContext contexto;

    @Test
    void interruptorApagadoNoHayBeansNiRuta() throws Exception {
        assertEquals("false", contexto.getEnvironment().getProperty("prisaly.m7.reportes.participacion.enabled"));
        assertEquals(0, contexto.getBeansOfType(ReporteParticipacionController.class).size());
        assertEquals(0, contexto.getBeansOfType(ReporteParticipacionService.class).size());
        UUID admin = administrador();
        Docente a = docente(admin, true);
        Sesion s = sesion(a, "Espacio apagado", "EnCurso");
        convocar(s, "No Exportado");
        var r = descargar(a.token(), s.id());
        assertEquals(404, r.statusCode(), texto(r));
        assertFalse(texto(r).contains("No Exportado"));
        assertTrue(r.headers().firstValue("Content-Disposition").isEmpty());
        assertFalse(r.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
    }
}
