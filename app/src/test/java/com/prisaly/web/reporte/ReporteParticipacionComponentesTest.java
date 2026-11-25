package com.prisaly.web.reporte;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prisaly.m4.api.ReglaNegocioException;
import com.prisaly.m4.application.service.SesionAcademicaService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** DC-BE-CIERRE-FUNCIONAL-72: switch, authorization-before-read and M4-uniform error mapping. */
class ReporteParticipacionComponentesTest {

    @Test
    void controladorYServicioSoloExistenConElInterruptorEnTrue() {
        for (Class<?> tipo : new Class<?>[] {ReporteParticipacionController.class, ReporteParticipacionService.class}) {
            var c = tipo.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(c, tipo.getSimpleName());
            assertArrayEquals(new String[] {"prisaly.m7.reportes.participacion.enabled"}, c.name());
            assertEquals("true", c.havingValue());
            assertFalse(c.matchIfMissing(), "OFF when the property is absent");
        }
    }

    @Test
    void sinAutorizacionM4NoSeConsultaNingunDato() {
        var sesiones = mock(SesionAcademicaService.class);
        var jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(sesiones.consultarSesionAutorizada(id))
                .thenThrow(new ReglaNegocioException("M4-SESION-AJENA", "El docente no es propietario de esta sesión"));
        // DC-92: the constructor verifies the DC-92 schema once; no data query may follow a denied authorization.
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Boolean.class),
                org.mockito.ArgumentMatchers.<Object>any())).thenReturn(true);
        var servicio = new ReporteParticipacionService(sesiones, jdbc);
        var ex = assertThrows(ReglaNegocioException.class, () -> servicio.generar(id));
        assertEquals("M4-SESION-AJENA", ex.getCodigo());
        verify(jdbc, never()).query(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    void erroresConLosCodigosYEstadosDeM4() {
        var controlador = new ReporteParticipacionController(mock(ReporteParticipacionService.class));
        assertEquals(401, estado(controlador, "M4-NO-AUTENTICADO"));
        assertEquals(403, estado(controlador, "M4-SIN-PERMISO"));
        assertEquals(403, estado(controlador, "M4-SESION-AJENA"));
        assertEquals(404, estado(controlador, "M4-SESION-NO-ENCONTRADA"));
        assertEquals(503, estado(controlador, "M4-PROVEEDOR-NO-DISPONIBLE"));
        var interno = controlador.regla(new ReglaNegocioException("OTRO", "detalle interno"));
        assertEquals(500, interno.getStatusCode().value());
        assertEquals("M4-ERROR-INTERNO", interno.getBody().code());
        assertFalse(interno.getBody().message().contains("detalle"), "no internal detail is exposed");
        assertEquals(MediaType.APPLICATION_JSON, interno.getHeaders().getContentType());
        assertEquals(400, controlador.idInvalido().getStatusCode().value());
    }

    private static int estado(ReporteParticipacionController c, String codigo) {
        var r = c.regla(new ReglaNegocioException(codigo, "m"));
        assertEquals(codigo, r.getBody().code());
        assertEquals("no-store", r.getHeaders().getCacheControl());
        return r.getStatusCode().value();
    }
}
