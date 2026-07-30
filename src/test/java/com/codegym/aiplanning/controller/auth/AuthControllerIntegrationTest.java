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
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.size()).isEqualTo(1);
        String token = body.path("data").path("accessToken").asText();

        mockMvc.perform(get(ApiConstant.PROFILE).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void rejectInvalidCredentialsWithoutLeakingAccountState() throws Exception {
        MvcResult result = login("admin", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."))
                .andReturn();

        assertErrorOnly(result, 401, "INVALID_CREDENTIALS");
    }

    @Test
    void validationErrorsContainOnlyStatusCodeAndMessage() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        assertErrorOnly(result, 400, "VALIDATION_FAILED");
    }

    @Test
    void missingBearerTokenUsesTheSameErrorEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(get(ApiConstant.PROFILE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andReturn();

        assertErrorOnly(result, 401, "AUTHENTICATION_REQUIRED");
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

    private void assertErrorOnly(MvcResult result, int status, String code) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.size()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(body.path("status").asInt()).isEqualTo(status);
        org.assertj.core.api.Assertions.assertThat(body.path("code").asText()).isEqualTo(code);
        org.assertj.core.api.Assertions.assertThat(body.path("message").asText()).isNotBlank();
    }
}
