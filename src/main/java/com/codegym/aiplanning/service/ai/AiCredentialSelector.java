package com.codegym.aiplanning.service.ai;

import java.util.Optional;
import java.util.UUID;

public interface AiCredentialSelector {

    Optional<ResolvedAiCredential> findFirstAvailable(UUID providerId);
}
