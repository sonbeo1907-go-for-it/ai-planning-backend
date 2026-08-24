package com.codegym.aiplanning.service.ai.provider;

import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleConnectionTester implements AiProviderConnectionTester {

    @Override
    public AiProviderProtocol protocol() {
        return AiProviderProtocol.OPENAI_COMPATIBLE;
    }

    @Override
    public ProviderConnectionResult test(
            AiProvider provider, AiProviderConfig config, String apiKey) {
        long startedAtNanos = System.nanoTime();
        try {
            RestClient client = ProviderConnectionSupport.restClient(config.getTimeoutSeconds());
            Map<String, Object> requestBody = Map.of(
                    "model", config.getModel(),
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", "Reply with OK.")),
                    "max_tokens", 1,
                    "temperature", 0,
                    "stream", false);

            client.post()
                    .uri(ProviderConnectionSupport.endpoint(
                            provider.getBaseUrl(), "/chat/completions"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            return ProviderConnectionResult.success(
                    ProviderConnectionSupport.elapsedMillis(startedAtNanos));
        } catch (RuntimeException exception) {
            return ProviderConnectionSupport.classify(startedAtNanos, exception);
        }
    }
}
