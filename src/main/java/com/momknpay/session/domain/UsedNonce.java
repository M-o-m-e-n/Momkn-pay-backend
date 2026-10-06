package com.momknpay.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * A payload nonce that has been consumed. The primary key makes every nonce single-use.
 *
 * <p>Implements {@link Persistable} so {@code save} always INSERTs: a duplicate nonce must fail
 * with a key violation, never be merged into the existing row.
 */
@Entity
@Table(name = "used_nonces")
public class UsedNonce implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 32)
    private String nonce;

    @Column(name = "session_id", nullable = false, updatable = false)
    private String sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UsedNonce() {}

    public UsedNonce(String nonce, String sessionId, Instant createdAt) {
        this.nonce = nonce;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    @Override
    public String getId() {
        return nonce;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
