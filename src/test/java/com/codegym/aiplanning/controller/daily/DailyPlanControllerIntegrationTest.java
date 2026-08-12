package com.codegym.aiplanning.controller.daily;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
    private PasswordEncoder passwordEncoder;

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

        String addTaskPayload = """
                {
                    "title": "Học Lập trình Java Core Module 1",
                    "description": "Thực hành chuỗi và mảng trong Java",
                    "category": "NEW_MATERIAL",
                    "plannedMinutes": 45
                }
                """;

        MvcResult addTaskResult = mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + planId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTaskPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Học Lập trình Java Core Module 1"))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andReturn();

        String itemId = objectMapper.readTree(addTaskResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

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

        String checkStatusPayload = """
                {
                    "status": "COMPLETED",
                    "actualMinutes": 45
                }
                """;

        mockMvc.perform(patch(ApiConstant.DAILY_PLANS + "/" + planId + "/items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkStatusPayload))
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

        mockMvc.perform(get(ApiConstant.DAILY_PLANS + "/" + ownerPlanId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(ApiConstant.DAILY_PLANS + "/" + ownerPlanId + "/items")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Attacker Task\", \"plannedMinutes\": 30}"))
                .andExpect(status().isNotFound());
    }

    private UserAccount createUser(String username, String fullName) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                username,
                username + "@example.com",
                passwordEncoder.encode("Password@123"),
                fullName,
                UserRole.USER,
                AccountStatus.ACTIVE));
        userProfileRepository.saveAndFlush(UserProfile.create(account));
        return account;
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
