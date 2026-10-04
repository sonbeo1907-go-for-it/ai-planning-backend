package com.codegym.aiplanning.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.common.validation.password.PasswordPolicy;
import com.codegym.aiplanning.controller.auth.dto.PasswordResetConfirmRequest;
import com.codegym.aiplanning.controller.auth.dto.RegisterRequest;
import com.codegym.aiplanning.controller.profile.dto.ChangePasswordRequest;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UnifiedPasswordPolicyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("Flow 1: Register Flow - Frontend Bypass Tests")
    class RegisterFlowTests {

        @Test
        @DisplayName("Register with password missing uppercase returns PASSWORD_MISSING_UPPERCASE")
        void register_MissingUppercase_ReturnsBadRequestWithSpecificCode() throws Exception {
            String email = "test-noupper-" + UUID.randomUUID() + "@example.com";
            String payload = String.format("""
                    {
                      "email": "%s",
                      "password": "lowercase123",
                      "displayName": "No Upper"
                    }
                    """, email);

            mockMvc.perform(post(ApiConstant.AUTH_REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field == 'password')].code")
                            .value(PasswordPolicy.CODE_MISSING_UPPERCASE));
        }

        @Test
        @DisplayName("Register with password missing digit returns PASSWORD_MISSING_DIGIT")
        void register_MissingDigit_ReturnsBadRequestWithSpecificCode() throws Exception {
            String email = "test-nodigit-" + UUID.randomUUID() + "@example.com";
            String payload = String.format("""
                    {
                      "email": "%s",
                      "password": "PasswordOnly",
                      "displayName": "No Digit"
                    }
                    """, email);

            mockMvc.perform(post(ApiConstant.AUTH_REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field == 'password')].code")
                            .value(PasswordPolicy.CODE_MISSING_DIGIT));
        }

        @Test
        @DisplayName("Register with password too short returns PASSWORD_TOO_SHORT")
        void register_TooShort_ReturnsBadRequestWithSpecificCode() throws Exception {
            String email = "test-short-" + UUID.randomUUID() + "@example.com";
            String payload = String.format("""
                    {
                      "email": "%s",
                      "password": "Pass1",
                      "displayName": "Short"
                    }
                    """, email);

            mockMvc.perform(post(ApiConstant.AUTH_REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field == 'password')].code")
                            .value(PasswordPolicy.CODE_TOO_SHORT));
        }

        @Test
        @DisplayName("Register with password containing whitespace returns PASSWORD_CONTAINS_WHITESPACE")
        void register_Whitespace_ReturnsBadRequestWithSpecificCode() throws Exception {
            String email = "test-space-" + UUID.randomUUID() + "@example.com";
            String payload = String.format("""
                    {
                      "email": "%s",
                      "password": "Pass word123",
                      "displayName": "Space"
                    }
                    """, email);

            mockMvc.perform(post(ApiConstant.AUTH_REGISTER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field == 'password')].code")
                            .value(PasswordPolicy.CODE_CONTAINS_WHITESPACE));
        }
    }

    @Nested
    @DisplayName("Flow 2: Reset Password Flow - Frontend Bypass Tests")
    class ResetPasswordFlowTests {

        @Test
        @DisplayName("Reset password with weak password returns PASSWORD_MISSING_DIGIT")
        void resetPassword_MissingDigit_ReturnsBadRequestWithSpecificCode() throws Exception {
            String payload = """
                    {
                      "token": "dummy-token",
                      "newPassword": "PasswordOnly"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field == 'newPassword')].code")
                            .value(PasswordPolicy.CODE_MISSING_DIGIT));
        }

        @Test
        @DisplayName("Reset password with whitespace returns PASSWORD_CONTAINS_WHITESPACE")
        void resetPassword_Whitespace_ReturnsBadRequestWithSpecificCode() throws Exception {
            String payload = """
                    {
                      "token": "dummy-token",
                      "newPassword": "Pass word123"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field == 'newPassword')].code")
                            .value(PasswordPolicy.CODE_CONTAINS_WHITESPACE));
        }
    }

    @Nested
    @DisplayName("Flow 3: Change Password Flow - Frontend Bypass Tests")
    class ChangePasswordFlowTests {

        @Test
        @DisplayName("Change password with weak new password returns PASSWORD_MISSING_UPPERCASE")
        void changePassword_MissingUppercase_ReturnsBadRequestWithSpecificCode() throws Exception {
            String email = "changepass-" + UUID.randomUUID() + "@example.com";
            userAccountRepository.saveAndFlush(UserAccount.create(
                    email,
                    passwordEncoder.encode("ValidPass123"),
                    UserRole.USER,
                    AccountStatus.ACTIVE));

            // Login to get access token
            MvcResult loginResult = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    java.util.Map.of("email", email, "password", "ValidPass123"))))
                    .andExpect(status().isOk())
                    .andReturn();
            String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                    .path("data").path("accessToken").asText();

            String payload = """
                    {
                      "currentPassword": "ValidPass123",
                      "newPassword": "weakpassword123",
                      "confirmPassword": "weakpassword123"
                    }
                    """;

            mockMvc.perform(put("/api/v1/profile/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field == 'newPassword')].code")
                            .value(PasswordPolicy.CODE_MISSING_UPPERCASE));
        }
    }

    @Nested
    @DisplayName("Security & Log Leakage Tests")
    class SecurityAndLeakageTests {

        @Test
        @DisplayName("DTO toString methods never expose raw passwords")
        void dtoToString_MasksPasswords() {
            String secret = "VerySecretPassword123";

            RegisterRequest reg = new RegisterRequest("test@example.com", secret, "Name");
            assertThat(reg.toString()).doesNotContain(secret);
            assertThat(reg.toString()).contains("[PROTECTED]");

            PasswordResetConfirmRequest reset = new PasswordResetConfirmRequest("token", secret);
            assertThat(reset.toString()).doesNotContain(secret);
            assertThat(reset.toString()).contains("[PROTECTED]");

            ChangePasswordRequest change = new ChangePasswordRequest("oldSecret", secret, secret);
            assertThat(change.toString()).doesNotContain(secret);
            assertThat(change.toString()).doesNotContain("oldSecret");
            assertThat(change.toString()).contains("[PROTECTED]");
        }

        @Test
        @DisplayName("Failed validation requests never log the raw password")
        void validationFailure_DoesNotLogRawPassword() throws Exception {
            String rawPassword = "UniquePasswordNoDigitToLog";

            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
            listAppender.start();
            rootLogger.addAppender(listAppender);

            try {
                String payload = objectMapper.writeValueAsString(
                        new RegisterRequest("leak-check-" + UUID.randomUUID() + "@example.com", rawPassword, "User"));

                mockMvc.perform(post(ApiConstant.AUTH_REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isBadRequest());

                boolean passwordFoundInLogs = listAppender.list.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains(rawPassword));

                assertThat(passwordFoundInLogs).isFalse();
            } finally {
                rootLogger.detachAppender(listAppender);
            }
        }
    }

    @Nested
    @DisplayName("OpenAPI Contract Consistency Tests")
    class OpenApiContractTests {

        @Test
        @DisplayName("OpenAPI schema reflects unified password min/max length constraints")
        void openApi_ReflectsUnifiedPolicyConstraints() throws Exception {
            MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode schemas = doc.at("/components/schemas");

            // RegisterRequest password schema
            JsonNode regPassword = schemas.at("/RegisterRequest/properties/password");
            assertThat(regPassword.at("/minLength").asInt()).isEqualTo(PasswordPolicy.MIN_LENGTH);
            assertThat(regPassword.at("/maxLength").asInt()).isEqualTo(PasswordPolicy.MAX_LENGTH);

            // PasswordResetConfirmRequest newPassword schema
            JsonNode resetPassword = schemas.at("/PasswordResetConfirmRequest/properties/newPassword");
            assertThat(resetPassword.at("/minLength").asInt()).isEqualTo(PasswordPolicy.MIN_LENGTH);
            assertThat(resetPassword.at("/maxLength").asInt()).isEqualTo(PasswordPolicy.MAX_LENGTH);

            // ChangePasswordRequest newPassword schema
            JsonNode changePassword = schemas.at("/ChangePasswordRequest/properties/newPassword");
            assertThat(changePassword.at("/minLength").asInt()).isEqualTo(PasswordPolicy.MIN_LENGTH);
            assertThat(changePassword.at("/maxLength").asInt()).isEqualTo(PasswordPolicy.MAX_LENGTH);
        }
    }
}
