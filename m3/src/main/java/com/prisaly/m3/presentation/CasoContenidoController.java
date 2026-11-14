package com.prisaly.m3.presentation;

import com.prisaly.m3.application.service.CasoContenidoService;
import com.prisaly.m3.application.update.CasoUpdateCommand;
import com.prisaly.m3.presentation.dto.CasoContenidoRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adds scenarios, questions and options to a draft case. Routes are a sprint proposal: no
 * architecture contract defines them yet. Each response carries the new ETag for the next write.
 */
@RestController
@RequestMapping("/v1/casos/{idCaso}")
public final class CasoContenidoController {
    private final CasoContenidoService service;
    private final CasoPatchConcurrencyContract concurrency;

    public CasoContenidoController(CasoContenidoService service, Optional<CasoPatchConcurrencyContract> concurrency) {
        this.service = service;
        this.concurrency = concurrency.orElse(null);
    }

    /** Agrega un escenario al borrador. Exige el ETag vigente en If-Match. */
    @PostMapping(value = "/escenarios", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> escenario(@PathVariable("idCaso") String caso,
            @RequestHeader HttpHeaders headers, @RequestBody String body) {
        return ejecutar(caso, headers, () -> CasoContenidoRequest.escenario(body));
    }

    /** Agrega una pregunta a un escenario del borrador. */
    @PostMapping(value = "/escenarios/{idEscenario}/preguntas", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> pregunta(@PathVariable("idCaso") String caso,
            @PathVariable("idEscenario") String escenario, @RequestHeader HttpHeaders headers,
            @RequestBody String body) {
        UUID idEscenario = IdentificadorCanonico.uuid(escenario);
        return ejecutar(caso, headers, () -> CasoContenidoRequest.pregunta(idEscenario, body));
    }

    /** Agrega una opción a una pregunta. La publicación exige exactamente una correcta. */
    @PostMapping(value = "/preguntas/{idPregunta}/opciones", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> opcion(@PathVariable("idCaso") String caso,
            @PathVariable("idPregunta") String pregunta, @RequestHeader HttpHeaders headers,
            @RequestBody String body) {
        UUID idPregunta = IdentificadorCanonico.uuid(pregunta);
        return ejecutar(caso, headers, () -> CasoContenidoRequest.opcion(idPregunta, body));
    }

    private ResponseEntity<Map<String, String>> ejecutar(String caso, HttpHeaders headers,
            Supplier<CasoUpdateCommand> command) {
        UUID id = IdentificadorCanonico.uuid(caso);
        CasoUpdateCommand[] applied = new CasoUpdateCommand[1];
        var result = service.aplicar(id, () -> {
            if (concurrency == null) { throw new CasoGestionController.HttpContractUnavailable(); }
            return concurrency.expectedFingerprint(headers);
        }, () -> applied[0] = command.get());
        var response = ResponseEntity.status(HttpStatus.CREATED);
        if (concurrency instanceof CasoEtagConcurrencyContract contract) {
            response.header(HttpHeaders.ETAG, contract.etag(result.fingerprint()));
        }
        return response.body(Map.of("id", idDe(applied[0]).toString()));
    }

    private static UUID idDe(CasoUpdateCommand command) {
        return switch (command) {
            case CasoUpdateCommand.AgregarEscenarioCommand c -> c.idEscenario();
            case CasoUpdateCommand.AgregarPreguntaCommand c -> c.idPregunta();
            case CasoUpdateCommand.AgregarOpcionRespuestaCommand c -> c.idOpcion();
            default -> throw new IllegalStateException("Unsupported content command");
        };
    }
}
