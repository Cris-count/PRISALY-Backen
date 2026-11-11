package com.prisaly.m1.presentation;

import com.prisaly.m1.api.IdentityDomainException;
import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.application.service.IdentityCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DM1-08 (DC-M1-04): change of the own password, where the policy is applied. Extension pending conciliation with
 * ARQ-008.3 (like /personal/me). Existing tokens are not revoked by this operation (documented limitation).
 */
@RestController
@RequestMapping("/v1/personal")
public class PersonalContrasenaController {
    private final IdentityAccessPort identidad;
    private final IdentityCatalogService catalogo;

    public PersonalContrasenaController(IdentityAccessPort identidad, IdentityCatalogService catalogo) {
        this.identidad = identidad;
        this.catalogo = catalogo;
    }

    public record CambioContrasenaRequest(@NotNull String actual, @NotNull String nueva) { }

    @PutMapping("/contrasena")
    public ResponseEntity<Void> cambiar(@Valid @RequestBody CambioContrasenaRequest request) {
        AuthenticatedUser actual = identidad.currentUser();
        if (actual == null || actual.userId() == null) {
            throw new IdentityDomainException("UNAUTHENTICATED", "Authentication required");
        }
        catalogo.cambiarContrasena(actual.userId(), request.actual(), request.nueva());
        return ResponseEntity.noContent().build();
    }
}
