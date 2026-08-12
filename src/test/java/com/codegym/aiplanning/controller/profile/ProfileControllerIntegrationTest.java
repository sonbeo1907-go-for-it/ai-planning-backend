package com.codegym.aiplanning.controller.profile;

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
    void userProfileReturnsPersonalLearningPreferences() throws Exception {
        UserAccount user = createUser("profile-user", "User Profile");
        String accessToken = login(user.getUsername());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.username").value("profile-user"))
                .andExpect(jsonPath("$.data.email").value("profile-user@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("User Profile"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.preferences.timeZone").value("UTC"))
                .andExpect(jsonPath("$.data.preferences.locale").value("en"))
                .andExpect(jsonPath("$.data.preferences.defaultDailyMinutes").value(60));
    }

    @Test
    void userCanUpdateOnlyTheirOwnLearningProfile() throws Exception {
        UserAccount user = createUser("profile-self", "Self Profile");
        UserAccount other = createUser("profile-other", "Other Profile");
        String accessToken = login(user.getUsername());

        mockMvc.perform(patch(ApiConstant.PROFILE)
                        .queryParam("userId", other.getId().toString())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated User",
                                  "timeZone": "Asia/Ho_Chi_Minh",
                                  "locale": "vi-VN",
                                  "defaultDailyMinutes": 90,
                                  "learningPreferences": "Prefer concise examples"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.fullName").value("Updated User"))
                .andExpect(jsonPath("$.data.preferences.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.preferences.locale").value("vi-VN"))
                .andExpect(jsonPath("$.data.preferences.defaultDailyMinutes").value(90))
                .andExpect(jsonPath("$.data.preferences.learningPreferences")
                        .value("Prefer concise examples"));

        org.assertj.core.api.Assertions.assertThat(
                        userAccountRepository.findById(other.getId()).orElseThrow().getFullName())
                .isEqualTo("Other Profile");
    }

    @Test
    void adminCanReadAccountProfileButCannotCreatePersonalLearningPreferences() throws Exception {
        UserAccount admin = createAccount("profile-admin", "Profile Administrator", UserRole.ADMIN);
        String accessToken = login(admin.getUsername());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.preferences").doesNotExist());

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

    private UserAccount createUser(String username, String fullName) {
        UserAccount account = createAccount(username, fullName, UserRole.USER);
        userProfileRepository.saveAndFlush(UserProfile.create(account));
        return account;
    }

    private UserAccount createAccount(String username, String fullName, UserRole role) {
        return userAccountRepository.saveAndFlush(UserAccount.create(
                username,
                username + "@example.com",
                passwordEncoder.encode("Password@123"),
                fullName,
                role,
                AccountStatus.ACTIVE));
    }

    private String login(String username) throws Exception {
        String email = username + "@example.com";
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
