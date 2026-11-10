package com.codegym.aiplanning.controller.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.ai.AiProviderConfigRepository;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AiProviderConfigControllerIntegrationTest {

    private static final String TEST_SECRET = "test-deepseek-secret";
    private static final String PROVIDER_BODY_MARKER = "provider-body-must-not-leak";
    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();

    private static HttpServer providerServer;
    private static String providerBaseUrl;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AiProviderConfigRepository providerConfigRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeAll
    static void startProviderServer() throws IOException {
        providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerServer.createContext("/", AiProviderConfigControllerIntegrationTest::respond);
        providerServer.start();
        providerBaseUrl =
                "http://127.0.0.1:" + providerServer.getAddress().getPort() + "/v1";
    }

    @AfterAll
    static void stopProviderServer() {
        providerServer.stop(0);
    }

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.allow-insecure-http-base-urls", () -> true);
        registry.add("TEST_DEEPSEEK_API_KEY", () -> TEST_SECRET);
    }

    @Test
    void adminManagesRegistryCredentialsPurposeDefaultsAndConnectionTests()
            throws Exception {
        UserAccount admin = createAccount(UserRole.ADMIN);
        String adminToken = login(admin);
        String providerCode = "DEEPSEEK_" + randomCodeSuffix();

        MvcResult providerCreate = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROVIDERS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(providerJson(providerCode, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(providerCode))
                .andExpect(jsonPath("$.data.protocol").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.data.credentialStrategy").value("PRIORITY"))
                .andExpect(jsonPath("$.data.credentials[0].secretRef")
                        .value("env:TEST_DEEPSEEK_API_KEY"))
                .andExpect(jsonPath("$.data.credentials[0].maskedSecret").value("****cret"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(TEST_SECRET))))
                .andReturn();
        JsonNode provider = data(providerCreate);
        UUID providerId = UUID.fromString(provider.path("id").asText());

        MvcResult configCreate = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROVIDER_CONFIGS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configJson(providerId, true, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.providerId").value(providerId.toString()))
                .andExpect(jsonPath("$.data.providerCode").value(providerCode))
                .andExpect(jsonPath("$.data.defaultProvider").value(true))
                .andReturn();
        JsonNode config = data(configCreate);
        UUID configId = UUID.fromString(config.path("id").asText());

        int requestsBeforeTest = REQUEST_COUNT.get();
        mockMvc.perform(post(configPath(configId) + "/test-connection")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.latencyMs").isNumber())
                .andExpect(jsonPath("$.data.providerCode").value(providerCode))
                .andExpect(jsonPath("$.data.credentialLabel").value("Primary key"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(PROVIDER_BODY_MARKER))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(TEST_SECRET))));
        assertThat(REQUEST_COUNT.get()).isEqualTo(requestsBeforeTest + 1);
        assertThat(objectMapper.readTree(LAST_REQUEST_BODY.get())
                        .path("max_tokens")
                        .asInt())
                .isEqualTo(1);

        MvcResult secondCredential = mockMvc.perform(post(providerPath(providerId) + "/credentials")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "label", "Higher priority key",
                                "secretRef", "env:TEST_DEEPSEEK_API_KEY",
                                "priority", 100,
                                "enabled", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priority").value(100))
                .andReturn();

        mockMvc.perform(post(configPath(configId) + "/test-connection")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialId")
                        .value(data(secondCredential).path("id").asText()))
                .andExpect(jsonPath("$.data.credentialLabel")
                        .value("Higher priority key"));

        mockMvc.perform(post(providerPath(providerId) + "/disable")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "version", provider.path("version").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_IN_USE"));

        assertThat(providerConfigRepository
                        .findByPurposeAndDefaultProviderTrueAndArchivedAtIsNull(
                                AiPurpose.ROADMAP_GENERATION)
                        .orElseThrow()
                        .getId())
                .isEqualTo(configId);

        assertThat(auditLogRepository.findAll())
                .filteredOn(log -> admin.getId().equals(log.getActorId()))
                .extracting(log -> log.getAction())
                .contains(
                        AuditEventAction.AI_PROVIDER_CREATED,
                        AuditEventAction.AI_PROVIDER_CREDENTIAL_CREATED,
                        AuditEventAction.AI_PROVIDER_CONFIG_CREATED,
                        AuditEventAction.AI_PROVIDER_CONNECTION_TESTED);
    }

    @Test
    void providerArchiveRequiresConfigurationsToBeArchivedFirst() throws Exception {
        String token = login(createAccount(UserRole.ADMIN));
        String providerCode = "ARCHIVE_" + randomCodeSuffix();
        JsonNode provider = data(mockMvc.perform(post(ApiConstant.ADMIN_AI_PROVIDERS)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(providerJson(providerCode, true)))
                .andExpect(status().isCreated())
                .andReturn());
        UUID providerId = UUID.fromString(provider.path("id").asText());

        JsonNode config = data(mockMvc.perform(post(ApiConstant.ADMIN_AI_PROVIDER_CONFIGS)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configJson(providerId, false, false)))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(delete(providerPath(providerId))
                        .queryParam("version", provider.path("version").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_IN_USE"));

        mockMvc.perform(delete(configPath(UUID.fromString(config.path("id").asText())))
                        .queryParam("version", config.path("version").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(providerPath(providerId))
                        .queryParam("version", provider.path("version").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotAccessProviderOrConfigurationAdministration() throws Exception {
        String userToken = login(createAccount(UserRole.USER));

        mockMvc.perform(get(ApiConstant.ADMIN_AI_PROVIDERS)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get(ApiConstant.ADMIN_AI_PROVIDER_CONFIGS)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ApiConstant.ADMIN_AI_PROVIDERS)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(providerJson("FORBIDDEN_" + randomCodeSuffix(), false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerDocumentsProviderRegistryAndConfigurationEndpoints() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(document.at("/paths/~1api~1v1~1admin~1ai-providers/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1admin~1ai-providers~1{providerId}~1credentials/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1admin~1ai-provider-configs/post")
                        .isMissingNode())
                .isFalse();
    }

    private UserAccount createAccount(UserRole role) {
        String email = "ai-provider-" + UUID.randomUUID() + "@example.com";
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
        if (role == UserRole.USER) {
            UserProfile profile = UserProfile.create(account, "Provider Test User");
            profile.completeSetup(
                    "Provider Test User", "Asia/Ho_Chi_Minh", "vi", 60, Instant.now());
            userProfileRepository.saveAndFlush(profile);
        }
        return account;
    }

    private String login(UserAccount account) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", account.getEmail(),
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("accessToken").asText();
    }

    private String providerJson(String code, boolean withCredential) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("code", code);
        body.put("displayName", "DeepSeek Test");
        body.put("baseUrl", providerBaseUrl);
        body.put("protocol", "OPENAI_COMPATIBLE");
        body.put("credentialStrategy", "PRIORITY");
        body.put("enabled", true);
        if (withCredential) {
            body.put("initialCredential", Map.of(
                    "label", "Primary key",
                    "secretRef", "env:TEST_DEEPSEEK_API_KEY",
                    "priority", 10,
                    "enabled", true));
        }
        return objectMapper.writeValueAsString(body);
    }

    private String configJson(UUID providerId, boolean enabled, boolean defaultProvider)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "providerId", providerId,
                "purpose", "ROADMAP_GENERATION",
                "model", "deepseek-chat",
                "enabled", enabled,
                "defaultProvider", defaultProvider,
                "timeoutSeconds", 2,
                "maxInputTokens", 100000,
                "maxOutputTokens", 8000,
                "temperature", 0.2));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data");
    }

    private String providerPath(UUID providerId) {
        return ApiConstant.ADMIN_AI_PROVIDERS + "/" + providerId;
    }

    private String configPath(UUID configId) {
        return ApiConstant.ADMIN_AI_PROVIDER_CONFIGS + "/" + configId;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String randomCodeSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static void respond(HttpExchange exchange) throws IOException {
        REQUEST_COUNT.incrementAndGet();
        LAST_REQUEST_BODY.set(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = ("{\"content\":\"" + PROVIDER_BODY_MARKER + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
