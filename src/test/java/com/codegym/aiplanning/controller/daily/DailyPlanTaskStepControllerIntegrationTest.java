package com.codegym.aiplanning.controller.daily;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepStateRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class DailyPlanTaskStepControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private DailyPlanItemRepository dailyPlanItemRepository;

    @Autowired
    private DailyPlanTaskStepStateRepository taskStepStateRepository;

    @Autowired
    private ProgressEntryRepository progressEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void ownerCanManageDraftStepsAndExecuteThemOnlyAfterActivation() throws Exception {
        UserAccount owner = createAccount("task-step-owner", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-11");
        String stepsPath = stepsPath(path);

        MvcResult firstResult = mockMvc.perform(post(stepsPath)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Write a minimal example",
                                  "guidance": "Create one observable example.",
                                  "estimatedMinutes": 10,
                                  "required": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].orderIndex").value(0))
                .andExpect(jsonPath("$.data.progress.requiredCount").value(1))
                .andReturn();
        String firstStepId = read(firstResult).path("data").path("steps").get(0)
                .path("id").asText();

        MvcResult secondResult = mockMvc.perform(post(stepsPath)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Run the relevant tests",
                                  "orderIndex": 0,
                                  "estimatedMinutes": 15,
                                  "required": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].title")
                        .value("Run the relevant tests"))
                .andExpect(jsonPath("$.data.steps[1].title")
                        .value("Write a minimal example"))
                .andReturn();
        String secondStepId = read(secondResult).path("data").path("steps").get(0)
                .path("id").asText();
        long firstStepVersion = findStep(read(secondResult), firstStepId)
                .path("entityVersion").asLong();

        mockMvc.perform(put(stepsPath + "/" + firstStepId + "/completion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"stateVersion\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_PLAN_VERSION_NOT_ACTIVE"));

        mockMvc.perform(patch(stepsPath + "/" + firstStepId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entityVersion": %d,
                                  "title": "Explain the observed result",
                                  "guidance": null,
                                  "orderIndex": 0,
                                  "estimatedMinutes": 5,
                                  "required": true
                                }
                                """.formatted(firstStepVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].title")
                        .value("Explain the observed result"));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + path.planId()
                        + "/versions/" + path.versionId() + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put(stepsPath + "/" + firstStepId + "/completion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"stateVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.completedRequiredCount").value(1))
                .andExpect(jsonPath("$.data.progress.allRequiredStepsCompleted").value(false));

        mockMvc.perform(put(stepsPath + "/" + secondStepId + "/completion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"stateVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.completionPercentage").value(100.0))
                .andExpect(jsonPath("$.data.progress.allRequiredStepsCompleted").value(true));

        mockMvc.perform(put(stepsPath + "/" + secondStepId + "/completion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"stateVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress.completionPercentage").value(100.0));

        mockMvc.perform(put(stepsPath + "/" + secondStepId + "/completion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":false,\"stateVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        org.assertj.core.api.Assertions.assertThat(taskStepStateRepository.count())
                .isGreaterThanOrEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(
                        dailyPlanItemRepository.findById(java.util.UUID.fromString(path.itemId()))
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(com.codegym.aiplanning.entity.daily.DailyTaskStatus.NOT_STARTED);
    }

    @Test
    void taskStepsAreOwnerScopedAndUnavailableToAdmin() throws Exception {
        UserAccount owner = createAccount("task-step-security-owner", UserRole.USER);
        UserAccount other = createAccount("task-step-security-other", UserRole.USER);
        UserAccount admin = createAccount("task-step-security-admin", UserRole.ADMIN);
        String ownerToken = login(owner);
        PlanPath path = createPlanAndTask(ownerToken, "2032-01-12");
        String endpoint = stepsPath(path);

        mockMvc.perform(get(endpoint)
                        .header("Authorization", "Bearer " + login(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get(endpoint)
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        MvcResult created = mockMvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write an owned example\",\"required\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        String stepId = read(created).path("data").path("steps").get(0)
                .path("id").asText();

        mockMvc.perform(put(endpoint + "/" + stepId + "/completion")
                        .header("Authorization", "Bearer " + login(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"stateVersion\":null}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        String mismatchedVersionPath = ApiConstant.DAILY_PLANS + "/" + path.planId()
                + "/versions/" + java.util.UUID.randomUUID()
                + "/items/" + path.itemId()
                + "/steps/" + stepId + "/completion";
        mockMvc.perform(put(mismatchedVersionPath)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"stateVersion\":null}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void stepEstimatesCannotExceedParentTaskBudget() throws Exception {
        UserAccount owner = createAccount("task-step-budget", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-13");

        mockMvc.perform(post(stepsPath(path))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Consume too much time",
                                  "estimatedMinutes": 31,
                                  "required": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_STEP_TIME_EXCEEDED"));
    }

    @Test
    void ownerCanDeleteAStepOnlyWhileItsVersionIsDraft() throws Exception {
        UserAccount owner = createAccount("task-step-delete", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-14");
        String endpoint = stepsPath(path);

        MvcResult first = mockMvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Required action\",\"required\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        String firstStepId = read(first).path("data").path("steps").get(0)
                .path("id").asText();

        MvcResult second = mockMvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second action\",\"required\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        String secondStepId = read(second).path("data").path("steps").get(1)
                .path("id").asText();
        long firstStepVersion = findStep(read(second), firstStepId)
                .path("entityVersion").asLong();

        mockMvc.perform(delete(endpoint + "/" + firstStepId)
                        .queryParam("entityVersion", Long.toString(firstStepVersion))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(1))
                .andExpect(jsonPath("$.data.steps[0].id").value(secondStepId))
                .andExpect(jsonPath("$.data.steps[0].orderIndex").value(0));

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + path.planId()
                        + "/versions/" + path.versionId() + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete(endpoint + "/" + secondStepId)
                        .queryParam("entityVersion", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_PLAN_VERSION_NOT_EDITABLE"));
    }

    @Test
    void deletingMiddleStepUsesCollisionFreeOrderNormalization() throws Exception {
        UserAccount owner = createAccount("task-step-middle-delete", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-17");
        String endpoint = stepsPath(path);

        JsonNode first = createStep(token, endpoint, "Write first example");
        String firstId = first.path("data").path("steps").get(0).path("id").asText();
        JsonNode second = createStep(token, endpoint, "Run second example");
        String secondId = second.path("data").path("steps").get(1).path("id").asText();
        JsonNode third = createStep(token, endpoint, "Explain third example");
        String thirdId = third.path("data").path("steps").get(2).path("id").asText();
        long secondVersion = findStep(third, secondId).path("entityVersion").asLong();

        mockMvc.perform(delete(endpoint + "/" + secondId)
                        .queryParam("entityVersion", Long.toString(secondVersion))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].id").value(firstId))
                .andExpect(jsonPath("$.data.steps[0].orderIndex").value(0))
                .andExpect(jsonPath("$.data.steps[1].id").value(thirdId))
                .andExpect(jsonPath("$.data.steps[1].orderIndex").value(1));
    }

    @Test
    void stalePlannedStepUpdateReturnsConcurrentModification() throws Exception {
        UserAccount owner = createAccount("task-step-stale-update", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-18");
        String endpoint = stepsPath(path);
        JsonNode created = createStep(token, endpoint, "Write original example");
        JsonNode step = created.path("data").path("steps").get(0);
        String stepId = step.path("id").asText();
        long entityVersion = step.path("entityVersion").asLong();

        String firstUpdate = """
                {
                  "entityVersion": %d,
                  "title": "Write updated example",
                  "guidance": null,
                  "orderIndex": 0,
                  "estimatedMinutes": 10,
                  "required": true
                }
                """.formatted(entityVersion);
        mockMvc.perform(patch(endpoint + "/" + stepId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstUpdate))
                .andExpect(status().isOk());

        mockMvc.perform(patch(endpoint + "/" + stepId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstUpdate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void draftVersionCopiesPlannedStepsButNotRuntimeState() throws Exception {
        UserAccount owner = createAccount("task-step-version-copy", UserRole.USER);
        String token = login(owner);
        PlanPath original = createPlanAndTask(token, "2032-01-15");

        MvcResult createStepResult = mockMvc.perform(post(stepsPath(original))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Build an observable example",
                                  "guidance": "Run it and note the output.",
                                  "estimatedMinutes": 15,
                                  "required": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String originalStepId = read(createStepResult)
                .path("data").path("steps").get(0).path("id").asText();

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + original.planId()
                        + "/versions/" + original.versionId() + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].steps[0].id")
                        .value(originalStepId))
                .andExpect(jsonPath("$.data.items[0].stepProgress.requiredCount")
                        .value(1));

        MvcResult draftResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/"
                        + original.planId() + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].steps[0].title")
                        .value("Build an observable example"))
                .andExpect(jsonPath("$.data.items[0].steps[0].completed")
                        .value(false))
                .andReturn();

        JsonNode draft = read(draftResult).path("data");
        String draftVersionId = draft.path("id").asText();
        String copiedItemId = draft.path("items").get(0).path("id").asText();
        String copiedStepId = draft.path("items").get(0)
                .path("steps").get(0).path("id").asText();

        org.assertj.core.api.Assertions.assertThat(copiedStepId)
                .isNotEqualTo(originalStepId);
        org.assertj.core.api.Assertions.assertThat(taskStepStateRepository
                        .findByTaskStepId(java.util.UUID.fromString(copiedStepId)))
                .isEmpty();

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + original.planId()
                        + "/versions/" + draftVersionId + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(copiedItemId))
                .andExpect(jsonPath("$.data.items[0].steps[0].id")
                        .value(copiedStepId));

        org.assertj.core.api.Assertions.assertThat(taskStepStateRepository
                        .findByTaskStepId(java.util.UUID.fromString(copiedStepId)))
                .isPresent()
                .get()
                .extracting(state -> state.getCompleted())
                .isEqualTo(false);
    }

    @Test
    void parentTaskEditCannotInvalidateItsExistingSteps() throws Exception {
        UserAccount owner = createAccount("task-step-parent-edit", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-16");

        mockMvc.perform(post(stepsPath(path))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Implement one small example",
                                  "estimatedMinutes": 20,
                                  "required": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch(ApiConstant.DAILY_PLANS + "/" + path.planId()
                        + "/versions/" + path.versionId()
                        + "/items/" + path.itemId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Practise one focused skill",
                                  "description": null,
                                  "category": "CUSTOM",
                                  "plannedMinutes": 10,
                                  "orderIndex": 0,
                                  "clearLearningUnit": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_STEP_TIME_EXCEEDED"));
    }

    @Test
    void finalRequiredStepAndParentOutcomeAreAtomicAndIdempotent() throws Exception {
        UserAccount owner = createAccount("task-step-atomic", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-19");
        JsonNode created = createStep(token, stepsPath(path), "Finish the required example");
        String stepId = created.path("data").path("steps").get(0).path("id").asText();

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + path.planId()
                        + "/versions/" + path.versionId() + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String endpoint = stepsPath(path) + "/" + stepId + "/complete-with-outcome";
        String request = """
                {
                  "stateVersion": 0,
                  "outcome": {
                    "status": "PARTIALLY_COMPLETED",
                    "completionPercentage": 35,
                    "actualMinutes": 20,
                    "actualResult": "Completed the main example"
                  }
                }
                """;

        mockMvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "atomic-step-outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskSteps.progress.allRequiredStepsCompleted")
                        .value(true))
                .andExpect(jsonPath("$.data.task.status").value("PARTIALLY_COMPLETED"))
                .andExpect(jsonPath("$.data.task.completionPercentage").value(35));

        mockMvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "atomic-step-outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.completionPercentage").value(35));

        org.assertj.core.api.Assertions.assertThat(progressEntryRepository
                        .findByUserIdAndDailyPlanItemIdOrderByRecordedAtDesc(
                                owner.getId(),
                                java.util.UUID.fromString(path.itemId())))
                .hasSize(1);
    }

    @Test
    void invalidOutcomeRollsBackFinalStepCompletion() throws Exception {
        UserAccount owner = createAccount("task-step-rollback", UserRole.USER);
        String token = login(owner);
        PlanPath path = createPlanAndTask(token, "2032-01-20");
        JsonNode created = createStep(token, stepsPath(path), "Finish before recording");
        String stepId = created.path("data").path("steps").get(0).path("id").asText();

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + path.planId()
                        + "/versions/" + path.versionId() + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post(stepsPath(path) + "/" + stepId + "/complete-with-outcome")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "invalid-atomic-step-outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stateVersion": 0,
                                  "outcome": {
                                    "status": "PARTIALLY_COMPLETED",
                                    "actualMinutes": 20
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(stepsPath(path))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].completed").value(false))
                .andExpect(jsonPath("$.data.progress.allRequiredStepsCompleted").value(false));
    }

    private PlanPath createPlanAndTask(String token, String date) throws Exception {
        MvcResult planResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planDate\":\"" + date + "\",\"availableMinutes\":60}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode plan = read(planResult).path("data");
        String planId = plan.path("id").asText();
        String versionId = plan.path("latestVersionId").asText();

        MvcResult itemResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId
                        + "/versions/" + versionId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Practise one focused skill",
                                  "category": "CUSTOM",
                                  "plannedMinutes": 30
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = read(itemResult).path("data").path("id").asText();
        return new PlanPath(planId, versionId, itemId);
    }

    private String stepsPath(PlanPath path) {
        return ApiConstant.DAILY_PLANS + "/" + path.planId()
                + "/versions/" + path.versionId()
                + "/items/" + path.itemId()
                + "/steps";
    }

    private JsonNode createStep(
            String token,
            String endpoint,
            String title) throws Exception {
        MvcResult result = mockMvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "required", true))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    private UserAccount createAccount(String prefix, UserRole role) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                prefix + "@example.com",
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
        userProfileRepository.saveAndFlush(UserProfile.create(account, "Task Step User"));
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
        return read(result).path("data").path("accessToken").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findStep(JsonNode response, String stepId) {
        for (JsonNode step : response.path("data").path("steps")) {
            if (stepId.equals(step.path("id").asText())) {
                return step;
            }
        }
        throw new AssertionError("Task Step was not present in the response: " + stepId);
    }

    private record PlanPath(String planId, String versionId, String itemId) {}
}
