package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.service.auth.SessionRevocationStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.security.session",
        name = "redis-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RedisSessionRevocationStore implements SessionRevocationStore {

    private static final Logger log =
            LoggerFactory.getLogger(RedisSessionRevocationStore.class);
    private static final String PREFIX = "ai-planning:auth:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisSessionRevocationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isSessionRevoked(UUID sessionId) {
        return hasKey(sessionKey(sessionId));
    }

    @Override
    public boolean isAccessTokenRevoked(String tokenId) {
        return hasKey(tokenKey(tokenId));
    }

    @Override
    public void markSessionRevoked(UUID sessionId, Duration ttl) {
        set(sessionKey(sessionId), ttl);
    }

    @Override
    public void markAccessTokenRevoked(String tokenId, Duration ttl) {
        set(tokenKey(tokenId), ttl);
    }

    @Override
    public LockState acquireRefreshLock(String tokenHash, String owner, Duration ttl) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(lockKey(tokenHash), owner, ttl);
            return Boolean.TRUE.equals(acquired) ? LockState.ACQUIRED : LockState.BUSY;
        } catch (RuntimeException exception) {
            logRedisFailure("acquire refresh lock", exception);
            return LockState.UNAVAILABLE;
        }
    }

    @Override
    public void releaseRefreshLock(String tokenHash, String owner) {
        try {
            redis.execute(RELEASE_SCRIPT, List.of(lockKey(tokenHash)), owner);
        } catch (RuntimeException exception) {
            logRedisFailure("release refresh lock", exception);
        }
    }

    private boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (RuntimeException exception) {
            logRedisFailure("read revocation", exception);
            return false;
        }
    }

    private void set(String key, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException exception) {
            logRedisFailure("write revocation", exception);
        }
    }

    private void logRedisFailure(String operation, RuntimeException exception) {
        if (exception instanceof RedisConnectionFailureException
                || exception.getCause() instanceof RedisConnectionFailureException) {
            log.warn("Redis unavailable while attempting to {}; PostgreSQL remains authoritative",
                    operation);
        } else {
            log.warn("Redis error while attempting to {}; PostgreSQL remains authoritative",
                    operation, exception);
        }
    }

    private String sessionKey(UUID sessionId) {
        return PREFIX + "revoked:session:" + sessionId;
    }

    private String tokenKey(String tokenId) {
        return PREFIX + "revoked:jti:" + tokenId;
    }

    private String lockKey(String tokenHash) {
        return PREFIX + "refresh-lock:" + tokenHash;
    }
}
