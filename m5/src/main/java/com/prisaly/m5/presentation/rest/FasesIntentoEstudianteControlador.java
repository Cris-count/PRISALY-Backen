package com.prisaly.m5.presentation.rest;

import com.prisaly.m5.application.ReferenciaIntento;
import com.prisaly.m5.application.acceso.AccesoProtegidoParticipacion;
import com.prisaly.m5.presentation.rest.FasesIntentoDto.Completitud;
import com.prisaly.m5.presentation.rest.FasesIntentoDto.Cuestionario;
import com.prisaly.m5.presentation.rest.FasesIntentoDto.Fase;
import com.prisaly.m5.presentation.rest.FasesIntentoDto.Narrativa;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas estudiantiles de las fases académicas P1–P4 (DC-BE-SIM-B2B-B3-32, B3), bajo el contexto {@code /api}.
 *
 * <p><b>Preparadas, no publicadas.</b> La clase solo se registra con {@value #PROPIEDAD_HTTP}{@code =true}; el valor
 * de {@code application.yml} es {@code false}, así que por defecto las rutas no existen (404). Encender la exposición
 * HTTP no elude la indisponibilidad de las fases: con {@code prisaly.m5.exploracion.fases.enabled=false} el arranque
 * falla cerrado ({@value #CODIGO_HTTP_SIN_FASES}), y el Proxy seguiría rechazando P1–P4 de todos modos.
 *
 * <p>La cadena de seguridad exige la sesión estudiantil de M1 en {@code /v1/estudiantes/**} y, para el POST, un
 * {@code Origin} autorizado. La identidad la resuelve M1 y la titularidad el Proxy {@link AccesoProtegidoParticipacion}:
 * no se lee ninguna cabecera ni parámetro de identidad. Todas las respuestas llevan {@code Cache-Control: no-store}; los
 * errores los traduce {@code ManejadorErroresM5} con el cuerpo {@code {code, message}}.
 */
@RestController
@ConditionalOnProperty(name = FasesIntentoEstudianteControlador.PROPIEDAD_HTTP, havingValue = "true")
@RequestMapping("/v1/estudiantes/intentos/{idIntento}")
public final class FasesIntentoEstudianteControlador {

    /** Interruptor de exposición HTTP de P1–P4; {@code false} en {@code application.yml}. */
    public static final String PROPIEDAD_HTTP = "prisaly.m5.exploracion.fases.http.enabled";
    public static final String CODIGO_HTTP_SIN_FASES = "M5-FASES-HTTP-REQUIERE-FASES";

    private final AccesoProtegidoParticipacion operaciones;

    public FasesIntentoEstudianteControlador(AccesoProtegidoParticipacion operaciones,
            @Value("${prisaly.m5.exploracion.fases.enabled:false}") boolean fasesHabilitadas) {
        if (operaciones == null) {
            throw new IllegalArgumentException("El controlador requiere el acceso protegido de M5");
        }
        if (!fasesHabilitadas) {
            throw new IllegalStateException(CODIGO_HTTP_SIN_FASES + ": " + PROPIEDAD_HTTP
                    + "=true exige prisaly.m5.exploracion.fases.enabled=true");
        }
        this.operaciones = operaciones;
    }

    /** P1: guion publicado fijado en el intento, solo en exploración (R4, D-SIM-10). */
    @GetMapping("/narrativa")
    public ResponseEntity<Narrativa> narrativa(@PathVariable("idIntento") UUID idIntento) {
        return sinCache(Narrativa.de(operaciones.consultarNarrativa(referencia(idIntento))));
    }

    /** P2: fase persistida; base de la recuperación tras recargar. */
    @GetMapping("/fase")
    public ResponseEntity<Fase> fase(@PathVariable("idIntento") UUID idIntento) {
        return sinCache(Fase.de(operaciones.consultarFase(referencia(idIntento))));
    }

    /** P3: entrada al cuestionario, atómica e idempotente (D-SIM-3). Siempre 200; un reintento trae {@code yaIniciado}. */
    @PostMapping("/cuestionario")
    public ResponseEntity<Cuestionario> cuestionario(@PathVariable("idIntento") UUID idIntento) {
        return sinCache(Cuestionario.de(operaciones.iniciarCuestionario(referencia(idIntento))));
    }

    /** P4: completitud informativa, sin preguntas fuera del cuestionario (D-SIM-2). */
    @GetMapping("/completitud")
    public ResponseEntity<Completitud> completitud(@PathVariable("idIntento") UUID idIntento) {
        return sinCache(Completitud.de(operaciones.consultarCompletitud(referencia(idIntento))));
    }

    private static ReferenciaIntento referencia(UUID idIntento) {
        return new ReferenciaIntento(idIntento.toString());
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
