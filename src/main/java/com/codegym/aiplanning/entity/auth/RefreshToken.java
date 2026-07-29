package com.codegym.aiplanning.entity.auth;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuthSession session;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    protected RefreshToken() {}

    public static RefreshToken create(
            AuthSession session, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.session = session;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public AuthSession getSession() {
        return session;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now, UUID replacementId) {
        consumedAt = now;
        replacedByTokenId = replacementId;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
