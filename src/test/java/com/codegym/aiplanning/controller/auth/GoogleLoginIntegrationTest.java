package com.codegym.aiplanning.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.AuthProvider;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.AuthIdentityRepository;
import com.codegym.aiplanning.repository.auth.AuthSessionRepository;
import com.codegym.aiplanning.repository.auth.RefreshTokenRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.auth.GoogleIdTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "app.security.google.enabled=true",
    "app.security.google.client-id=google-client-id"
})
class GoogleLoginIntegrationTest {

    private static final String GOOGLE_EMAIL = "google.user@example.com";
    private static final String COLLISION_EMAIL = "existing.local@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @BeforeEach
    void cleanGoogleTestData() {
        reset(googleIdTokenVerifier);
        refreshTokenRepository.deleteAll();
        authSessionRepository.deleteAll();
        authIdentityRepository.deleteAll();
        userAccountRepository.findByEmailIgnoreCase(GOOGLE_EMAIL)
                .ifPresent(userAccountRepository::delete);
        userAccountRepository.findByEmailIgnoreCase(COLLISION_EMAIL)
                .ifPresent(userAccountRepository::delete);
        userAccountRepository.flush();
    }

    @Test
    void firstGoogleLoginCreatesUserWithoutPasswordAndLocalSession() throws Exception {
        stubGoogleIdentity("google-subject-1", GOOGLE_EMAIL);

        MvcResult result = googleLogin("valid-google-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("refresh_token=")
                .contains("HttpOnly");
        UserAccount account = userAccountRepository
                .findByEmailIgnoreCase(GOOGLE_EMAIL)
                .orElseThrow();
        assertThat(account.getRole()).isEqualTo(UserRole.USER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getPasswordHash()).isNull();
        assertThat(account.getUsername()).startsWith("google_");
        assertThat(userProfileRepository.findByUserId(account.getId())).isPresent();

        var identity = authIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject-1")
                .orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(account.getId());
        assertThat(identity.getProviderEmail()).isEqualTo(GOOGLE_EMAIL);
    }

    @Test
    void returningGoogleIdentityUsesExistingAccount() throws Exception {
        stubGoogleIdentity("google-subject-1", GOOGLE_EMAIL);
        googleLogin("valid-google-token").andExpect(status().isOk());
        UserAccount firstAccount = userAccountRepository
                .findByEmailIgnoreCase(GOOGLE_EMAIL)
                .orElseThrow();

        googleLogin("valid-google-token").andExpect(status().isOk());

        assertThat(userAccountRepository.findByEmailIgnoreCase(GOOGLE_EMAIL).orElseThrow().getId())
                .isEqualTo(firstAccount.getId());
        assertThat(authIdentityRepository.count()).isEqualTo(1);
    }

    @Test
    void googleOnlyAccountCannotUseLocalPasswordLogin() throws Exception {
        stubGoogleIdentity("google-subject-1", GOOGLE_EMAIL);
        googleLogin("valid-google-token").andExpect(status().isOk());

        mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", GOOGLE_EMAIL,
                                "password", "Password@123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(userAccountRepository.findByEmailIgnoreCase(GOOGLE_EMAIL)
                        .orElseThrow()
                        .getFailedLoginAttempts())
                .isZero();
    }

    @Test
    void matchingLocalEmailRequiresExplicitLinkInsteadOfAutomaticLink() throws Exception {
        userAccountRepository.saveAndFlush(UserAccount.create(
                "existing_local",
                COLLISION_EMAIL,
                passwordEncoder.encode("Password@123"),
                "Existing Local User",
                UserRole.USER,
                AccountStatus.ACTIVE));
        stubGoogleIdentity("google-subject-collision", COLLISION_EMAIL);

        googleLogin("collision-token")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_LINK_REQUIRED"));

        assertThat(authIdentityRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions googleLogin(String token)
            throws Exception {
        return mockMvc.perform(post(ApiConstant.AUTH_GOOGLE_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("idToken", token))));
    }

    private void stubGoogleIdentity(String subject, String email) {
        when(googleIdTokenVerifier.verify(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new GoogleIdTokenVerifier.GoogleIdentityClaims(
                        subject, email, "Google User"));
    }
}
