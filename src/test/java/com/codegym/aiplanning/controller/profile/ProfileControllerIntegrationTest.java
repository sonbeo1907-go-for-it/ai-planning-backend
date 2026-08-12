package com.codegym.aiplanning.controller.profile;

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
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ProfileControllerIntegrationTest {

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
    void newUserProfileExposesFirstAccessSetupState() throws Exception {
        UserAccount user = createUser("profile-user", "User Profile");
        String accessToken = login(user.getEmail());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("profile-user@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.profile.displayName").value("User Profile"))
                .andExpect(jsonPath("$.data.profile.timeZone").value("UTC"))
                .andExpect(jsonPath("$.data.profile.locale").value("en"))
                .andExpect(jsonPath("$.data.profile.defaultDailyMinutes").value(60))
                .andExpect(jsonPath("$.data.profile.setupCompleted").value(false))
                .andExpect(jsonPath("$.data.profile.setupCompletedAt").doesNotExist());
    }

    @Test
    void userCompletesFirstAccessSetupWithBrowserTimeZone() throws Exception {
        UserAccount user = createUser("profile-self", "Self Profile");
        UserAccount other = createUser("profile-other", "Other Profile");
        String accessToken = login(user.getEmail());

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .queryParam("userId", other.getId().toString())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Vũ Ngọc Duy",
                                  "timeZone": "Asia/Ho_Chi_Minh",
                                  "locale": "vi-VN",
                                  "defaultDailyMinutes": 90
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.profile.displayName").value("Vũ Ngọc Duy"))
                .andExpect(jsonPath("$.data.profile.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.profile.locale").value("vi-VN"))
                .andExpect(jsonPath("$.data.profile.defaultDailyMinutes").value(90))
                .andExpect(jsonPath("$.data.profile.setupCompleted").value(true))
                .andExpect(jsonPath("$.data.profile.setupCompletedAt").isNotEmpty());

        org.assertj.core.api.Assertions.assertThat(
                        userProfileRepository.findByUserId(other.getId()).orElseThrow().getDisplayName())
                .isEqualTo("Other Profile");
    }

    @Test
    void setupRejectsInvalidTimeZone() throws Exception {
        UserAccount user = createUser("invalid-zone", "Invalid Zone");

        mockMvc.perform(put(ApiConstant.PROFILE + ApiConstant.PROFILE_SETUP)
                        .header("Authorization", "Bearer " + login(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Invalid Zone",
                                  "timeZone": "Browser/Unknown",
                                  "locale": "vi"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void partialUpdateRequiresCompletedInitialSetup() throws Exception {
        UserAccount user = createUser("setup-required", "Setup Required");

        mockMvc.perform(patch(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + login(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultDailyMinutes\":90}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_SETUP_REQUIRED"));
    }

    @Test
    void adminCanReadAccountProfileButCannotCreatePersonalLearningPreferences() throws Exception {
        UserAccount admin = createAccount("profile-admin", UserRole.ADMIN);
        String accessToken = login(admin.getEmail());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.profile").doesNotExist());

        mockMvc.perform(patch(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultDailyMinutes\":90}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentsThatProfileIsForTheAuthenticatedUserOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(document
                        .at("/paths/~1api~1v1~1profile/get/description")
                        .asText())
                .contains("never accepts a target user ID");
    }

    private UserAccount createUser(String emailAlias, String displayName) {
        UserAccount account = createAccount(emailAlias, UserRole.USER);
        userProfileRepository.saveAndFlush(UserProfile.create(account, displayName));
        return account;
    }

    private UserAccount createAccount(String emailAlias, UserRole role) {
        return userAccountRepository.saveAndFlush(UserAccount.create(
                emailAlias + "@example.com",
                passwordEncoder.encode("Password@123"),
                role,
                AccountStatus.ACTIVE));
    }

    private String login(String email) throws Exception {
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
}
