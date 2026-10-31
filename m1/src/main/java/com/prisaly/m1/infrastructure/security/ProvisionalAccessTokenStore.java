package com.prisaly.m1.infrastructure.security;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * TD-M1-AUTH-PROVISIONAL: in-memory opaque tokens. Lost on JVM restart.
 */
@Component
public class ProvisionalAccessTokenStore {

    public static final long DEFAULT_TTL_SECONDS = 8L * 60L * 60L;

    private final SecureRandom secureRandom;
    private final Map<String, TokenRecord> tokensByValue;
    private final long ttlSeconds;

    public ProvisionalAccessTokenStore() {
        this.secureRandom = new SecureRandom();
        this.tokensByValue = new ConcurrentHashMap<>();
        this.ttlSeconds = DEFAULT_TTL_SECONDS;
    }

    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        tokensByValue.put(token, new TokenRecord(userId, expiresAt));
        return token;
    }

    public Optional<UUID> resolveActiveUserId(String token) {
        TokenRecord record = tokensByValue.get(token);
        if (record == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(record.expiresAt())) {
            tokensByValue.remove(token);
            return Optional.empty();
        }
        return Optional.of(record.userId());
    }

    public void revoke(String token) {
        tokensByValue.remove(token);
    }

    private record TokenRecord(UUID userId, Instant expiresAt) {
    }
}
