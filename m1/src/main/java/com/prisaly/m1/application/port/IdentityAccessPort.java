package com.prisaly.m1.application.port;

import java.util.UUID;

/**
 * Shared M1 contract. Other modules must not depend on Spring Security types or JPA entities.
 * Resource ownership remains the responsibility of M3–M6.
 */
public interface IdentityAccessPort {

    /**
     * Authenticated user of the current HTTP request, when present.
     */
    AuthenticatedUser currentUser();

    /**
     * Load identity by user id (usable outside an HTTP security context).
     */
    AuthenticatedUser requireUser(UUID userId);

    boolean hasPermission(UUID userId, String permissionCode);

    boolean hasRole(UUID userId, String roleName);
}
