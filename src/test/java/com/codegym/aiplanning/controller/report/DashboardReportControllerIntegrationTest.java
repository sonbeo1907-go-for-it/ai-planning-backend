package com.codegym.aiplanning.controller.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
class DashboardReportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ProgressEntryRepository progressEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void getDashboardReport_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(ApiConstant.REPORTS_DASHBOARD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboardReport_authenticated_returnsDashboardStats() throws Exception {
        UserAccount user = createUser("report-user", "Dashboard Learner");
        String accessToken = login(user.getEmail());

        // Initially zero streak and empty study time
        mockMvc.perform(get(ApiConstant.REPORTS_DASHBOARD)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streak.currentStreak").value(0))
                .andExpect(jsonPath("$.data.streak.longestStreak").value(0))
                .andExpect(jsonPath("$.data.streak.isActiveToday").value(false))
                .andExpect(jsonPath("$.data.studyTime.totalStudyMinutes").value(0))
                .andExpect(jsonPath("$.data.studyTime.dailyPoints").isArray())
                .andExpect(jsonPath("$.data.studyTime.dailyPoints.length()").value(7));

        // Add a completed progress entry today
        ProgressEntry entry = ProgressEntry.create(
                user.getId(),
                null,
                ProgressEntryStatus.COMPLETED,
                50,
                100,
                "Finished task",
                2,
                5,
                "All good",
                null);
        progressEntryRepository.save(entry);

        // Query again and verify streak is 1, total minutes is 50
        mockMvc.perform(get(ApiConstant.REPORTS_DASHBOARD)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streak.currentStreak").value(1))
                .andExpect(jsonPath("$.data.streak.longestStreak").value(1))
                .andExpect(jsonPath("$.data.streak.isActiveToday").value(true))
                .andExpect(jsonPath("$.data.studyTime.totalStudyMinutes").value(50))
                .andExpect(jsonPath("$.data.studyTime.totalStudyHours").value(0.8));
    }

    private UserAccount createUser(String prefix, String displayName) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        UserAccount user = UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                UserRole.USER,
                AccountStatus.ACTIVE);
        user = userAccountRepository.save(user);

        UserProfile profile = UserProfile.create(user, displayName);
        userProfileRepository.save(profile);

        return user;
    }

    private String login(String email) throws Exception {
        String payload = String.format("""
                {
                    "email": "%s",
                    "password": "Password@123"
                }
                """, email);

        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("accessToken").asText();
    }
}
