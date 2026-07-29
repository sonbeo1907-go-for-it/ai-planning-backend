package com.codegym.aiplanning.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearAdminLoginFailures() {
        userAccountRepository.findByUsernameIgnoreCase("admin").ifPresent(account -> {
            account.clearLoginFailures();
            userAccountRepository.saveAndFlush(account);
        });
    }

    @Test
    void loginAndReadProfile() throws Exception {
        MvcResult loginResult = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "Admin@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = body.path("data").path("accessToken").asText();

        mockMvc.perform(get(ApiConstant.PROFILE).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void rejectInvalidCredentialsWithoutLeakingAccountState() throws Exception {
        login("admin", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void temporarilyBlockAccountAfterConfiguredFailedAttemptLimit() throws Exception {
        login("admin", "wrong-password").andExpect(status().isUnauthorized());
        login("admin", "wrong-password").andExpect(status().isUnauthorized());
        login("admin", "wrong-password").andExpect(status().isUnauthorized());

        UserAccount account =
                userAccountRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(account.getFailedLoginAttempts()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(account.getLoginBlockedUntil()).isNotNull();

        login("admin", "Admin@123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void successfulLoginResetsConsecutiveFailureCount() throws Exception {
        login("admin", "wrong-password").andExpect(status().isUnauthorized());
        login("admin", "wrong-password").andExpect(status().isUnauthorized());

        login("admin", "Admin@123").andExpect(status().isOk());

        UserAccount account =
                userAccountRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(account.getFailedLoginAttempts()).isZero();
        org.assertj.core.api.Assertions.assertThat(account.getLoginBlockedUntil()).isNull();

        login("admin", "wrong-password").andExpect(status().isUnauthorized());
        login("admin", "wrong-password").andExpect(status().isUnauthorized());
        login("admin", "Admin@123").andExpect(status().isOk());
    }

    @Test
    void lockedAndInactiveAccountsReceiveGenericAuthenticationFailure() throws Exception {
        createAccount("locked-user", AccountStatus.LOCKED);
        createAccount("inactive-user", AccountStatus.INACTIVE);

        login("locked-user", "Password@123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
        login("inactive-user", "Password@123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void unknownUsernameReceivesGenericAuthenticationFailure() throws Exception {
        login("missing-user", "Password@123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        java.util.Map.of("username", username, "password", password))));
    }

    private void createAccount(String username, AccountStatus status) {
        userAccountRepository.findByUsernameIgnoreCase(username).ifPresent(userAccountRepository::delete);
        userAccountRepository.saveAndFlush(UserAccount.create(
                username,
                passwordEncoder.encode("Password@123"),
                username,
                UserRole.STUDENT,
                status));
    }
}
