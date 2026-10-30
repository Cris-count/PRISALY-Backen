package com.prisaly.m1.presentation;

import com.prisaly.m1.application.port.AuthenticatedUser;
import com.prisaly.m1.application.service.PersonnelAuthenticationService;
import com.prisaly.m1.presentation.dto.PersonalAutenticarRequest;
import com.prisaly.m1.presentation.dto.PersonalAutenticarResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/personal")
public class PersonalAuthController {

    private final PersonnelAuthenticationService personnelAuthenticationService;

    public PersonalAuthController(PersonnelAuthenticationService personnelAuthenticationService) {
        this.personnelAuthenticationService = personnelAuthenticationService;
    }

    @PostMapping("/autenticar")
    public PersonalAutenticarResponse autenticar(@Valid @RequestBody PersonalAutenticarRequest request) {
        PersonnelAuthenticationService.AuthResult result =
                personnelAuthenticationService.authenticate(request.correo(), request.password());
        AuthenticatedUser user = result.user();
        return new PersonalAutenticarResponse(
                result.accessToken(),
                "Bearer",
                user.userId(),
                user.email(),
                user.displayName(),
                user.roleName(),
                user.permissionCodes(),
                "TD-M1-AUTH-PROVISIONAL");
    }

    /**
     * Logout is a proposed extension pending Architecture conciliation (not in the core ARQ-008.3 pair).
     */
    @PostMapping("/cerrar-sesion")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            personnelAuthenticationService.logout(header.substring("Bearer ".length()).trim());
        }
        return ResponseEntity.noContent().build();
    }
}
