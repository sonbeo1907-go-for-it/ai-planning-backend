package com.codegym.aiplanning.entity.auth;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends BaseEntity {

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "login_blocked_until")
    private Instant loginBlockedUntil;

    protected UserAccount() {}

    public static UserAccount create(
            String email,
            String passwordHash,
            UserRole role,
            AccountStatus status) {
        UserAccount account = new UserAccount();
        account.email = normalizeEmail(email);
        account.passwordHash = passwordHash;
        account.role = role;
        account.status = status;
        account.failedLoginAttempts = 0;
        return account;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isLocked() {
        return status == AccountStatus.LOCKED;
    }

    public boolean isLoginBlocked(Instant now) {
        return loginBlockedUntil != null && loginBlockedUntil.isAfter(now);
    }

    public void recordFailedLogin(Instant now, int maxAttempts, Duration blockDuration) {
        if (!isActive() || isLocked() || isLoginBlocked(now)) {
            return;
        }

        if (loginBlockedUntil != null) {
            failedLoginAttempts = 0;
            loginBlockedUntil = null;
        }

        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            loginBlockedUntil = now.plus(blockDuration);
        }
    }

    public void clearLoginFailures() {
        failedLoginAttempts = 0;
        loginBlockedUntil = null;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLoginBlockedUntil() {
        return loginBlockedUntil;
    }

    public void changeEmail(String newEmail) {
        if (newEmail != null && !newEmail.isBlank()) {
            this.email = normalizeEmail(newEmail);
        }
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash != null && !newPasswordHash.isBlank()) {
            this.passwordHash = newPasswordHash;
            clearLoginFailures();
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
