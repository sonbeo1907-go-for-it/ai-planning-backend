package com.codegym.aiplanning.service.ai.provider;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

final class ProviderConnectionSupport {

    private ProviderConnectionSupport() {}

    static RestClient restClient(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    static ProviderConnectionResult classify(
            long startedAtNanos, RuntimeException exception) {
        long latencyMs = elapsedMillis(startedAtNanos);
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 401 || status == 403) {
                return ProviderConnectionResult.failure(
                        latencyMs,
                        "AUTHENTICATION_FAILED",
                        "The provider rejected the configured credential.");
            }
            if (status == 404) {
                return ProviderConnectionResult.failure(
                        latencyMs,
                        "MODEL_OR_ENDPOINT_NOT_FOUND",
                        "The configured model or endpoint was not found.");
            }
            if (status == 429) {
                return ProviderConnectionResult.failure(
                        latencyMs,
                        "RATE_LIMITED",
                        "The provider rate-limited the connection test.");
            }
            if (status >= 500) {
                return ProviderConnectionResult.failure(
                        latencyMs,
                        "PROVIDER_UNAVAILABLE",
                        "The provider is temporarily unavailable.");
            }
            return ProviderConnectionResult.failure(
                    latencyMs,
                    "PROVIDER_REJECTED_REQUEST",
                    "The provider rejected the connection test request.");
        }
        if (exception instanceof ResourceAccessException) {
            return ProviderConnectionResult.failure(
                    latencyMs,
                    "TIMEOUT_OR_NETWORK_ERROR",
                    "The provider could not be reached within the configured timeout.");
        }
        return ProviderConnectionResult.failure(
                latencyMs,
                "CONNECTION_TEST_FAILED",
                "The provider connection test failed.");
    }

    static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    static String endpoint(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBase + path;
    }
}
