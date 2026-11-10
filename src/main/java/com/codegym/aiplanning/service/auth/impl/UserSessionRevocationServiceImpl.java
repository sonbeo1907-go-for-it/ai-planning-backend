package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.entity.auth.AuthSession;
import com.codegym.aiplanning.entity.auth.AuthSessionStatus;
import com.codegym.aiplanning.repository.auth.AuthSessionRepository;
import com.codegym.aiplanning.repository.auth.RefreshTokenRepository;
import com.codegym.aiplanning.service.auth.SessionRevocationStore;
import com.codegym.aiplanning.service.auth.UserSessionRevocationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserSessionRevocationServiceImpl implements UserSessionRevocationService {

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRevocationStore revocationStore;

    public UserSessionRevocationServiceImpl(
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            SessionRevocationStore revocationStore) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.revocationStore = revocationStore;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAllActiveSessions(UUID userId, String reason) {
        Instant now = Instant.now();
        List<AuthSession> sessions = authSessionRepository.findAllActiveByUserIdForUpdate(
                userId, AuthSessionStatus.ACTIVE, now);
        if (sessions.isEmpty()) {
            return 0;
        }

        sessions.forEach(session -> session.revoke(now, reason));
        refreshTokenRepository.revokeAllBySessionIds(
                sessions.stream().map(AuthSession::getId).toList(), now);
        cacheRevokedSessionsAfterCommit(sessions);
        return sessions.size();
    }

    private void cacheRevokedSessionsAfterCommit(List<AuthSession> sessions) {
        List<RevokedSession> revokedSessions = sessions.stream()
                .map(session -> new RevokedSession(session.getId(), session.getExpiresAt()))
                .toList();
        Runnable cacheRevocations = () -> {
            Instant cacheTime = Instant.now();
            revokedSessions.forEach(session -> {
                Duration ttl = Duration.between(cacheTime, session.expiresAt());
                if (!ttl.isNegative() && !ttl.isZero()) {
                    revocationStore.markSessionRevoked(session.id(), ttl);
                }
            });
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cacheRevocations.run();
                        }
                    });
        } else {
            cacheRevocations.run();
        }
    }

    private record RevokedSession(UUID id, Instant expiresAt) {}
}
