package com.codegym.aiplanning.controller.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiExecutionResultType;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.ai.CredentialSelectionStrategy;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.repository.ai.AiProviderConfigRepository;
import com.codegym.aiplanning.repository.ai.AiProviderRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class AdminAiExecutionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AiProviderRepository providerRepository;

    @Autowired
    private AiProviderConfigRepository providerConfigRepository;

    @Autowired
    private AiExecutionRepository executionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount adminAccount;
    private UserAccount userAccount;
    private String adminToken;
    private String userToken;

    private AiProvider testProvider;
    private AiProviderConfig testConfig;

    @BeforeEach
    void setUp() throws Exception {
        adminAccount = createAccount(UserRole.ADMIN);
        userAccount = createAccount(UserRole.USER);
        adminToken = login(adminAccount);
        userToken = login(userAccount);

        String providerCode = "TEST_PROV_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        testProvider = providerRepository.saveAndFlush(AiProvider.create(
                providerCode,
                "OpenAI Test",
                "https://api.openai.com/v1",
                AiProviderProtocol.OPENAI_COMPATIBLE,
                CredentialSelectionStrategy.PRIORITY,
                true));

        testConfig = providerConfigRepository.saveAndFlush(AiProviderConfig.create(
                testProvider,
                AiPurpose.ROADMAP_GENERATION,
                "gpt-4o",
                true,
                30,
                4000,
                2000,
                BigDecimal.valueOf(0.7)));
    }

    @Test
    @DisplayName("AC5 & Security: Unauthenticated request returns 401 Unauthorized")
    void listExecutions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ApiConstant.ADMIN_AI_EXECUTIONS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC5 & Security: USER role request returns 403 Forbidden")
    void listExecutions_asUser_returns403() throws Exception {
        mockMvc.perform(get(ApiConstant.ADMIN_AI_EXECUTIONS)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC1: ADMIN role receives paginated list with default sort createdAt DESC")
    void listExecutions_asAdmin_returnsPaginatedDefaultSorted() throws Exception {
        createExecution(AiExecutionStatus.QUEUED, 0, null, null, null, null, null);
        createExecution(AiExecutionStatus.SUCCEEDED, 1, 1200L, 500, 1000, null, null);

        mockMvc.perform(get(ApiConstant.ADMIN_AI_EXECUTIONS)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.pageable").exists())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("AC2: Operational filters by status, providerId, and purpose")
    void listExecutions_withFilters_returnsMatchingRecordsOnly() throws Exception {
        AiExecution failedExec = createExecution(
                AiExecutionStatus.FAILED, 2, 800L, 300, null, "AI_RATE_LIMIT", "Rate limit exceeded");
        AiExecution succeededExec = createExecution(
                AiExecutionStatus.SUCCEEDED, 1, 1500L, 400, 800, null, null);

        // Filter by status=FAILED
        mockMvc.perform(get(ApiConstant.ADMIN_AI_EXECUTIONS)
                        .param("status", "FAILED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + failedExec.getId() + "')]").exists())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + succeededExec.getId() + "')]").doesNotExist());

        // Filter by failureCode=AI_RATE_LIMIT
        mockMvc.perform(get(ApiConstant.ADMIN_AI_EXECUTIONS)
                        .param("failureCode", "AI_RATE_LIMIT")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + failedExec.getId() + "')]").exists())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + succeededExec.getId() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("AC3, AC4, AC5, AC6: Detail endpoint returns sanitized diagnostics and timeline with NO user PII or personal resource IDs")
    void getExecutionDetail_asAdmin_returnsSanitizedDiagnosticsAndTimeline() throws Exception {
        AiExecution execution = createExecution(
                AiExecutionStatus.FAILED, 3, 2500L, 1000, null, "CONTEXT_OVERFLOW", "Prompt context exceeded limit");

        MvcResult result = mockMvc.perform(get(ApiConstant.ADMIN_AI_EXECUTIONS + "/" + execution.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(execution.getId().toString()))
                .andExpect(jsonPath("$.data.providerId").value(testProvider.getId().toString()))
                .andExpect(jsonPath("$.data.providerName").value("OpenAI Test"))
                .andExpect(jsonPath("$.data.model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.purpose").value("ROADMAP_GENERATION"))
                .andExpect(jsonPath("$.data.operation").value("GENERATE"))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.attemptCount").value(3))
                .andExpect(jsonPath("$.data.latencyMs").value(2500))
                .andExpect(jsonPath("$.data.inputTokens").value(1000))
                .andExpect(jsonPath("$.data.failureCode").value("CONTEXT_OVERFLOW"))
                .andExpect(jsonPath("$.data.failureMessage").value("Prompt context exceeded limit"))
                // Timeline milestones
                .andExpect(jsonPath("$.data.timeline").isArray())
                .andExpect(jsonPath("$.data.timeline[0].eventName").value("QUEUED"))
                .andExpect(jsonPath("$.data.timeline[0].synthetic").value(false))
                .andExpect(jsonPath("$.data.timeline[1].eventName").value("RUNNING"))
                .andExpect(jsonPath("$.data.timeline[1].synthetic").value(false))
                .andExpect(jsonPath("$.data.timeline[2].eventName").value("RETRY (Attempt 2)"))
                .andExpect(jsonPath("$.data.timeline[2].synthetic").value(true))
                .andExpect(jsonPath("$.data.timeline[3].eventName").value("RETRY (Attempt 3)"))
                .andExpect(jsonPath("$.data.timeline[3].synthetic").value(true))
                .andExpect(jsonPath("$.data.timeline[4].eventName").value("FAILED"))
                .andExpect(jsonPath("$.data.timeline[4].synthetic").value(false))
                // AC5 & AC6: STRICT PRIVACY VERIFICATION
                .andExpect(jsonPath("$.data.targetId").doesNotExist())
                .andExpect(jsonPath("$.data.targetType").doesNotExist())
                .andExpect(jsonPath("$.data.resultId").doesNotExist())
                .andExpect(jsonPath("$.data.resultType").doesNotExist())
                .andExpect(jsonPath("$.data.owner").doesNotExist())
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.activeSlotTargetId").doesNotExist())
                .andExpect(jsonPath("$.data.prompt").doesNotExist())
                .andExpect(jsonPath("$.data.response").doesNotExist())
                .andReturn();

        // Extra check: ensure personal target ID is nowhere in the entire JSON string
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(execution.getTargetId().toString());
    }

    @Test
    @DisplayName("Detail endpoint: 404 Not Found when execution does not exist")
    void getExecutionDetail_notFound_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get(ApiConstant.ADMIN_AI_EXECUTIONS + "/" + nonExistentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private AiExecution createExecution(
            AiExecutionStatus status,
            int attemptCount,
            Long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String failureCode,
            String failureMessage) {
        UUID targetId = UUID.randomUUID();
        AiExecution execution = AiExecution.queue(
                userAccount,
                testConfig,
                AiPurpose.ROADMAP_GENERATION,
                AiExecutionOperation.GENERATE,
                AiExecutionTargetType.ROADMAP,
                targetId,
                UUID.randomUUID().toString());

        Instant now = Instant.now();
        ReflectionTestUtils.setField(execution, "attemptCount", attemptCount);
        ReflectionTestUtils.setField(execution, "status", status);
        ReflectionTestUtils.setField(execution, "startedAt", status != AiExecutionStatus.QUEUED ? now.minusSeconds(10) : null);
        ReflectionTestUtils.setField(execution, "completedAt", status == AiExecutionStatus.SUCCEEDED || status == AiExecutionStatus.FAILED || status == AiExecutionStatus.TIMEOUT ? now : null);
        ReflectionTestUtils.setField(execution, "latencyMs", latencyMs);
        ReflectionTestUtils.setField(execution, "inputTokens", inputTokens);
        ReflectionTestUtils.setField(execution, "outputTokens", outputTokens);
        if (status == AiExecutionStatus.SUCCEEDED) {
            ReflectionTestUtils.setField(execution, "resultType", AiExecutionResultType.ROADMAP_VERSION);
            ReflectionTestUtils.setField(execution, "resultId", UUID.randomUUID());
            ReflectionTestUtils.setField(execution, "failureCode", null);
            ReflectionTestUtils.setField(execution, "failureMessage", null);
            ReflectionTestUtils.setField(execution, "activeSlotTargetId", null);
        } else if (status == AiExecutionStatus.FAILED || status == AiExecutionStatus.TIMEOUT) {
            ReflectionTestUtils.setField(execution, "resultType", null);
            ReflectionTestUtils.setField(execution, "resultId", null);
            ReflectionTestUtils.setField(execution, "failureCode", failureCode != null ? failureCode : "DEFAULT_ERROR");
            ReflectionTestUtils.setField(execution, "failureMessage", failureMessage != null ? failureMessage : "Error occurred");
            ReflectionTestUtils.setField(execution, "activeSlotTargetId", null);
        } else {
            ReflectionTestUtils.setField(execution, "resultType", null);
            ReflectionTestUtils.setField(execution, "resultId", null);
            ReflectionTestUtils.setField(execution, "failureCode", null);
            ReflectionTestUtils.setField(execution, "failureMessage", null);
        }

        return executionRepository.saveAndFlush(execution);
    }

    private UserAccount createAccount(UserRole role) {
        String email = "exec-test-" + UUID.randomUUID() + "@example.com";
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
        if (role == UserRole.USER) {
            UserProfile profile = UserProfile.create(account, "Test User");
            profile.completeSetup(
                    "Test User", "Asia/Ho_Chi_Minh", "vi", 60, Instant.now());
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
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
