package com.codegym.aiplanning.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.service.ai.provider.OpenAiCompatibleAiClient;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleAiClientTest {

    @Mock
    private AiProviderSelector providerSelector;

    @Mock
    private AiCredentialSelector credentialSelector;

    @Mock
    private AiProviderConfig config;

    @Mock
    private AiProvider provider;

    private OpenAiCompatibleAiClient client;

    @BeforeEach
    void setUp() {
        client = new OpenAiCompatibleAiClient(providerSelector, credentialSelector);
        when(config.isEnabled()).thenReturn(true);
        when(provider.isEnabled()).thenReturn(true);
    }

    @Test
    void selectsTheDefaultConfigurationForRoadmapGeneration() {
        UUID providerId = UUID.randomUUID();
        when(providerSelector.requireDefault(AiPurpose.ROADMAP_GENERATION))
                .thenReturn(config);
        when(config.getProvider()).thenReturn(provider);
        when(config.getMaxInputTokens()).thenReturn(1000);
        when(provider.getProtocol()).thenReturn(AiProviderProtocol.OPENAI_COMPATIBLE);
        when(provider.getId()).thenReturn(providerId);
        when(credentialSelector.findFirstAvailable(providerId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.generateContent(
                        AiPurpose.ROADMAP_GENERATION,
                        "System prompt",
                        "User prompt"));

        assertEquals(ErrorCode.AI_PROVIDER_UNAVAILABLE, exception.errorCode());
        verify(providerSelector).requireDefault(AiPurpose.ROADMAP_GENERATION);
        verify(credentialSelector).findFirstAvailable(providerId);
    }

    @Test
    void rejectsPromptsThatExceedTheConfiguredInputBudget() {
        when(providerSelector.requireDefault(AiPurpose.ROADMAP_GENERATION))
                .thenReturn(config);
        when(config.getProvider()).thenReturn(provider);
        when(config.getMaxInputTokens()).thenReturn(1);
        when(provider.getProtocol()).thenReturn(AiProviderProtocol.OPENAI_COMPATIBLE);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.generateContent(
                        AiPurpose.ROADMAP_GENERATION,
                        "Long system prompt",
                        "Long user prompt"));

        assertEquals(ErrorCode.AI_GENERATION_FAILED, exception.errorCode());
        verify(credentialSelector, never()).findFirstAvailable(org.mockito.ArgumentMatchers.any());
    }
}
