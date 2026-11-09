package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.AutorizacionConsultaCasoPort;
import com.prisaly.m3.application.port.CaseAuthorizationPort;
import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.port.CasoRepositoryPort;
import com.prisaly.m3.application.port.CasoUpdatePort;
import com.prisaly.m3.application.service.CasoContenidoService;
import com.prisaly.m3.application.service.CasoDocenteConsultaService;
import com.prisaly.m3.application.update.CasoUpdateResult;
import com.prisaly.m3.domain.rule.CasoDomainException;
import com.prisaly.m3.presentation.dto.CasoRubricaDto;
import com.prisaly.m3.presentation.dto.CasoRubricaDto.RubricaResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F5/PD-06: the single rubric of a case. Reads follow PD-01; writes also require a draft case. */
@RestController
@RequestMapping(value = "/v1/casos/{idCaso}/rubrica", produces = MediaType.APPLICATION_JSON_VALUE)
public final class CasoRubricaController {
    private final CasoDocenteConsultaService read;
    private final CasoContenidoService write;
    private final CasoPatchConcurrencyContract concurrency;

    public CasoRubricaController(CasoCurrentUserPort identity, CasoRepositoryPort repository,
            Optional<AutorizacionConsultaCasoPort> readPolicy, Optional<CaseAuthorizationPort> writePolicy,
            Optional<CasoUpdatePort> updates, Optional<CasoPatchConcurrencyContract> concurrency) {
        read = new CasoDocenteConsultaService(identity, readPolicy.orElse(null), repository, updates.orElse(null));
        write = new CasoContenidoService(identity, writePolicy.orElse(null), updates.orElse(null));
        this.concurrency = concurrency.orElse(null);
    }

    @GetMapping
    public ResponseEntity<RubricaResponse> consultar(@PathVariable("idCaso") String id) {
        return responder(codec(), read.consultarContenido(id));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RubricaResponse> guardar(@PathVariable("idCaso") String value,
            @RequestHeader HttpHeaders headers, @RequestBody String body) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) { throw new IllegalArgumentException("Invalid UUID"); }
        var contract = codec();
        return responder(contract, write.aplicar(id, () -> contract.expectedFingerprint(headers), () -> CasoRubricaDto.comando(body)));
    }

    private CasoEtagConcurrencyContract codec() {
        if (concurrency instanceof CasoEtagConcurrencyContract contract) { return contract; }
        throw new CasoGestionController.HttpContractUnavailable();
    }

    private static ResponseEntity<RubricaResponse> responder(CasoEtagConcurrencyContract contract, CasoUpdateResult result) {
        var rubrica = result.caso().rubrica();
        if (rubrica == null) { throw new CasoDomainException("RUBRIC_NOT_FOUND", "The case has no rubric"); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ETAG, contract.etag(result.fingerprint())).body(RubricaResponse.from(rubrica));
    }
}
