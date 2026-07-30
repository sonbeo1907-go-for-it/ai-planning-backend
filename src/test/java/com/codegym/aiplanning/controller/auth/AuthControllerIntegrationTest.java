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
import com.codegym.aiplanning.repository.auth.AuthSessionRepository;
import com.codegym.aiplanning.repository.auth.RefreshTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
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

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void clearAdminLoginFailures() {
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
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
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(
                        loginResult.getResponse().getHeader("Set-Cookie"))
                .contains("refresh_token=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.size()).isEqualTo(1);
        String token = body.path("data").path("accessToken").asText();
        Jwt jwt = jwtDecoder.decode(token);
        org.assertj.core.api.Assertions.assertThat(jwt.getId()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(jwt.getClaimAsString("sid")).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(jwt.getClaimAsString("typ"))
                .isEqualTo("access");

        mockMvc.perform(get(ApiConstant.PROFILE).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void logoutRevokesCurrentSessionAndClearsRefreshCookie() throws Exception {
        LoginSession login = loginSuccessfully();

        mockMvc.perform(post(ApiConstant.AUTH_LOGOUT)
                        .header("Authorization", "Bearer " + login.accessToken())
                        .cookie(login.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getHeader("Set-Cookie"))
                        .contains("refresh_token=")
                        .contains("Max-Age=0"));

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRefreshCookieAllowsLogoutWhenBearerTokenHasExpired() throws Exception {
        LoginSession login = loginSuccessfully();
        Jwt original = jwtDecoder.decode(login.accessToken());
        Instant now = Instant.now();
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer(original.getClaimAsString("iss"))
                .issuedAt(now.minusSeconds(3600))
                .expiresAt(now.minusSeconds(1800))
                .id(UUID.randomUUID().toString())
                .subject(original.getSubject())
                .claim("typ", "access")
                .claim("sid", original.getClaimAsString("sid"))
                .claim("preferred_username", original.getClaimAsString("preferred_username"))
                .claim("roles", original.getClaimAsStringList("roles"))
                .build();
        String expiredToken = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        expiredClaims))
                .getTokenValue();

        mockMvc.perform(post(ApiConstant.AUTH_LOGOUT)
                        .header("Authorization", "Bearer " + expiredToken)
                        .cookie(login.refreshCookie()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesEntireSession() throws Exception {
        LoginSession login = loginSuccessfully();

        MvcResult refreshResult = mockMvc.perform(post(ApiConstant.AUTH_REFRESH)
                        .cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String refreshedAccessToken = objectMapper
                .readTree(refreshResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        Cookie rotatedCookie = refreshResult.getResponse().getCookie("refresh_token");
        org.assertj.core.api.Assertions.assertThat(rotatedCookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(rotatedCookie.getValue())
                .isNotEqualTo(login.refreshCookie().getValue());

        mockMvc.perform(post(ApiConstant.AUTH_REFRESH).cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION"));

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ApiConstant.AUTH_REFRESH).cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsIdempotentWithoutCredentials() throws Exception {
        mockMvc.perform(post(ApiConstant.AUTH_LOGOUT)).andExpect(status().isNoContent());
        mockMvc.perform(post(ApiConstant.AUTH_LOGOUT)).andExpect(status().isNoContent());
    }

    @Test
    void openApiDocumentsRefreshCookieAndLogoutSecurityAlternatives() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode document =
                objectMapper.readTree(result.getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(document
                        .at("/components/securitySchemes/bearerAuth/type")
                        .asText())
                .isEqualTo("http");
        org.assertj.core.api.Assertions.assertThat(document
                        .at("/components/securitySchemes/refreshCookie/in")
                        .asText())
                .isEqualTo("cookie");
        org.assertj.core.api.Assertions.assertThat(document
                        .at("/paths/~1api~1v1~1auth~1refresh/post/responses/200/headers/Set-Cookie")
                        .isMissingNode())
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(document
                        .at("/paths/~1api~1v1~1auth~1refresh/post/responses/409")
                        .isMissingNode())
                .isFalse();
        org.assertj.core.api.Assertions.assertThat(document
                        .at("/paths/~1api~1v1~1auth~1logout/post/responses/204")
                        .isMissingNode())
                .isFalse();

        JsonNode logoutSecurity =
                document.at("/paths/~1api~1v1~1auth~1logout/post/security");
        org.assertj.core.api.Assertions.assertThat(logoutSecurity.toString())
                .contains("bearerAuth")
                .contains("refreshCookie");
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

    private LoginSession loginSuccessfully() throws Exception {
        MvcResult result = login("admin", "Admin@123")
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        org.assertj.core.api.Assertions.assertThat(refreshCookie).isNotNull();
        return new LoginSession(accessToken, refreshCookie);
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

    private record LoginSession(String accessToken, Cookie refreshCookie) {}
}
