package com.codegym.aiplanning.controller.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.codegym.aiplanning.entity.source.LearningSourceStatus;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
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
class RoadmapOnboardingControllerIntegrationTest {

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
    private RoadmapSourceRepository roadmapSourceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void incompleteProfileCannotStartRoadmapOnboarding() throws Exception {
        UserAccount user = createUser(false);

        mockMvc.perform(post(ApiConstant.ROADMAP_ONBOARDING)
                        .header("Authorization", "Bearer " + login(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_SETUP_REQUIRED"));
    }

    @Test
    void wizardStepsCanBeSavedAndReadWithoutLosingEarlierValues() throws Exception {
        UserAccount user = createUser(true);
        String accessToken = login(user);
        UUID roadmapId = start(accessToken);

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Learn Web Development with React\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goal")
                        .value("Learn Web Development with React"));

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proficiencyLevel\":\"BASIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goal")
                        .value("Learn Web Development with React"))
                .andExpect(jsonPath("$.data.proficiencyLevel").value("BASIC"));

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyCommitmentMinutes": 60,
                                  "expectedDurationDays": 90
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(path(roadmapId))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value(roadmapId.toString()))
                .andExpect(jsonPath("$.data.status").value("ONBOARDING"))
                .andExpect(jsonPath("$.data.goal")
                        .value("Learn Web Development with React"))
                .andExpect(jsonPath("$.data.proficiencyLevel").value("BASIC"))
                .andExpect(jsonPath("$.data.dailyCommitmentMinutes").value(60))
                .andExpect(jsonPath("$.data.expectedDurationDays").value(90))
                .andExpect(jsonPath("$.data.completed").value(false));

        mockMvc.perform(get(ApiConstant.ROADMAP_ONBOARDING + ApiConstant.CURRENT)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value(roadmapId.toString()));

        assertThat(start(accessToken)).isEqualTo(roadmapId);
    }

    @Test
    void completionRequiresEveryFieldAndFixedChoiceValues() throws Exception {
        UserAccount user = createUser(true);
        String accessToken = login(user);
        UUID roadmapId = start(accessToken);

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Learn React\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(path(roadmapId) + ApiConstant.COMPLETE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROADMAP_ONBOARDING_INCOMPLETE"));

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyCommitmentMinutes\":45}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedDurationDays\":120}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void completedRoadmapsRemainIndependentWhenOwnerStartsAnotherOnboarding() throws Exception {
        UserAccount owner = createUser(true);
        UserAccount other = createUser(true);
        String ownerToken = login(owner);
        String otherToken = login(other);
        UUID roadmapId = start(ownerToken);

        mockMvc.perform(get(path(roadmapId))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goal": "Backend with Java",
                                  "proficiencyLevel": "BEGINNER",
                                  "dailyCommitmentMinutes": 30,
                                  "expectedDurationDays": 30
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult firstCompletion = mockMvc.perform(post(path(roadmapId) + ApiConstant.COMPLETE)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andReturn();

        String firstCompletedAt = objectMapper
                .readTree(firstCompletion.getResponse().getContentAsString())
                .path("data")
                .path("completedAt")
                .asText();
        mockMvc.perform(post(path(roadmapId) + ApiConstant.COMPLETE)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmapId").value(roadmapId.toString()))
                .andExpect(jsonPath("$.data.completedAt").value(firstCompletedAt));

        mockMvc.perform(get(ApiConstant.ROADMAP_ONBOARDING + ApiConstant.CURRENT)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        UUID secondRoadmapId = start(ownerToken);
        assertThat(secondRoadmapId).isNotEqualTo(roadmapId);
        assertThat(start(ownerToken)).isEqualTo(secondRoadmapId);

        mockMvc.perform(patch(path(secondRoadmapId))
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goal": "Frontend with React",
                                  "proficiencyLevel": "BASIC",
                                  "dailyCommitmentMinutes": 120,
                                  "expectedDurationDays": 90
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(path(secondRoadmapId) + ApiConstant.COMPLETE)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get(path(roadmapId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goal").value("Backend with Java"))
                .andExpect(jsonPath("$.data.proficiencyLevel").value("BEGINNER"))
                .andExpect(jsonPath("$.data.dailyCommitmentMinutes").value(30))
                .andExpect(jsonPath("$.data.expectedDurationDays").value(30));

        mockMvc.perform(get(path(secondRoadmapId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goal").value("Frontend with React"))
                .andExpect(jsonPath("$.data.proficiencyLevel").value("BASIC"))
                .andExpect(jsonPath("$.data.dailyCommitmentMinutes").value(120))
                .andExpect(jsonPath("$.data.expectedDurationDays").value(90));

        mockMvc.perform(get(ApiConstant.ROADMAP_ONBOARDING + ApiConstant.CURRENT)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        assertThat(roadmapRepository.countByOwnerId(owner.getId())).isEqualTo(2);
        assertThat(userProfileRepository.findByUserId(owner.getId())
                        .orElseThrow()
                        .getDefaultDailyMinutes())
                .isEqualTo(60);

        var roadmap = roadmapRepository.findById(roadmapId).orElseThrow();
        var firstGoalSource = roadmapSourceRepository
                .findGoalSourceByRoadmapId(roadmapId)
                .orElseThrow()
                .getLearningSource();
        var secondGoalSource = roadmapSourceRepository
                .findGoalSourceByRoadmapId(secondRoadmapId)
                .orElseThrow()
                .getLearningSource();
        assertThat(roadmap.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(firstGoalSource.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(secondGoalSource.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(firstGoalSource.getId()).isNotEqualTo(secondGoalSource.getId());
        assertThat(firstGoalSource.getStatus()).isEqualTo(LearningSourceStatus.READY);
        assertThat(secondGoalSource.getStatus()).isEqualTo(LearningSourceStatus.READY);

        mockMvc.perform(patch(path(roadmapId))
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Overwrite user goal\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void adminCannotAccessPersonalRoadmapOnboarding() throws Exception {
        UserAccount admin = createAccount(UserRole.ADMIN);

        mockMvc.perform(post(ApiConstant.ROADMAP_ONBOARDING)
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentsRoadmapOnboardingAndItsFixedOptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(document
                        .at("/paths/~1api~1v1~1roadmap-onboarding/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document
                        .at("/paths/~1api~1v1~1roadmap-onboarding~1{roadmapId}/patch")
                        .isMissingNode())
                .isFalse();
        assertThat(document
                        .at("/paths/~1api~1v1~1roadmap-onboarding~1{roadmapId}~1complete/post")
                        .isMissingNode())
                .isFalse();
        assertThat(document
                        .at("/components/schemas/SaveRoadmapOnboardingRequest/properties/"
                                + "dailyCommitmentMinutes/enum")
                        .toString())
                .contains("30", "60", "120");
    }

    private UUID start(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.ROADMAP_ONBOARDING)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("roadmapId")
                .asText());
    }

    private String path(UUID roadmapId) {
        return ApiConstant.ROADMAP_ONBOARDING + "/" + roadmapId;
    }

    private UserAccount createUser(boolean profileComplete) {
        UserAccount user = createAccount(UserRole.USER);
        UserProfile profile = UserProfile.create(user, "Roadmap User");
        if (profileComplete) {
            profile.completeSetup(
                    "Roadmap User", "Asia/Ho_Chi_Minh", "vi", 60, Instant.now());
        }
        userProfileRepository.saveAndFlush(profile);
        return user;
    }

    private UserAccount createAccount(UserRole role) {
        String email = "onboarding-" + UUID.randomUUID() + "@example.com";
        return userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
    }

    private String login(UserAccount user) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(),
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
