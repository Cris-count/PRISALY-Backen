package com.prisaly.web.reporte;

import com.prisaly.config.dc92.VistaParticipacionDc92;
import com.prisaly.m4.application.service.SesionAcademicaService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-16 / DD-11 (DC-92): read-only participation report of ONE session for its owner teacher.
 *
 * <p>No policy is introduced: authorization is exactly M4's session read
 * ({@link SesionAcademicaService#consultarSesionAutorizada}: authenticated personnel, existing permission
 * {@code GESTIONAR_SESION}, owner of the session; 401/403/404 as M4). The rows come from the DC-92 read model
 * ({@link VistaParticipacionDc92}), which exposes a grade only once it is released (N2). Nothing is written. Needs the
 * DC-92 candidate schema; startup fails closed without it ({@code RF16-REQUIERE-ESQUEMA-DC92}).
 */
@Service
@ConditionalOnProperty(name = ReporteParticipacionController.PROPIEDAD, havingValue = "true")
public class ReporteParticipacionService {

    record Reporte(String nombreArchivo, byte[] contenido) { }

    private final SesionAcademicaService sesiones;
    private final VistaParticipacionDc92 vista;

    public ReporteParticipacionService(SesionAcademicaService sesiones, JdbcTemplate jdbc) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        VistaParticipacionDc92.verificarEsquema(Objects.requireNonNull(jdbc, "jdbc"), "RF16-REQUIERE-ESQUEMA-DC92");
        this.vista = new VistaParticipacionDc92(jdbc);
    }

    @Transactional(readOnly = true)
    public Reporte generar(UUID idSesion) {
        var sesion = sesiones.consultarSesionAutorizada(idSesion);
        return new Reporte("participacion-sesion-" + sesion.getId() + ".csv",
                ReporteParticipacionCsv.generar(vista.filas(sesion.getId())));
    }
}
