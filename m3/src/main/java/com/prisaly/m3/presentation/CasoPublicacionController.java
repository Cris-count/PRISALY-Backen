package com.prisaly.m3.presentation;

import com.prisaly.m3.application.service.CasoTransicionService;
import com.prisaly.m3.application.update.CasoUpdateResult;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PD-02: Borrador to Publicado and Publicado to Archivado. Routes are a technical proposal. */
@RestController
@RequestMapping(value = "/v1/casos/{idCaso}", produces = MediaType.APPLICATION_JSON_VALUE)
public final class CasoPublicacionController {
    private final CasoTransicionService service;
    private final CasoPatchConcurrencyContract concurrency;

    public CasoPublicacionController(CasoTransicionService service, Optional<CasoPatchConcurrencyContract> concurrency) {
        this.service = service;
        this.concurrency = concurrency.orElse(null);
    }

    @PostMapping("/publicar")
    public ResponseEntity<Map<String, String>> publicar(@PathVariable("idCaso") String caso, @RequestHeader HttpHeaders headers) {
        return responder(service.publicar(IdentificadorCanonico.uuid(caso), () -> fingerprint(headers)));
    }

    @PostMapping("/archivar")
    public ResponseEntity<Map<String, String>> archivar(@PathVariable("idCaso") String caso, @RequestHeader HttpHeaders headers) {
        return responder(service.archivar(IdentificadorCanonico.uuid(caso), () -> fingerprint(headers)));
    }

    private String fingerprint(HttpHeaders headers) {
        if (concurrency == null) { throw new CasoGestionController.HttpContractUnavailable(); }
        return concurrency.expectedFingerprint(headers);
    }

    private ResponseEntity<Map<String, String>> responder(CasoUpdateResult result) {
        var response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (concurrency instanceof CasoEtagConcurrencyContract contract) {
            response.header(HttpHeaders.ETAG, contract.etag(result.fingerprint()));
        }
        return response.body(Map.of("idCaso", result.caso().id(), "estado", result.caso().estado().name()));
    }
}
