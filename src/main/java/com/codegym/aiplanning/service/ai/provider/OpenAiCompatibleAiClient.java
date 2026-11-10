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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiCompatibleAiClient implements AiClientService {

    private static final String DEEPSEEK_PROVIDER_CODE = "DEEPSEEK";

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
                    .body(requestBody(config, provider, systemPrompt, userPrompt))
                    .retrieve()
                    .body(CompletionResponse.class);
            if (response != null && response.usage() != null) {
                AiUsageHolder.set(new AiUsage(
                        response.usage().promptTokens(),
                        response.usage().completionTokens()));
            }
            return requireContent(response);
        } catch (RestClientResponseException exception) {
            throw providerFailure(exception);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new BusinessException(
                        ErrorCode.AI_TIMEOUT,
                        "The AI provider timed out during generation.",
                        exception.getCause() != null ? exception.getCause() : exception);
            }
            throw new BusinessException(
                    ErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "The AI provider could not be reached within the configured timeout.",
                    exception.getCause() != null ? exception.getCause() : exception);
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
            AiProviderConfig config,
            AiProvider provider,
            String systemPrompt,
            String userPrompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.getModel());
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        request.put("max_tokens", config.getMaxOutputTokens());
        request.put("temperature", config.getTemperature());
        request.put("response_format", Map.of("type", "json_object"));
        request.put("stream", false);

        if (DEEPSEEK_PROVIDER_CODE.equalsIgnoreCase(provider.getCode())) {
            request.put("thinking", Map.of("type", "disabled"));
        }
        return request;
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
        if (response != null
                && response.choices() != null
                && !response.choices().isEmpty()
                && "length".equalsIgnoreCase(response.choices().get(0).finishReason())) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATION_FAILED,
                    "The AI provider stopped at its configured output-token limit. Increase that limit or reduce the requested output size.");
        }
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

    private boolean isTimeout(ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("timed out")) {
                return true;
            }
            cause = cause.getCause();
        }
        String msg = exception.getMessage();
        return msg != null && msg.toLowerCase().contains("timed out");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionResponse(
            List<CompletionChoice> choices,
            CompletionUsage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionChoice(
            CompletionMessage message,
            @com.fasterxml.jackson.annotation.JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionMessage(String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {}
}
