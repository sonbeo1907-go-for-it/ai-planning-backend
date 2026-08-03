package com.codegym.aiplanning.service.auth;

import java.util.UUID;

public interface UserSessionRevocationService {

    int revokeAllActiveSessions(UUID userId, String reason);
}
