package com.prisaly.m3.presentation;

import com.prisaly.m3.application.service.CasoDocenteConsultaService;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.model.Caso;
import com.prisaly.m3.domain.model.Escenario;
import com.prisaly.m3.domain.model.Pregunta;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.dto.CasoContenidoResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PD-04: complete content reads for the owning teacher. Every route applies the PD-01 read policy
 * through the existing use case and resolves nested elements inside the authorized aggregate only.
 */
@RestController
@RequestMapping(value = "/v1/casos/{idCaso}", produces = MediaType.APPLICATION_JSON_VALUE)
public final class CasoContenidoConsultaController {
    private static final String ELEMENTO_AJENO = "ELEMENT_NOT_IN_CASE";
    private final CasoDocenteConsultaService read;
    private final CasoPatchConcurrencyContract concurrency;

    public CasoContenidoConsultaController(CasoDocenteConsultaService read,
            Optional<CasoPatchConcurrencyContract> concurrency) {
        this.read = read;
        this.concurrency = concurrency.orElse(null);
    }

    /** Contenido completo del caso del docente dueño, con ETag para la siguiente escritura. */
    @GetMapping("/contenido")
    public ResponseEntity<CasoContenidoResponse> contenido(@PathVariable("idCaso") String id) {
        return responder(id, CasoContenidoResponse::from);
    }

    /** Lista los escenarios del caso. Un docente ajeno no los ve. */
    @GetMapping("/escenarios")
    public ResponseEntity<List<CasoContenidoResponse.EscenarioContenido>> escenarios(@PathVariable("idCaso") String id) {
        return responder(id, CasoContenidoResponse::escenariosDe);
    }

    /** Un escenario del caso. Falla si el identificador no pertenece a ese caso. */
    @GetMapping("/escenarios/{idEscenario}")
    public ResponseEntity<CasoContenidoResponse.EscenarioContenido> escenario(@PathVariable("idCaso") String id,
            @PathVariable("idEscenario") String elemento) {
        UUID buscado = IdentificadorCanonico.uuid(elemento);
        return responder(id, caso -> CasoContenidoResponse.EscenarioContenido.from(escenarioDe(caso, buscado)));
    }

    /** Una pregunta del caso, con su texto y su orden. */
    @GetMapping("/preguntas/{idPregunta}")
    public ResponseEntity<CasoContenidoResponse.PreguntaContenido> pregunta(@PathVariable("idCaso") String id,
            @PathVariable("idPregunta") String elemento) {
        UUID buscado = IdentificadorCanonico.uuid(elemento);
        return responder(id, caso -> CasoContenidoResponse.PreguntaContenido.from(preguntaDe(caso, buscado)));
    }

    /** Opciones de una pregunta, incluida cuál es la correcta. Solo para el docente dueño. */
    @GetMapping("/preguntas/{idPregunta}/opciones")
    public ResponseEntity<List<CasoContenidoResponse.OpcionContenido>> opciones(@PathVariable("idCaso") String id,
            @PathVariable("idPregunta") String elemento) {
        UUID buscado = IdentificadorCanonico.uuid(elemento);
        return responder(id, caso -> CasoContenidoResponse.opcionesDe(preguntaDe(caso, buscado)));
    }

    private <T> ResponseEntity<T> responder(String id, Function<Caso, T> proyeccion) {
        if (!(concurrency instanceof CasoEtagConcurrencyContract contract)) {
            throw new CasoGestionController.HttpContractUnavailable();
        }
        CasoUpdateResult result = read.consultarContenido(id);
        T body = proyeccion.apply(result.caso());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ETAG, contract.etag(result.fingerprint())).body(body);
    }

    private static Escenario escenarioDe(Caso caso, UUID id) {
        return caso.escenarios().stream().filter(e -> id.toString().equalsIgnoreCase(e.id())).findFirst()
                .orElseThrow(CasoContenidoConsultaController::ajeno);
    }

    private static Pregunta preguntaDe(Caso caso, UUID id) {
        return caso.escenarios().stream().flatMap(e -> e.preguntas().stream())
                .filter(p -> id.toString().equalsIgnoreCase(p.id())).findFirst()
                .orElseThrow(CasoContenidoConsultaController::ajeno);
    }

    private static CasoDomainException ajeno() {
        return new CasoDomainException(ELEMENTO_AJENO, "Element does not belong to this case");
    }
}
