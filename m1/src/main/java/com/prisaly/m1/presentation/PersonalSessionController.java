package com.prisaly.m1.presentation;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.presentation.dto.PersonalAutenticarResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proposed extension for local integration (session probe). Must be conciliated before shared contract freeze.
 */
@RestController
@RequestMapping("/v1/personal")
public class PersonalSessionController {

    private final IdentityAccessPort identityAccessPort;

    public PersonalSessionController(IdentityAccessPort identityAccessPort) {
        this.identityAccessPort = identityAccessPort;
    }

    @GetMapping("/me")
    public PersonalAutenticarResponse me() {
        AuthenticatedUser user = identityAccessPort.currentUser();
        return new PersonalAutenticarResponse(
                null,
                "Bearer",
                user.userId(),
                user.email(),
                user.displayName(),
                user.roleName(),
                user.permissionCodes(),
                "TD-M1-AUTH-PROVISIONAL");
    }
}
