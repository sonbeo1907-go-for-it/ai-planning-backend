package com.codegym.aiplanning.service.auth;

public interface PasswordResetRateLimiter {
    boolean isAllowedByEmail(String email);
    boolean isAllowedByIp(String ip);
}
