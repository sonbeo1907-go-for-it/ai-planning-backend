package com.codegym.aiplanning.service.auth;

import java.time.Duration;
import java.util.UUID;

public interface SessionRevocationStore {

    enum LockState {
        ACQUIRED,
        BUSY,
        UNAVAILABLE
    }

    boolean isSessionRevoked(UUID sessionId);

    boolean isAccessTokenRevoked(String tokenId);

    void markSessionRevoked(UUID sessionId, Duration ttl);

    void markAccessTokenRevoked(String tokenId, Duration ttl);

    LockState acquireRefreshLock(String tokenHash, String owner, Duration ttl);

    void releaseRefreshLock(String tokenHash, String owner);
}
