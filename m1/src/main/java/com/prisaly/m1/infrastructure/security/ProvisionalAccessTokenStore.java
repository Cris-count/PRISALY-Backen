package com.prisaly.m1.infrastructure.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TD-M1-AUTH-PROVISIONAL: in-memory opaque tokens. Lost on JVM restart.
 */
@Component
public class ProvisionalAccessTokenStore {

    public static final long DEFAULT_TTL_SECONDS = 8L * 60L * 60L;
    public static final int DEFAULT_CAPACITY = 10_000;

    private final SecureRandom secureRandom;
    private final Map<String, TokenRecord> tokensByValue;
    private final long ttlSeconds;
    private final Clock clock;
    private final int capacity;

    @Autowired
    public ProvisionalAccessTokenStore() {
        this(Clock.systemUTC(), DEFAULT_TTL_SECONDS);
    }

    // The clock makes expiry verifiable; production uses the system clock and the 8 h TTL.
    public ProvisionalAccessTokenStore(Clock clock, long ttlSeconds) {
        this(clock, ttlSeconds, DEFAULT_CAPACITY);
    }

    public ProvisionalAccessTokenStore(Clock clock, long ttlSeconds, int capacity) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Token TTL must be positive");
        }
        this.secureRandom = new SecureRandom();
        if (capacity < 1) {
            throw new IllegalArgumentException("Token capacity must be positive");
        }
        this.tokensByValue = new HashMap<>();
        this.ttlSeconds = ttlSeconds;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.capacity = capacity;
    }

    public synchronized String issue(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        purgeExpired();
        if (tokensByValue.size() >= capacity) {
            throw new CapacityExceededException();
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plusSeconds(ttlSeconds);
        tokensByValue.put(token, new TokenRecord(userId, expiresAt));
        return token;
    }

    public synchronized Optional<UUID> resolveActiveUserId(String token) {
        purgeExpired();
        TokenRecord record = tokensByValue.get(token);
        if (record == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(record.expiresAt())) {
            tokensByValue.remove(token);
            return Optional.empty();
        }
        return Optional.of(record.userId());
    }

    public synchronized void revoke(String token) {
        tokensByValue.remove(token);
    }

    /** Explicit maintenance entry point; issuance and lookup also reclaim abandoned tokens. */
    public synchronized int purgeExpired() {
        Instant now = clock.instant();
        int before = tokensByValue.size();
        tokensByValue.values().removeIf(record -> !now.isBefore(record.expiresAt()));
        return before - tokensByValue.size();
    }

    private record TokenRecord(UUID userId, Instant expiresAt) {
    }

    public static final class CapacityExceededException extends RuntimeException {
        public CapacityExceededException() { super("Provisional token capacity reached"); }
    }
}
