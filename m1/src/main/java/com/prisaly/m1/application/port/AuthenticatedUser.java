package com.prisaly.m1.application.port;

import java.util.Set;
import java.util.UUID;

/**
 * Identity context exposed to other modules. Never includes password hashes.
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        String displayName,
        String roleName,
        String accountStatus,
        Set<String> permissionCodes
) {
}
