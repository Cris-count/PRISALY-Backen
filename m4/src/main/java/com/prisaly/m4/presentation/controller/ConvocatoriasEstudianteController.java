package com.prisaly.m4.presentation.controller;

import com.prisaly.m4.api.ConsultaProgramacionNoDisponibleException;
import com.prisaly.m4.api.ContextoProgramacion;
import com.prisaly.m4.api.ProgramacionAutenticadaPort;
import com.prisaly.m4.presentation.dto.ConvocatoriaEstudianteResponse;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /v1/estudiantes/convocatorias} (DC-BE-CIERRE-FUNCIONAL-09, fase 2): descubrimiento de las
 * convocatorias autorizadas del estudiante autenticado.
 *
 * <ul>
 *   <li>La ruta está bajo {@code /v1/estudiantes/**}: la cadena de seguridad exige la sesión estudiantil de M1.
 *       Con DD-02 desactivado (producción) responde 401/403 y nunca llega aquí.</li>
 *   <li>La identidad sale solo de M1 ({@link ProgramacionAutenticadaPort}); no se acepta ningún identificador de
 *       estudiante del cliente, así que no hay forma de pedir convocatorias ajenas.</li>
 *   <li>«Autorizadas»: {@code estadoAutorizacion = Autorizado} y sin {@code fechaRetiro}. Es un filtro de datos
 *       persistidos, no una regla de elegibilidad: no implica que se pueda iniciar un intento.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/estudiantes/convocatorias")
public class ConvocatoriasEstudianteController {
    static final String AUTORIZADO = "Autorizado";

    private final ObjectProvider<ProgramacionAutenticadaPort> programacion;

    public ConvocatoriasEstudianteController(ObjectProvider<ProgramacionAutenticadaPort> programacion) {
        this.programacion = programacion;
    }

    @GetMapping
    public ResponseEntity<List<ConvocatoriaEstudianteResponse>> listar() {
        ProgramacionAutenticadaPort puerto = programacion.getIfAvailable();
        if (puerto == null) {
            throw new ConsultaProgramacionNoDisponibleException();
        }
        List<ConvocatoriaEstudianteResponse> propias = puerto.listarConvocatoriasPropias().stream()
                .filter(ConvocatoriasEstudianteController::autorizada)
                .map(ConvocatoriaEstudianteResponse::de)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(propias);
    }

    static boolean autorizada(ContextoProgramacion c) {
        return AUTORIZADO.equals(c.estadoAutorizacionConvocatoria()) && c.fechaRetiroConvocatoria().isEmpty();
    }
}
