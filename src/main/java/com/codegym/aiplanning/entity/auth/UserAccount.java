package com.codegym.aiplanning.entity.auth;

import com.codegym.aiplanning.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

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
            String username,
            String passwordHash,
            String fullName,
            UserRole role,
            AccountStatus status) {
        UserAccount account = new UserAccount();
        account.username = username;
        account.passwordHash = passwordHash;
        account.fullName = fullName;
        account.role = role;
        account.status = status;
        account.failedLoginAttempts = 0;
        return account;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
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

    public void updateProfile(String fullName, UserRole role, AccountStatus status) {
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName;
        }
        if (role != null) {
            this.role = role;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash != null && !newPasswordHash.isBlank()) {
            this.passwordHash = newPasswordHash;
        }
    }

    public void setStatus(AccountStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public void deactivate() {
        this.status = AccountStatus.INACTIVE;
    }
}
