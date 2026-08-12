package com.codegym.aiplanning.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccountLoginAttemptTest {

    @Test
    void expiredTemporaryBlockStartsANewFailureWindow() {
        UserAccount account = UserAccount.create(
                "user",
                "user@example.com",
                "password-hash",
                "User",
                UserRole.USER,
                AccountStatus.ACTIVE);
        Instant initialAttempt = Instant.parse("2026-01-01T00:00:00Z");
        Duration blockDuration = Duration.ofMinutes(15);

        account.recordFailedLogin(initialAttempt, 3, blockDuration);
        account.recordFailedLogin(initialAttempt.plusSeconds(1), 3, blockDuration);
        account.recordFailedLogin(initialAttempt.plusSeconds(2), 3, blockDuration);

        assertThat(account.getLoginBlockedUntil())
                .isEqualTo(initialAttempt.plusSeconds(2).plus(blockDuration));

        account.recordFailedLogin(initialAttempt.plus(Duration.ofMinutes(16)), 3, blockDuration);

        assertThat(account.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(account.getLoginBlockedUntil()).isNull();
    }
}
