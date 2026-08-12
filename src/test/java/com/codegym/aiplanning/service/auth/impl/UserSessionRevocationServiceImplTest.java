package com.codegym.aiplanning.service.auth.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.AuthSession;
import com.codegym.aiplanning.entity.auth.AuthSessionStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.AuthSessionRepository;
import com.codegym.aiplanning.repository.auth.RefreshTokenRepository;
import com.codegym.aiplanning.service.auth.SessionRevocationStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class UserSessionRevocationServiceImplTest {

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private SessionRevocationStore revocationStore;

    private UserSessionRevocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserSessionRevocationServiceImpl(
                authSessionRepository, refreshTokenRepository, revocationStore);
    }

    @Test
    void revokeAllActiveSessionsRevokesDatabaseStateAndCachesEverySessionId() {
        UUID userId = UUID.randomUUID();
        AuthSession first = activeSession("first-user", Duration.ofDays(2));
        AuthSession second = activeSession("second-user", Duration.ofDays(3));
        when(authSessionRepository.findAllActiveByUserIdForUpdate(
                        eq(userId), eq(AuthSessionStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(first, second));

        TransactionSynchronizationManager.initSynchronization();
        int revoked;
        try {
            revoked = service.revokeAllActiveSessions(userId, "ACCOUNT_DEACTIVATED");

            verify(revocationStore, never())
                    .markSessionRevoked(any(UUID.class), any(Duration.class));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(revoked).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(second.getStatus()).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(first.getRevokeReason()).isEqualTo("ACCOUNT_DEACTIVATED");
        verify(refreshTokenRepository).revokeAllBySessionIds(
                eq(List.of(first.getId(), second.getId())), any(Instant.class));
        verify(revocationStore).markSessionRevoked(eq(first.getId()), any(Duration.class));
        verify(revocationStore).markSessionRevoked(eq(second.getId()), any(Duration.class));
    }

    @Test
    void revokeAllActiveSessionsDoesNothingWhenUserHasNoActiveSession() {
        UUID userId = UUID.randomUUID();
        when(authSessionRepository.findAllActiveByUserIdForUpdate(
                        eq(userId), eq(AuthSessionStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(service.revokeAllActiveSessions(userId, "ACCOUNT_DEACTIVATED"))
                .isZero();

        verify(refreshTokenRepository, never())
                .revokeAllBySessionIds(any(), any(Instant.class));
        verify(revocationStore, never())
                .markSessionRevoked(any(UUID.class), any(Duration.class));
    }

    private AuthSession activeSession(String emailAlias, Duration lifetime) {
        UserAccount account = UserAccount.create(
                emailAlias + "@example.com",
                "password-hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
        AuthSession session = AuthSession.create(account, Instant.now().plus(lifetime));
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }
}
