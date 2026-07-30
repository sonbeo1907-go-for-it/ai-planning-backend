package com.codegym.aiplanning.entity.auth;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auth_sessions")
public class AuthSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 50)
    private String revokeReason;

    protected AuthSession() {}

    public static AuthSession create(UserAccount user, Instant expiresAt) {
        AuthSession session = new AuthSession();
        session.user = user;
        session.status = AuthSessionStatus.ACTIVE;
        session.expiresAt = expiresAt;
        return session;
    }

    public UserAccount getUser() {
        return user;
    }

    public AuthSessionStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isActive(Instant now) {
        return status == AuthSessionStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void revoke(Instant now, String reason) {
        if (status == AuthSessionStatus.REVOKED) {
            return;
        }
        status = AuthSessionStatus.REVOKED;
        revokedAt = now;
        revokeReason = reason;
    }
}
