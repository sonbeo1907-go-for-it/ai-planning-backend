package com.codegym.aiplanning.controller.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
class ManualRoadmapControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void userBuildsOrdersActivatesAndVersionsAnIndependentManualRoadmap() throws Exception {
        String token = login(createAccount(UserRole.USER));
        CreatedRoadmap created = createRoadmap(token, "Backend with Java");

        UUID weekTwo = addMilestone(token, created, "Week 2", 0);
        UUID weekOne = addMilestone(token, created, "Week 1", 0);
        UUID javaBasics = addTopic(token, created, weekOne, "Java basics", 0, 60);
        addTopic(token, created, weekTwo, "Spring Boot", 0, 120);

        mockMvc.perform(patch(itemPath(created, javaBasics))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Java OOP fundamentals",
                                  "description": "Classes, interfaces, and composition",
                                  "orderIndex": 0,
                                  "estimatedMinutes": 90
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Java OOP fundamentals"))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(90));

        mockMvc.perform(get(versionPath(created))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.milestones[0].title").value("Week 1"))
                .andExpect(jsonPath("$.data.milestones[0].orderIndex").value(0))
                .andExpect(jsonPath("$.data.milestones[1].title").value("Week 2"))
                .andExpect(jsonPath("$.data.milestones[0].topics[0].title")
                        .value("Java OOP fundamentals"));

        mockMvc.perform(post(versionPath(created) + "/activate")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activatedAt").isNotEmpty());

        mockMvc.perform(patch(itemPath(created, javaBasics))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Overwrite active content",
                                  "orderIndex": 0,
                                  "estimatedMinutes": 30
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        MvcResult nextDraftResult = mockMvc.perform(post(roadmapPath(created.roadmapId()) + "/versions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNumber").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.origin").value("USER_EDITED"))
                .andExpect(jsonPath("$.data.milestones[0].topics[0].title")
                        .value("Java OOP fundamentals"))
                .andReturn();
        UUID nextVersionId = dataId(nextDraftResult);

        mockMvc.perform(post(roadmapPath(created.roadmapId()) + "/versions/" + nextVersionId + "/activate")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get(roadmapPath(created.roadmapId()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activeVersionId").value(nextVersionId.toString()))
                .andExpect(jsonPath("$.data.versions[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.versions[1].status").value("SUPERSEDED"));
    }

    @Test
    void activationRequiresEveryMilestoneToContainATopic() throws Exception {
        String token = login(createAccount(UserRole.USER));
        CreatedRoadmap created = createRoadmap(token, "Frontend with React");
        addMilestone(token, created, "Week 1", null);

        mockMvc.perform(post(versionPath(created) + "/activate")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROADMAP_STRUCTURE_INCOMPLETE"));
    }

    @Test
    void draftTopicsAndMilestonesCanBeDeletedWithoutChangingDailyPlanCode() throws Exception {
        String token = login(createAccount(UserRole.USER));
        CreatedRoadmap created = createRoadmap(token, "Delete draft structure");
        UUID milestoneId = addMilestone(token, created, "Temporary week", null);
        UUID topicId = addTopic(token, created, milestoneId, "Temporary topic", 0, 30);

        mockMvc.perform(delete(itemPath(created, topicId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(itemPath(created, milestoneId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(versionPath(created))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.milestones").isEmpty());
    }

    @Test
    void personalRoadmapsAreOwnerScopedAndAdminIsDenied() throws Exception {
        String ownerToken = login(createAccount(UserRole.USER));
        String otherToken = login(createAccount(UserRole.USER));
        String adminToken = login(createAccount(UserRole.ADMIN));
        CreatedRoadmap created = createRoadmap(ownerToken, "Private Roadmap");

        mockMvc.perform(get(roadmapPath(created.roadmapId()))
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ApiConstant.ROADMAPS)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post(roadmapPath(created.roadmapId()) + "/generate-ai")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get(ApiConstant.AI_EXECUTIONS + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void completedOnboardingRoadmapInitializesItsFirstContentVersionWithoutDuplication()
            throws Exception {
        String token = login(createAccount(UserRole.USER));
        MvcResult startResult = mockMvc.perform(post(ApiConstant.ROADMAP_ONBOARDING)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        UUID roadmapId = UUID.fromString(data(startResult).path("roadmapId").asText());

        mockMvc.perform(patch(ApiConstant.ROADMAP_ONBOARDING + "/" + roadmapId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goal": "Learn TypeScript",
                                  "proficiencyLevel": "BEGINNER",
                                  "dailyCommitmentMinutes": 60,
                                  "expectedDurationDays": 60
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post(ApiConstant.ROADMAP_ONBOARDING + "/" + roadmapId + "/complete")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post(roadmapPath(roadmapId) + "/versions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get(roadmapPath(roadmapId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(roadmapId.toString()))
                .andExpect(jsonPath("$.data.versions.length()").value(1));
    }

    @Test
    void listReturnsNestedContentForMultipleRoadmaps() throws Exception {
        String token = login(createAccount(UserRole.USER));
        CreatedRoadmap backend = createRoadmap(token, "Backend with Java");
        CreatedRoadmap frontend = createRoadmap(token, "Frontend with React");
        UUID backendMilestone = addMilestone(token, backend, "Backend Week 1", 0);
        UUID frontendMilestone = addMilestone(token, frontend, "Frontend Week 1", 0);
        addTopic(token, backend, backendMilestone, "Spring Boot", 0, 60);
        addTopic(token, frontend, frontendMilestone, "React", 0, 45);

        MvcResult result = mockMvc.perform(get(ApiConstant.ROADMAPS)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

        assertThat(data(result).findValuesAsText("title"))
                .contains(
                        "Backend with Java",
                        "Backend Week 1",
                        "Spring Boot",
                        "Frontend with React",
                        "Frontend Week 1",
                        "React");
    }

    @Test
    void swaggerDocumentsManualRoadmapEndpoints() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(document.at("/paths/~1api~1v1~1roadmaps/post").isMissingNode()).isFalse();
        assertThat(document.at("/paths/~1api~1v1~1roadmaps~1{roadmapId}~1versions~1{versionId}~1milestones/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1roadmaps~1{roadmapId}~1versions~1{versionId}~1activate/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1roadmaps~1{roadmapId}~1generate-ai/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1roadmaps~1{roadmapId}~1generate-ai/post/responses/202")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1roadmaps~1{roadmapId}~1regenerate-ai/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1roadmaps~1{roadmapId}~1ai-executions~1current/get")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1ai-executions~1{executionId}/get")
                        .isMissingNode())
                .isFalse();
    }

    private CreatedRoadmap createRoadmap(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.ROADMAPS)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "description", "User-authored Roadmap"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.versions[0].versionNumber").value(1))
                .andExpect(jsonPath("$.data.versions[0].status").value("DRAFT"))
                .andReturn();
        JsonNode data = data(result);
        return new CreatedRoadmap(
                UUID.fromString(data.path("id").asText()),
                UUID.fromString(data.path("versions").get(0).path("id").asText()));
    }

    private UUID addMilestone(
            String token, CreatedRoadmap created, String title, Integer orderIndex)
            throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("title", title);
        if (orderIndex != null) {
            payload.put("orderIndex", orderIndex);
        }
        MvcResult result = mockMvc.perform(post(versionPath(created) + "/milestones")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        return dataId(result);
    }

    private UUID addTopic(
            String token,
            CreatedRoadmap created,
            UUID milestoneId,
            String title,
            int orderIndex,
            int estimatedMinutes)
            throws Exception {
        MvcResult result = mockMvc.perform(post(versionPath(created) + "/milestones/" + milestoneId + "/topics")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "orderIndex", orderIndex,
                                "estimatedMinutes", estimatedMinutes))))
                .andExpect(status().isOk())
                .andReturn();
        return dataId(result);
    }

    private UserAccount createAccount(UserRole role) {
        String email = "manual-roadmap-" + UUID.randomUUID() + "@example.com";
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
        UserProfile profile = UserProfile.create(account, "Manual Roadmap User");
        profile.completeSetup(
                "Manual Roadmap User", "Asia/Ho_Chi_Minh", "vi", 60, Instant.now());
        userProfileRepository.saveAndFlush(profile);
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

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data");
    }

    private UUID dataId(MvcResult result) throws Exception {
        return UUID.fromString(data(result).path("id").asText());
    }

    private String roadmapPath(UUID roadmapId) {
        return ApiConstant.ROADMAPS + "/" + roadmapId;
    }

    private String versionPath(CreatedRoadmap created) {
        return roadmapPath(created.roadmapId()) + "/versions/" + created.versionId();
    }

    private String itemPath(CreatedRoadmap created, UUID itemId) {
        return versionPath(created) + "/items/" + itemId;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record CreatedRoadmap(UUID roadmapId, UUID versionId) {}
}
