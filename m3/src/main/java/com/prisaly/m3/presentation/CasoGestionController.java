package com.prisaly.m3.presentation;

import com.prisaly.m3.application.service.*;
import com.prisaly.m3.presentation.dto.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** APP supplies the /api context path. Missing policies never grant access. */
@RestController
@RequestMapping("/v1/casos")
public final class CasoGestionController {
    private final CasoDocenteConsultaService read;
    private final CasoPersistentUpdateService update;
    private final CasoPatchConcurrencyContract concurrency;

    public CasoGestionController(CasoDocenteConsultaService read, CasoPersistentUpdateService update,
            Optional<CasoPatchConcurrencyContract> concurrency) {
        this.read = read;
        this.update = update;
        this.concurrency = concurrency.orElse(null);
    }

    /** Ficha del caso para su docente. Incluye el ETag que las escrituras deben devolver. */
    @GetMapping(value = "/{idCaso}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CasoDocenteConsultaResponse> get(@PathVariable("idCaso") String id) {
        if (concurrency instanceof CasoEtagConcurrencyContract contract) {
            var result = read.consultarConVersion(id);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.ETAG, contract.etag(result.fingerprint()))
                    .body(CasoDocenteConsultaResponse.from(result.ficha()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CasoDocenteConsultaResponse.from(read.consultar(id)));
    }

    /** Actualiza campos del borrador si el If-Match coincide con la versión guardada. */
    @PatchMapping(value = "/{idCaso}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> patch(@PathVariable("idCaso") String value,
            @RequestHeader HttpHeaders headers, @RequestBody String body) {
        UUID id = IdentificadorCanonico.uuid(value);
        // Decoding/precondition resolution occurs inside the protected application use case.
        update.updateFieldsAuthorized(id, () -> {
            if (concurrency == null) { throw new HttpContractUnavailable(); }
            String fingerprint = concurrency.expectedFingerprint(headers);
            if (fingerprint == null || !fingerprint.matches("m3-v2:[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid fingerprint");
            }
            return fingerprint;
        }, stored -> CasoPatchRequest.fromJson(body).apply(stored));
        return ResponseEntity.noContent().build();
    }

    public static final class HttpContractUnavailable extends RuntimeException { }

    /** D9: the request carries no If-Match validator at all. */
    public static final class IfMatchRequired extends RuntimeException { }
}
