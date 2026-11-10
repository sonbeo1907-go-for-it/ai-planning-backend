package com.codegym.aiplanning.service.ai.secret;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentAiSecretStoreTest {

    @Test
    void resolvesOnlyNonBlankEnvironmentReferences() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DEEPSEEK_API_KEY", "test-secret");
        EnvironmentAiSecretStore store = new EnvironmentAiSecretStore(environment);

        assertThat(store.resolve("env:DEEPSEEK_API_KEY")).contains("test-secret");
        assertThat(store.resolve("env:MISSING_API_KEY")).isEmpty();
        assertThat(store.resolve("plain-text-secret")).isEmpty();
        assertThat(store.resolve(null)).isEmpty();
    }
}
