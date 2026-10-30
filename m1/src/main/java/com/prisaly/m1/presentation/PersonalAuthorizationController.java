package com.prisaly.m1.presentation;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.port.IdentityAccessPort;
import com.prisaly.m1.domain.rule.IdentityDomainException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Probe endpoint for shared permission checks via {@link IdentityAccessPort}.
 * Other modules must call the port, not this HTTP path, in production integrations.
 */
@RestController
@RequestMapping("/v1/personal")
public class PersonalAuthorizationController {

    public static final String PERMISSION_CREAR_CASO = "CREAR_CASO";
    public static final String CODE_FORBIDDEN = "FORBIDDEN";

    private final IdentityAccessPort identityAccessPort;

    public PersonalAuthorizationController(IdentityAccessPort identityAccessPort) {
        this.identityAccessPort = identityAccessPort;
    }

    @GetMapping("/autorizacion/crear-caso")
    public Map<String, Object> requireCrearCaso() {
        AuthenticatedUser user = identityAccessPort.currentUser();
        if (!identityAccessPort.hasPermission(user.userId(), PERMISSION_CREAR_CASO)) {
            throw new IdentityDomainException(CODE_FORBIDDEN, "Missing permission CREAR_CASO");
        }
        return Map.of(
                "allowed", true,
                "permission", PERMISSION_CREAR_CASO,
                "userId", user.userId().toString());
    }
}
