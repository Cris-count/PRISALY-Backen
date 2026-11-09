package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoPublicationPort;
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

    public CasoPublicacionController(Optional<CasoCurrentUserPort> identity, Optional<CaseAuthorizationPort> authorization,
            Optional<CasoPublicationPort> publication, Optional<CasoPatchConcurrencyContract> concurrency) {
        service = new CasoTransicionService(identity.orElse(null), authorization.orElse(null), publication.orElse(null));
        this.concurrency = concurrency.orElse(null);
    }

    @PostMapping("/publicar")
    public ResponseEntity<Map<String, String>> publicar(@PathVariable("idCaso") String caso, @RequestHeader HttpHeaders headers) {
        return responder(service.publicar(uuid(caso), () -> fingerprint(headers)));
    }

    @PostMapping("/archivar")
    public ResponseEntity<Map<String, String>> archivar(@PathVariable("idCaso") String caso, @RequestHeader HttpHeaders headers) {
        return responder(service.archivar(uuid(caso), () -> fingerprint(headers)));
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

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) { throw new IllegalArgumentException("Invalid UUID"); }
        return id;
    }
}
