package com.codegym.aiplanning.controller.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.ai.dto.AiPromptPreviewRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.CreateAiPromptDraftRequest;
import com.codegym.aiplanning.controller.admin.ai.dto.UpdateAiPromptDraftRequest;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.ai.AiPromptTemplateRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class AdminAiPromptControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AiPromptTemplateRepository promptTemplateRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserAccount adminAccount;
    private UserAccount userAccount;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        promptTemplateRepository.deleteAll();
        adminAccount = createAccount(UserRole.ADMIN);
        userAccount = createAccount(UserRole.USER);
        adminToken = login(adminAccount);
        userToken = login(userAccount);
    }

    @Test
    @DisplayName("GET /default returns default prompt for purpose")
    void getDefaultPrompt_returnsDefault() throws Exception {
        mockMvc.perform(get(ApiConstant.ADMIN_AI_PROMPTS + "/default?purpose=ROADMAP_GENERATION")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("ROADMAP_GENERATION"))
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    @DisplayName("Security: Unauthenticated request returns 401 Unauthorized")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ApiConstant.ADMIN_AI_PROMPTS + "?purpose=ROADMAP_GENERATION"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: USER role request returns 403 Forbidden")
    void userRole_returns403() throws Exception {
        mockMvc.perform(get(ApiConstant.ADMIN_AI_PROMPTS + "?purpose=ROADMAP_GENERATION")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Full Prompt Lifecycle: create draft -> update -> publish -> activate -> create second -> publish -> activate -> rollback -> archive")
    void fullPromptLifecycle_worksAsDesigned() throws Exception {
        // 1. Create Draft v1
        CreateAiPromptDraftRequest createRequest = new CreateAiPromptDraftRequest(
                AiPurpose.ROADMAP_GENERATION,
                "Prompt v1 in {{language}}");

        MvcResult createResult = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andReturn();

        JsonNode v1Json = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        UUID v1Id = UUID.fromString(v1Json.path("id").asText());
        long v1LockVersion = v1Json.path("version").asLong();

        // 2. Update Draft v1
        UpdateAiPromptDraftRequest updateRequest = new UpdateAiPromptDraftRequest(
                "Updated prompt v1 in {{language}}",
                v1LockVersion);

        MvcResult updateResult = mockMvc.perform(put(ApiConstant.ADMIN_AI_PROMPTS + "/" + v1Id)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Updated prompt v1 in {{language}}"))
                .andReturn();

        v1LockVersion = objectMapper.readTree(updateResult.getResponse().getContentAsString()).path("data").path("version").asLong();

        // 3. Publish v1
        MvcResult publishResult = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS + "/" + v1Id + "/publish?version=" + v1LockVersion)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andReturn();

        v1LockVersion = objectMapper.readTree(publishResult.getResponse().getContentAsString()).path("data").path("version").asLong();

        // 4. Activate v1
        MvcResult activateResult = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS + "/" + v1Id + "/activate?version=" + v1LockVersion)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andReturn();

        v1LockVersion = objectMapper.readTree(activateResult.getResponse().getContentAsString()).path("data").path("version").asLong();

        // 5. Create Draft v2
        CreateAiPromptDraftRequest createV2Request = new CreateAiPromptDraftRequest(
                AiPurpose.ROADMAP_GENERATION,
                "Prompt v2 in {{language}}");

        MvcResult createV2Result = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createV2Request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNumber").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andReturn();

        JsonNode v2Json = objectMapper.readTree(createV2Result.getResponse().getContentAsString()).path("data");
        UUID v2Id = UUID.fromString(v2Json.path("id").asText());
        long v2LockVersion = v2Json.path("version").asLong();

        // 6. Publish v2
        MvcResult publishV2Result = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS + "/" + v2Id + "/publish?version=" + v2LockVersion)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andReturn();

        v2LockVersion = objectMapper.readTree(publishV2Result.getResponse().getContentAsString()).path("data").path("version").asLong();

        // 7. Activate v2 (v1 should now be inactive!)
        MvcResult activateV2Result = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS + "/" + v2Id + "/activate?version=" + v2LockVersion)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andReturn();

        v2LockVersion = objectMapper.readTree(activateV2Result.getResponse().getContentAsString()).path("data").path("version").asLong();

        // Verify v1 is no longer active in DB
        var v1FromDb = promptTemplateRepository.findById(v1Id).orElseThrow();
        assertThat(v1FromDb.isActive()).isFalse();

        // 8. Rollback to v1 (AC6: active pointer moves to v1, v2 is deactivated, no version modified or deleted)
        MvcResult rollbackResult = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS + "/" + v1Id + "/rollback?version=" + v1FromDb.getVersion())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andReturn();

        var v2FromDb = promptTemplateRepository.findById(v2Id).orElseThrow();
        assertThat(v2FromDb.isActive()).isFalse();
        assertThat(v2FromDb.getStatus().name()).isEqualTo("PUBLISHED");

        // 9. Archive v2
        mockMvc.perform(delete(ApiConstant.ADMIN_AI_PROMPTS + "/" + v2Id + "?version=" + v2FromDb.getVersion())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        var v2Archived = promptTemplateRepository.findById(v2Id).orElseThrow();
        assertThat(v2Archived.isArchived()).isTrue();
        assertThat(v2Archived.isActive()).isFalse();
    }

    @Test
    @DisplayName("AC8: Preview endpoint renders synthetic data without calling LLM provider")
    void preview_rendersSyntheticData() throws Exception {
        AiPromptPreviewRequest previewRequest = new AiPromptPreviewRequest(
                AiPurpose.DAILY_PLAN_GENERATION,
                "Daily plan for {{availableMinutes}} min, review {{maxReviewMinutes}} min, locale {{language}}.",
                Map.of("availableMinutes", "60", "maxReviewMinutes", "18", "language", "vi-VN"));

        mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS + "/preview")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.renderedContent").value("Daily plan for 60 min, review 18 min, locale vi-VN."));
    }

    @Test
    @DisplayName("Edge Case: Creating draft with secret key returns 400 PROMPT_CONTAINS_SECRET")
    void createDraft_withSecret_returns400() throws Exception {
        CreateAiPromptDraftRequest request = new CreateAiPromptDraftRequest(
                AiPurpose.ROADMAP_GENERATION,
                "Prompt with secret key: sk-live-1234567890abcdef1234567890");

        mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMPT_CONTAINS_SECRET"));
    }

    @Test
    @DisplayName("Edge Case: Creating draft with invalid placeholder returns 400 PROMPT_PLACEHOLDER_INVALID")
    void createDraft_withInvalidPlaceholder_returns400() throws Exception {
        CreateAiPromptDraftRequest request = new CreateAiPromptDraftRequest(
                AiPurpose.ROADMAP_GENERATION,
                "Prompt with unauthorized variable {{userPassword}}");

        mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMPT_PLACEHOLDER_INVALID"));
    }

    @Test
    @DisplayName("Edge Case: Activating DRAFT directly returns 409 PROMPT_NOT_PUBLISHED")
    void activate_draftDirectly_returns409() throws Exception {
        CreateAiPromptDraftRequest request = new CreateAiPromptDraftRequest(
                AiPurpose.ROADMAP_GENERATION,
                "Valid prompt in {{language}}");

        MvcResult createResult = mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        UUID draftId = UUID.fromString(json.path("id").asText());
        long version = json.path("version").asLong();

        // Direct activate without publish
        mockMvc.perform(post(ApiConstant.ADMIN_AI_PROMPTS + "/" + draftId + "/activate?version=" + version)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMPT_NOT_PUBLISHED"));
    }

    private UserAccount createAccount(UserRole role) {
        String email = "prompt-test-" + UUID.randomUUID() + "@example.com";
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
        if (role == UserRole.USER) {
            UserProfile profile = UserProfile.create(account, "Test User");
            profile.completeSetup("Test User", "Asia/Ho_Chi_Minh", "vi", 60, Instant.now());
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
