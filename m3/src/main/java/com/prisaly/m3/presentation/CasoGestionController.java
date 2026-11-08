package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.*;
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

    public CasoGestionController(CasoCurrentUserPort identity, CasoRepositoryPort repository,
            Optional<AutorizacionConsultaCasoPort> readPolicy, Optional<CaseAuthorizationPort> writePolicy,
            Optional<CasoPatchConcurrencyContract> concurrency) {
        read = new CasoDocenteConsultaService(identity, readPolicy.orElse(null), repository);
        update = new CasoPersistentUpdateService(identity, writePolicy.orElse(null), repository);
        this.concurrency = concurrency.orElse(null);
    }

    @GetMapping(value = "/{idCaso}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CasoDocenteConsultaResponse get(@PathVariable("idCaso") String id) {
        return CasoDocenteConsultaResponse.from(read.consultar(id));
    }

    @PatchMapping(value = "/{idCaso}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> patch(@PathVariable("idCaso") String value,
            @RequestHeader HttpHeaders headers, @RequestBody String body) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) { throw new IllegalArgumentException("Invalid UUID"); }
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
}
