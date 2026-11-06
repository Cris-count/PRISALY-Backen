package com.prisaly.m3.presentation;

import com.prisaly.m3.application.port.CasoCurrentUserPort;
import com.prisaly.m3.application.service.CasoApplicationService;
import com.prisaly.m3.presentation.dto.CrearCasoRequest;
import com.prisaly.m3.presentation.dto.CrearCasoResponse;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/casos")
public final class CasoCreationController {
    private final CasoApplicationService service;
    private final CasoCurrentUserPort identity;

    public CasoCreationController(CasoApplicationService service, CasoCurrentUserPort identity) {
        this.service = Objects.requireNonNull(service);
        this.identity = Objects.requireNonNull(identity);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CrearCasoResponse> create(@RequestBody String body) {
        var owner = Objects.requireNonNull(identity.currentUserId());
        var request = CrearCasoRequest.fromJson(body);
        var draft = Objects.requireNonNull(service.createDraft(owner.toString(), request.toBuilder(owner)));
        // The application use case has committed before returning; no reread is performed.
        return ResponseEntity.status(201).body(new CrearCasoResponse(request.idCaso(), draft.estado().name()));
    }
}
