package com.prisaly.m5.presentation.rest;

import java.util.UUID;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.domain.respuesta.ReferenciaPregunta;
import com.prisaly.m5.domain.respuesta.ValorRespuesta;
import com.prisaly.m5.presentation.rest.IntentosM5Dto.Guardar;
import com.prisaly.m5.presentation.rest.IntentosM5Dto.Intento;
import com.prisaly.m5.presentation.rest.IntentosM5Dto.RespuestaGuardada;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas M5 registradas en Spring MVC (DC-DD02-01): solo GET de intento y PUT de respuesta, bajo el
 * contexto {@code /api}. Los POST siguen en {@link IntentosM5Controlador}, sin registro.
 *
 * <p>Rechazo seguro mientras DD-02 siga abierta:
 * <ul>
 *   <li>La cadena de seguridad de la aplicación exige autenticación: sin credencial válida responde
 *       401 antes de llegar aquí.</li>
 *   <li>La identidad del estudiante la decide solo M1 a través del proxy
 *       {@link AccesoProtegidoParticipacion}; el Bearer de personal no es identidad estudiantil y
 *       el proxy responde 403 {@code STUDENT_IDENTITY_UNAVAILABLE} sin leer ni escribir intentos.</li>
 *   <li>No lee cabeceras ni parámetros de identidad del cliente.</li>
 * </ul>
 * Titularidad y CAS (D06) se aplican en el proxy y en el servicio transaccional.
 */
@RestController
@RequestMapping("/v1/estudiantes")
public class IntentosEstudianteM5Controlador {

    private final AccesoProtegidoParticipacion operaciones;

    public IntentosEstudianteM5Controlador(AccesoProtegidoParticipacion operaciones) {
        if (operaciones == null) {
            throw new IllegalArgumentException("El controlador requiere el acceso protegido de M5");
        }
        this.operaciones = operaciones;
    }

    /** D05: solo información de M5 (estado, tiempos y respuestas propias con su revisión). */
    @GetMapping("/intentos/{idIntento}")
    public Intento consultar(@PathVariable("idIntento") UUID idIntento) {
        return Intento.de(operaciones.consultarIntento(new ReferenciaIntento(idIntento.toString())));
    }

    /** D06: guardado con revisión esperada; una revisión obsoleta responde 409 sin escritura. */
    @PutMapping("/intentos/{idIntento}/respuestas/{idPregunta}")
    public RespuestaGuardada guardar(@PathVariable("idIntento") UUID idIntento,
            @PathVariable("idPregunta") UUID idPregunta, @Valid @RequestBody Guardar solicitud) {
        return RespuestaGuardada.de(operaciones.guardarRespuesta(
                new ReferenciaIntento(idIntento.toString()),
                new ReferenciaPregunta(idPregunta.toString()),
                new ValorRespuesta(solicitud.idOpcionSeleccionada().toString()),
                solicitud.revisionEsperada()));
    }
}
