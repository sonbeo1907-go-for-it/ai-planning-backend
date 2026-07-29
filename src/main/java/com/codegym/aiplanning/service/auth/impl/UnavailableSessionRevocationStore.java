package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.service.auth.SessionRevocationStore;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.security.session",
        name = "redis-enabled",
        havingValue = "false")
public class UnavailableSessionRevocationStore implements SessionRevocationStore {

    @Override
    public boolean isSessionRevoked(UUID sessionId) {
        return false;
    }

    @Override
    public boolean isAccessTokenRevoked(String tokenId) {
        return false;
    }

    @Override
    public void markSessionRevoked(UUID sessionId, Duration ttl) {}

    @Override
    public void markAccessTokenRevoked(String tokenId, Duration ttl) {}

    @Override
    public LockState acquireRefreshLock(String tokenHash, String owner, Duration ttl) {
        return LockState.UNAVAILABLE;
    }

    @Override
    public void releaseRefreshLock(String tokenHash, String owner) {}
}
