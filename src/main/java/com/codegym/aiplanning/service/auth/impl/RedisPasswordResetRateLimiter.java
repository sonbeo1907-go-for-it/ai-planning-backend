package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.service.auth.PasswordResetRateLimiter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.security.session",
        name = "redis-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RedisPasswordResetRateLimiter implements PasswordResetRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisPasswordResetRateLimiter.class);
    private static final String PREFIX = "ai-planning:auth:rate_limit:pwd_reset:";
    private static final Duration EMAIL_COOLDOWN = Duration.ofMinutes(3);
    private static final Duration IP_WINDOW = Duration.ofHours(1);
    private static final long IP_MAX_REQUESTS = 20;

    private final StringRedisTemplate redis;

    public RedisPasswordResetRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isAllowedByEmail(String email) {
        String key = PREFIX + "email:" + email;
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", EMAIL_COOLDOWN);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException exception) {
            logRedisFailure("rate limit by email", exception);
            // Allow if Redis fails, to prevent blocking users when cache is down
            return true;
        }
    }

    @Override
    public boolean isAllowedByIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        String key = PREFIX + "ip:" + ip;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, IP_WINDOW);
            }
            return count != null && count <= IP_MAX_REQUESTS;
        } catch (RuntimeException exception) {
            logRedisFailure("rate limit by IP", exception);
            return true;
        }
    }

    private void logRedisFailure(String operation, RuntimeException exception) {
        if (exception instanceof RedisConnectionFailureException
                || exception.getCause() instanceof RedisConnectionFailureException) {
            log.warn("Redis unavailable while attempting to {}. Falling back to allow.", operation);
        } else {
            log.warn("Redis error while attempting to {}. Falling back to allow.", operation, exception);
        }
    }
}
