package com.codegym.aiplanning.service.ai.secret;

import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EnvironmentAiSecretStore implements AiSecretStore {

    private static final String ENV_REFERENCE_PREFIX = "env:";

    private final Environment environment;

    public EnvironmentAiSecretStore(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolve(String secretRef) {
        if (!StringUtils.hasText(secretRef)
                || !secretRef.startsWith(ENV_REFERENCE_PREFIX)) {
            return Optional.empty();
        }

        String variableName = secretRef.substring(ENV_REFERENCE_PREFIX.length());
        String secret = environment.getProperty(variableName);
        return StringUtils.hasText(secret) ? Optional.of(secret) : Optional.empty();
    }
}
