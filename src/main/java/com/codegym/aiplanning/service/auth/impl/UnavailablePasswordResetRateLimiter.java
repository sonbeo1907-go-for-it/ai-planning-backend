package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.service.auth.PasswordResetRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.security.session",
        name = "redis-enabled",
        havingValue = "false")
public class UnavailablePasswordResetRateLimiter implements PasswordResetRateLimiter {

    @Override
    public boolean isAllowedByEmail(String email) {
        return true;
    }

    @Override
    public boolean isAllowedByIp(String ip) {
        return true;
    }
}
