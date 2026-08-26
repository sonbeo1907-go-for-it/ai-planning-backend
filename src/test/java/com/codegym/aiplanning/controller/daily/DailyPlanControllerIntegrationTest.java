package com.codegym.aiplanning.controller.daily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class DailyPlanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RoadmapRepository roadmapRepository;

    @Autowired
    private RoadmapVersionRepository roadmapVersionRepository2;

    @Autowired
    private RoadmapItemRepository roadmapItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private AiClientService aiClientService;

    @Test
    void createDailyPlan_and_addTask_and_checklistCompletion_flow() throws Exception {
        UserAccount user = createUser("daily-user1", "Daily User 1");
        String token = login("daily-user1");

        LocalDate today = LocalDate.now();
        String createPlanPayload = String.format("""
                {
                    "planDate": "%s",
                    "availableMinutes": 120
                }
                """, today);

        MvcResult createResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planDate").value(today.toString()))
                .andExpect(jsonPath("$.data.availableMinutes").value(120))
                .andExpect(jsonPath("$.data.totalItemsCount").value(0))
                .andExpect(jsonPath("$.data.completionPercentage").value(0.0))
                .andReturn();

        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String versionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("latestVersionId").asText();

        String addTaskPayload = """
                {
                    "title": "Học Lập trình Java Core Module 1",
                    "description": "Thực hành chuỗi và mảng trong Java",
                    "category": "NEW_MATERIAL",
                    "plannedMinutes": 45
                }
                """;

        MvcResult addTaskResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTaskPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Học Lập trình Java Core Module 1"))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andReturn();

        String itemId = objectMapper.readTree(addTaskResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
                
        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/versions/" + versionId + "/activate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        String pomodoroPayload = """
                {
                    "completedMinutes": 25
                }
                """;

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/items/" + itemId + "/pomodoro")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pomodoroPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        String progressPayload = """
                {
                    "status": "COMPLETED",
                    "actualMinutes": 45,
                    "difficulty": 3,
                    "understandingRating": 4,
                    "note": "Done"
                }
                """;

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/items/" + itemId + "/progress")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(progressPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + planId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItemsCount").value(1))
                .andExpect(jsonPath("$.data.completedItemsCount").value(1))
                .andExpect(jsonPath("$.data.completionPercentage").value(100.0));
    }

    @Test
    void getTodayPlan_success() throws Exception {
        UserAccount user = createUser("daily-user2", "Daily User 2");
        String token = login("daily-user2");

        LocalDate today = LocalDate.now();
        String createPlanPayload = String.format("{\"planDate\": \"%s\", \"availableMinutes\": 60}", today);
        mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanPayload))
                .andExpect(status().isOk());

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planDate").value(today.toString()));
    }

    @Test
    void listDailyPlanHistoryReturnsMultiplePlansWithTheirCurrentVersions()
            throws Exception {
        createUser("daily-history-user", "Daily History User");
        String token = login("daily-history-user");
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        for (LocalDate planDate : List.of(yesterday, today)) {
            mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"planDate\": \"%s\", \"availableMinutes\": 60}",
                                    planDate)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].planDate").value(today.toString()))
                .andExpect(jsonPath("$.data[0].latestVersionId").isNotEmpty())
                .andExpect(jsonPath("$.data[1].planDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.data[1].latestVersionId").isNotEmpty());
    }

    @Test
    void versionLifecycle_preservesHistoryAndOnlyDraftIsEditable() throws Exception {
        createUser("daily-version-user", "Daily Version User");
        String token = login("daily-version-user");

        MvcResult createResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"planDate\": \"%s\", \"availableMinutes\": 90}",
                                LocalDate.now().plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String firstVersionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("latestVersionId").asText();

        MvcResult firstTaskResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + firstVersionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Original task\",\"plannedMinutes\":30}"))
                .andExpect(status().isOk())
                .andReturn();
        String firstTaskId = objectMapper.readTree(firstTaskResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + firstVersionId + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.activeVersionId").value(firstVersionId));

        MvcResult draftResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNumber").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.origin").value("USER_EDITED"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Original task"))
                .andExpect(jsonPath("$.data.items[0].status").value("NOT_STARTED"))
                .andReturn();

        String secondVersionId = objectMapper.readTree(draftResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String copiedTaskId = objectMapper.readTree(draftResult.getResponse().getContentAsString())
                .path("data").path("items").path(0).path("id").asText();
        assertThat(copiedTaskId).isNotEqualTo(firstTaskId);

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_PLAN_DRAFT_EXISTS"));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + firstVersionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Illegal edit\",\"plannedMinutes\":15}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_PLAN_LOCKED"));

        mockMvc.perform(delete(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + firstVersionId + "/items/" + firstTaskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_PLAN_LOCKED"));

        MvcResult addedDraftTaskResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + secondVersionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New task\",\"plannedMinutes\":15}"))
                .andExpect(status().isOk())
                .andReturn();
        String addedDraftTaskId = objectMapper
                .readTree(addedDraftTaskResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(delete(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + secondVersionId + "/items/" + addedDraftTaskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalPlannedMinutes").value(30));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + secondVersionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Replacement task\",\"plannedMinutes\":15}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + secondVersionId + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeVersionId").value(secondVersionId))
                .andExpect(jsonPath("$.data.totalItemsCount").value(2));

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + planId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(secondVersionId))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].id").value(firstVersionId))
                .andExpect(jsonPath("$.data[1].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.data[1].supersededAt").isNotEmpty());

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + firstVersionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(firstTaskId));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/items/" + copiedTaskId + "/progress")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PARTIALLY_COMPLETED\",\"actualMinutes\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + planId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completionPercentage").value(25.0));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_PLAN_LOCKED"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "DELETE FROM daily_plan_items WHERE id = ?",
                        java.util.UUID.fromString(copiedTaskId)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void userCannotAccessOrModifyAnotherUsersDailyPlan_returns404() throws Exception {
        UserAccount owner = createUser("daily-owner", "Daily Owner");
        UserAccount attacker = createUser("daily-attacker", "Attacker");

        String ownerToken = login("daily-owner");
        String attackerToken = login("daily-attacker");

        LocalDate today = LocalDate.now();
        MvcResult createResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"planDate\": \"%s\", \"availableMinutes\": 60}", today)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerPlanId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String ownerVersionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("latestVersionId").asText();

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + ownerPlanId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + ownerPlanId + "/versions")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + ownerPlanId
                        + "/versions/" + ownerVersionId + "/items")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Attacker Task\", \"plannedMinutes\": 30}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + ownerPlanId
                        + "/versions/generate")
                        .header("Authorization", "Bearer " + attackerToken)
                        .header("Idempotency-Key", "attacker-request"))
                .andExpect(status().isNotFound());

        MvcResult ownerTaskResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + ownerPlanId
                        + "/versions/" + ownerVersionId + "/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Owner Task\", \"plannedMinutes\": 30}"))
                .andExpect(status().isOk())
                .andReturn();
        String ownerItemId = objectMapper.readTree(ownerTaskResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + ownerPlanId + "/items/" + ownerItemId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + ownerPlanId
                        + "/items/" + ownerItemId + "/ai-suggestion")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotAccessPersonalDailyPlans() throws Exception {
        createAccount("daily-admin", "Daily Admin", UserRole.ADMIN);
        String adminToken = login("daily-admin");

        mockMvc.perform(get(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/"
                        + java.util.UUID.randomUUID()
                        + "/versions/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private UserAccount createUser(String emailPrefix, String displayName) {
        return createAccount(emailPrefix, displayName, UserRole.USER);
    }

    private UserAccount createAccount(
            String emailPrefix, String displayName, UserRole role) {
        String email = emailPrefix + "@example.com";
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
        userProfileRepository.saveAndFlush(UserProfile.create(account, displayName));
        return account;
    }

    private String login(String emailPrefix) throws Exception {
        String email = emailPrefix + "@example.com";
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "email", email,
                                        "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    @Test
    void taskAiSuggestion_generateReadRegenerateFlow() throws Exception {
        createUser("daily-ai-user", "Ai User");
        String token = login("daily-ai-user");

        LocalDate today = LocalDate.now();
        MvcResult createResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"planDate\": \"%s\", \"availableMinutes\": 60}", today)))
                .andExpect(status().isOk())
                .andReturn();
        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String versionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("latestVersionId").asText();

        MvcResult addTaskResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Học REST Controller\",\"plannedMinutes\":30}"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = objectMapper.readTree(addTaskResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Before generation, task detail contains no suggestion.
        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + planId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(itemId))
                .andExpect(jsonPath("$.data.aiSuggestion").doesNotExist());

        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString()))
                .thenReturn("""
                        {
                          "shortDescription": "Đọc tài liệu rồi thực hành.",
                          "steps": [
                            {"content": "Đọc tài liệu REST"},
                            {"content": "Chạy ví dụ CRUD"}
                          ],
                          "references": [
                            {"title": "Baeldung", "referenceType": "LINK", "documentId": null, "url": "https://baeldung.com/spring-boot"}
                          ]
                        }
                        """);

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/items/" + itemId + "/ai-suggestion")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "suggestion-request-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortDescription").value("Đọc tài liệu rồi thực hành."))
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[1].orderIndex").value(1))
                .andExpect(jsonPath("$.data.references[0].referenceType").value("LINK"))
                .andExpect(jsonPath("$.data.references[0].verified").value(false));

        // A second generate call is idempotent and must not call the AI again.
        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/items/" + itemId + "/ai-suggestion")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "suggestion-request-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortDescription").value("Đọc tài liệu rồi thực hành."));
        verify(aiClientService, times(1))
                .generateContent(eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString());

        // Task detail now embeds the generated suggestion.
        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + planId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSuggestion.shortDescription").value("Đọc tài liệu rồi thực hành."))
                .andExpect(jsonPath("$.data.aiSuggestion.references[0].referenceType").value("LINK"))
                .andExpect(jsonPath("$.data.aiSuggestion.references[0].verified").value(false));

        // Regenerate replaces the previous content.
        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString()))
                .thenReturn("""
                        {
                          "shortDescription": "Gợi ý thực hành mới hơn.",
                          "steps": [{"content": "Viết test trước"}],
                          "references": []
                        }
                        """);
        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/items/" + itemId + "/ai-suggestion/regenerate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortDescription").value("Gợi ý thực hành mới hơn."))
                .andExpect(jsonPath("$.data.steps.length()").value(1));
    }

    @Test
    void createDailyPlan_withRoadmapIntegration_flow() throws Exception {
        UserAccount user = createUser("daily-rmp-user", "Rmp User");
        String token = login("daily-rmp-user");

        // 1. Create a Roadmap and RoadmapVersion
        Roadmap roadmap = roadmapRepository.saveAndFlush(Roadmap.manualDraft(user, "Integration Roadmap", "Desc"));
        RoadmapVersion rVersion = roadmapVersionRepository2.saveAndFlush(RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.MANUAL));
        
        // Activate version
        roadmap.activateVersion(rVersion.getId());
        roadmap = roadmapRepository.saveAndFlush(roadmap);
        rVersion.activate(java.time.Instant.now());
        rVersion = roadmapVersionRepository2.saveAndFlush(rVersion);

        // Add a Topic to active Roadmap version
        RoadmapItem milestone = roadmapItemRepository.saveAndFlush(RoadmapItem.milestone(rVersion, "Week 1", "Desc", 0));
        RoadmapItem topic = roadmapItemRepository.saveAndFlush(RoadmapItem.topic(rVersion, milestone, "Learn Java Records", "Desc", 0, 45));

        // 2. Create Daily Plan linking to the Roadmap
        LocalDate today = LocalDate.now();
        String createPlanPayload = String.format("""
                {
                    "planDate": "%s",
                    "availableMinutes": 120,
                    "roadmapId": "%s"
                }
                """, today, roadmap.getId());

        MvcResult createResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPlanPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value(roadmap.getId().toString()))
                .andExpect(jsonPath("$.data.totalItemsCount").value(0))
                .andExpect(jsonPath("$.data.totalPlannedMinutes").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andReturn();

        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String versionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("latestVersionId").asText();

        // 3. Add manual task with roadmapItemId
        String addTaskPayload = String.format("""
                {
                    "title": "Học Lập trình Java Core Module 1",
                    "description": "Thực hành chuỗi và mảng trong Java",
                    "category": "NEW_MATERIAL",
                    "plannedMinutes": 45,
                    "roadmapItemId": "%s"
                }
                """, topic.getId());

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTaskPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapItemId").value(topic.getId().toString()));
    }
}
