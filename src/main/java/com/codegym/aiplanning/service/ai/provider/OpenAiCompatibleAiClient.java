package com.codegym.aiplanning.service.ai.provider;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.codegym.aiplanning.service.ai.AiCredentialSelector;
import com.codegym.aiplanning.service.ai.AiProviderSelector;
import com.codegym.aiplanning.service.ai.ResolvedAiCredential;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiCompatibleAiClient implements AiClientService {

    private final AiProviderSelector providerSelector;
    private final AiCredentialSelector credentialSelector;

    public OpenAiCompatibleAiClient(
            AiProviderSelector providerSelector,
            AiCredentialSelector credentialSelector) {
        this.providerSelector = providerSelector;
        this.credentialSelector = credentialSelector;
    }

    @Override
    public String generateContent(
            AiPurpose purpose, String systemPrompt, String userPrompt) {
        AiProviderConfig config = providerSelector.requireDefault(purpose);
        return generateContent(config, systemPrompt, userPrompt);
    }

    @Override
    public String generateContent(
            AiProviderConfig config, String systemPrompt, String userPrompt) {
        AiProvider provider = config.getProvider();
        requireUsableConfiguration(config, provider);
        requireSupportedProtocol(provider);
        requireWithinInputBudget(config, systemPrompt, userPrompt);

        ResolvedAiCredential credential = credentialSelector
                .findFirstAvailable(provider.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AI_PROVIDER_UNAVAILABLE,
                        "The configured AI provider credential is unavailable."));

        try {
            RestClient client = ProviderConnectionSupport.restClient(config.getTimeoutSeconds());
            CompletionResponse response = client.post()
                    .uri(ProviderConnectionSupport.endpoint(
                            provider.getBaseUrl(), "/chat/completions"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential.secret())
                    .body(requestBody(config, systemPrompt, userPrompt))
                    .retrieve()
                    .body(CompletionResponse.class);
            return requireContent(response);
        } catch (RestClientResponseException exception) {
            throw providerFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "The AI provider could not be reached within the configured timeout.");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATION_FAILED,
                    "The AI provider response could not be processed.");
        }
    }

    private void requireUsableConfiguration(
            AiProviderConfig config, AiProvider provider) {
        if (!config.isEnabled()
                || config.isArchived()
                || !provider.isEnabled()
                || provider.isArchived()) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "The AI provider configuration selected for this execution is unavailable.");
        }
    }

    private Map<String, Object> requestBody(
            AiProviderConfig config, String systemPrompt, String userPrompt) {
        return Map.of(
                "model", config.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "max_tokens", config.getMaxOutputTokens(),
                "temperature", config.getTemperature(),
                "response_format", Map.of("type", "json_object"),
                "stream", false);
    }

    private void requireSupportedProtocol(AiProvider provider) {
        if (provider.getProtocol() != AiProviderProtocol.OPENAI_COMPATIBLE) {
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_INVALID_CONFIGURATION,
                    "The configured AI provider protocol is not supported for generation.");
        }
    }

    private void requireWithinInputBudget(
            AiProviderConfig config, String systemPrompt, String userPrompt) {
        long approximateCharacterBudget = (long) config.getMaxInputTokens() * 4L;
        long promptCharacters = (long) systemPrompt.length() + userPrompt.length();
        if (promptCharacters > approximateCharacterBudget) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATION_FAILED,
                    "The AI generation context exceeds the configured input-token budget.");
        }
    }

    private String requireContent(CompletionResponse response) {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATION_FAILED,
                    "The AI provider returned an empty generation result.");
        }
        return response.choices().get(0).message().content();
    }

    private BusinessException providerFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new BusinessException(
                    ErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "The AI provider rejected the configured credential.");
        }
        if (status == 404) {
            return new BusinessException(
                    ErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "The configured AI model or endpoint was not found.");
        }
        if (status == 429) {
            return new BusinessException(
                    ErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "The AI provider rate limit was reached.");
        }
        return new BusinessException(
                ErrorCode.AI_PROVIDER_UNAVAILABLE,
                "The AI provider could not complete the generation request.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionResponse(List<CompletionChoice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionChoice(CompletionMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionMessage(String content) {}
}
