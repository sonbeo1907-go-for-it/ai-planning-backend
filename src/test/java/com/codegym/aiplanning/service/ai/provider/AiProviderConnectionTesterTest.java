package com.codegym.aiplanning.service.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.ai.CredentialSelectionStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiProviderConnectionTesterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void compatibleProtocolUsesOneMinimalRequest() throws Exception {
        AiProvider provider = provider(baseUrl);
        AiProviderConfig config = config(provider, "deepseek-chat");

        ProviderConnectionResult result = new OpenAiCompatibleConnectionTester()
                .test(provider, config, "provider-secret");

        assertThat(result.success()).isTrue();
        assertThat(requestCount).hasValue(1);
        assertThat(requestPath).hasValue("/v1/chat/completions");
        assertThat(authorization).hasValue("Bearer provider-secret");

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("deepseek-chat");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(1);
        assertThat(body.path("temperature").asInt()).isZero();
        assertThat(body.path("stream").asBoolean()).isFalse();
    }

    @Test
    void providerFailureIsSanitizedAndIsNotRetried() {
        responseStatus.set(401);
        AiProvider provider = provider(baseUrl);
        AiProviderConfig config = config(provider, "test-model");

        ProviderConnectionResult result = new OpenAiCompatibleConnectionTester()
                .test(provider, config, "invalid-secret");

        assertThat(result.success()).isFalse();
        assertThat(result.failureCategory()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(result.message()).doesNotContain("must-not-be-returned");
        assertThat(result.message()).doesNotContain("invalid-secret");
        assertThat(requestCount).hasValue(1);
    }

    private AiProvider provider(String providerBaseUrl) {
        return AiProvider.create(
                "DEEPSEEK",
                "DeepSeek",
                providerBaseUrl,
                AiProviderProtocol.OPENAI_COMPATIBLE,
                CredentialSelectionStrategy.PRIORITY,
                true);
    }

    private AiProviderConfig config(AiProvider provider, String model) {
        return AiProviderConfig.create(
                provider,
                AiPurpose.ROADMAP_GENERATION,
                model,
                true,
                2,
                1000,
                100,
                new BigDecimal("0.20"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        requestPath.set(exchange.getRequestURI().getPath());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] response = "{\"providerContent\":\"must-not-be-returned\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus.get(), response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
