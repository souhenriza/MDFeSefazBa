package com.souhenriza.sefazba.domain.model.sefaz;

import java.time.Instant;

/**
 * Value Object representing an authenticated SEFAZ session.
 * Immutable — a new login produces a new instance.
 */
public record SefazSession(
        SefazCredentials credentialsUsed,
        String sessionToken,
        Instant authenticatedAt,
        Instant expiresAt,
        boolean active
) {
    public SefazSession {
        if (credentialsUsed == null) {
            throw new IllegalArgumentException("Credentials must not be null");
        }
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new IllegalArgumentException("Session token must not be blank");
        }
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return active && !isExpired();
    }
}
