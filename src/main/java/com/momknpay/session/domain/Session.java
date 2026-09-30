package com.momknpay.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A crypto session. Holds the AES-256 payload key, wrapped with the master key — the raw key is
 * never stored.
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id private String id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "wrapped_key", nullable = false, updatable = false)
    private byte[] wrappedKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Session() {}

    public Session(
            String id, String userId, byte[] wrappedKey, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.wrappedKey = wrappedKey.clone();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean ownedBy(String candidateUserId) {
        return userId.equals(candidateUserId);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Idempotent: revoking twice keeps the first revocation time. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public byte[] getWrappedKey() {
        return wrappedKey.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
