package com.codegym.aiplanning.controller.profile;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class ProfileTimezoneAndStudyDurationIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Ho_Chi_Minh", "UTC", "America/New_York", "Europe/London"})
    @DisplayName("AC1, AC2: Setup profile accepts valid IANA timezones and does not mutate them")
    void setupAcceptsValidIanaTimeZones(String timeZone) throws Exception {
        UserAccount user = createUser("valid-tz-" + UUID.randomUUID().toString().substring(0, 8));
        String token = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Valid TZ Learner",
                                  "timeZone": "%s",
                                  "locale": "vi",
                                  "defaultDailyMinutes": 60
                                }
                                """.formatted(timeZone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.timeZone").value(timeZone))
                .andExpect(jsonPath("$.data.profile.defaultDailyMinutes").value(60));

        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(profile.getTimeZone()).isEqualTo(timeZone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("AC3: Setup profile rejects blank timezone with TIMEZONE_REQUIRED")
    void setupRejectsBlankTimeZone(String blankTz) throws Exception {
        UserAccount user = createUser("blank-tz-" + UUID.randomUUID().toString().substring(0, 8));
        String token = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Blank TZ Learner",
                                  "timeZone": "%s",
                                  "locale": "vi",
                                  "defaultDailyMinutes": 60
                                }
                                """.formatted(blankTz)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[?(@.field == 'timeZone')].message").value("TIMEZONE_REQUIRED"));
    }

    @Test
    @DisplayName("AC3: Setup profile rejects missing timezone with TIMEZONE_REQUIRED")
    void setupRejectsMissingTimeZone() throws Exception {
        UserAccount user = createUser("missing-tz-" + UUID.randomUUID().toString().substring(0, 8));
        String token = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Missing TZ Learner",
                                  "locale": "vi",
                                  "defaultDailyMinutes": 60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[?(@.field == 'timeZone')].message").value("TIMEZONE_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC+7", "Vietnam", "abc", "GMT+7", "Asia/SaigonCityUnknown"})
    @DisplayName("AC3: Setup profile rejects invalid IANA or free-text timezones with TIMEZONE_INVALID")
    void setupRejectsInvalidAndFreeTextTimeZones(String invalidTz) throws Exception {
        UserAccount user = createUser("invalid-tz-" + UUID.randomUUID().toString().substring(0, 8));
        String token = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Invalid TZ Learner",
                                  "timeZone": "%s",
                                  "locale": "vi",
                                  "defaultDailyMinutes": 60
                                }
                                """.formatted(invalidTz)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[?(@.field == 'timeZone')].message").value("TIMEZONE_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 240, 360, 480})
    @DisplayName("AC4: Setup profile accepts boundary study durations (15m, 4h, 6h, 8h)")
    void setupAcceptsBoundaryDurations(int durationMinutes) throws Exception {
        UserAccount user = createUser("dur-" + durationMinutes + "-" + UUID.randomUUID().toString().substring(0, 6));
        String token = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Duration Learner",
                                  "timeZone": "Asia/Ho_Chi_Minh",
                                  "locale": "vi",
                                  "defaultDailyMinutes": %d
                                }
                                """.formatted(durationMinutes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.defaultDailyMinutes").value(durationMinutes));

        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(profile.getDefaultDailyMinutes()).isEqualTo(durationMinutes);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 14, 495, 600, -15})
    @DisplayName("AC4: Setup profile rejects durations outside 15-480 minutes range")
    void setupRejectsOutOfRangeDurations(int invalidMinutes) throws Exception {
        UserAccount user = createUser("oor-" + invalidMinutes + "-" + UUID.randomUUID().toString().substring(0, 6));
        String token = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Duration Learner",
                                  "timeZone": "Asia/Ho_Chi_Minh",
                                  "locale": "vi",
                                  "defaultDailyMinutes": %d
                                }
                                """.formatted(invalidMinutes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 25, 37, 65, 479})
    @DisplayName("AC4: Setup profile rejects durations that are not multiples of 15 minutes")
    void setupRejectsNonFifteenMinuteIncrements(int nonStepMinutes) throws Exception {
        UserAccount user = createUser("step-" + nonStepMinutes + "-" + UUID.randomUUID().toString().substring(0, 6));
        String token = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Step Learner",
                                  "timeZone": "Asia/Ho_Chi_Minh",
                                  "locale": "vi",
                                  "defaultDailyMinutes": %d
                                }
                                """.formatted(nonStepMinutes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("AC2, AC3: Patch profile validates IANA timezone and rejects invalid formats")
    void patchProfileValidatesTimeZone() throws Exception {
        UserAccount user = createUser("patch-tz-" + UUID.randomUUID().toString().substring(0, 8));
        String token = login(user.getEmail());

        // Complete setup first
        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Setup Complete",
                                  "timeZone": "UTC",
                                  "locale": "vi",
                                  "defaultDailyMinutes": 60
                                }
                                """))
                .andExpect(status().isOk());

        // PATCH with valid IANA timezone
        mockMvc.perform(patch(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\": \"Asia/Ho_Chi_Minh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.timeZone").value("Asia/Ho_Chi_Minh"));

        // PATCH with invalid timezone
        mockMvc.perform(patch(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\": \"UTC+7\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[?(@.field == 'timeZone')].message").value("TIMEZONE_INVALID"));

        // PATCH with non-existent timezone
        mockMvc.perform(patch(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\": \"Vietnam\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[?(@.field == 'timeZone')].message").value("TIMEZONE_INVALID"));
    }

    @Test
    @DisplayName("AC5: Scope isolation - Account Default study duration and Roadmap Commitment are completely isolated")
    void testIsolationBetweenAccountDefaultAndRoadmapCommitment() throws Exception {
        UserAccount user = createUser("isolation-" + UUID.randomUUID().toString().substring(0, 8));
        String token = login(user.getEmail());

        // 1. User completes profile setup with Account Default = 60 minutes
        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Isolated Learner",
                                  "timeZone": "Asia/Ho_Chi_Minh",
                                  "locale": "vi",
                                  "defaultDailyMinutes": 60
                                }
                                """))
                .andExpect(status().isOk());

        UserProfile profileAfterSetup = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(profileAfterSetup.getDefaultDailyMinutes()).isEqualTo(60);

        // 2. User starts a Roadmap onboarding draft
        MvcResult startResult = mockMvc.perform(post(ApiConstant.ROADMAP_ONBOARDING)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        UUID roadmapId = UUID.fromString(objectMapper.readTree(startResult.getResponse().getContentAsString())
                .path("data").path("roadmapId").asText());

        // Update roadmap commitment to 120 minutes
        mockMvc.perform(patch(ApiConstant.ROADMAP_ONBOARDING + "/" + roadmapId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyCommitmentMinutes": 120,
                                  "expectedDurationDays": 60,
                                  "entityVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyCommitmentMinutes").value(120));

        // Verify isolation after roadmap commit:
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElseThrow();
        assertThat(roadmap.getDailyCommitmentMinutes()).isEqualTo(120);

        UserProfile profileAfterRoadmap = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        // Account default MUST remain 60!
        assertThat(profileAfterRoadmap.getDefaultDailyMinutes()).isEqualTo(60);

        // 3. User updates Account Default study duration to 240 minutes via profile API
        mockMvc.perform(patch(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultDailyMinutes\": 240}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.defaultDailyMinutes").value(240));

        // Verify isolation after profile update:
        UserProfile profileAfterProfileUpdate = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(profileAfterProfileUpdate.getDefaultDailyMinutes()).isEqualTo(240);

        // Existing Roadmap commitment MUST STILL remain 120!
        Roadmap roadmapAfterProfileUpdate = roadmapRepository.findById(roadmapId).orElseThrow();
        assertThat(roadmapAfterProfileUpdate.getDailyCommitmentMinutes()).isEqualTo(120);

        // 4. User updates Roadmap commitment to 360 minutes via roadmap onboarding API
        mockMvc.perform(patch(ApiConstant.ROADMAP_ONBOARDING + "/" + roadmapId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dailyCommitmentMinutes": 360,
                                  "entityVersion": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyCommitmentMinutes").value(360));

        // Verify isolation after roadmap update:
        Roadmap roadmapAfterSecondUpdate = roadmapRepository.findById(roadmapId).orElseThrow();
        assertThat(roadmapAfterSecondUpdate.getDailyCommitmentMinutes()).isEqualTo(360);

        // Account default MUST STILL remain 240!
        UserProfile profileAfterSecondRoadmapUpdate = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(profileAfterSecondRoadmapUpdate.getDefaultDailyMinutes()).isEqualTo(240);
    }

    private UserAccount createUser(String emailAlias) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                emailAlias + "@example.com",
                passwordEncoder.encode("Password@123"),
                UserRole.USER,
                AccountStatus.ACTIVE));
        userProfileRepository.saveAndFlush(UserProfile.create(account, emailAlias));
        return account;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
