package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.presentation.dto.CrearCasoRequest;
import com.prisaly.m3.presentation.dto.CrearCasoResponse;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/casos")
public final class CasoCreationController {
    private final CasoApplicationService service;
    private final CasoCurrentUserPort identity;
    private final TransactionTemplate creation;

    public CasoCreationController(CasoApplicationService service, CasoCurrentUserPort identity,
            PlatformTransactionManager manager) {
        this.service = Objects.requireNonNull(service);
        this.identity = Objects.requireNonNull(identity);
        creation = new TransactionTemplate(Objects.requireNonNull(manager));
        // Commit this HTTP operation before returning 201, even if a caller has a transaction.
        // INSERT joins this transaction through the existing REQUIRED writer.
        creation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CrearCasoResponse> create(@RequestBody String body) {
        var owner = Objects.requireNonNull(identity.currentUserId());
        var request = CrearCasoRequest.fromJson(body);
        var draft = Objects.requireNonNull(creation.execute(status ->
                service.createDraft(owner.toString(), request.toBuilder(owner))));
        // execute() has committed successfully here; no REQUIRES_NEW reread is performed.
        return ResponseEntity.status(201).body(new CrearCasoResponse(request.idCaso(), draft.estado().name()));
    }
}
