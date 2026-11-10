package com.codegym.aiplanning.service.ai.secret;

import java.util.Optional;

public interface AiSecretStore {

    Optional<String> resolve(String secretRef);
}
