package com.codegym.aiplanning.controller.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void studentProfileUsesCurrentDatabaseAccountAndExposesEnrollmentExtensionPoint()
            throws Exception {
        UserAccount student = createAccount("profile-student", UserRole.STUDENT, "Student Profile");

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + tokenFor(student, "wrong-token-name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(student.getId().toString()))
                .andExpect(jsonPath("$.data.username").value("profile-student"))
                .andExpect(jsonPath("$.data.fullName").value("Student Profile"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.student.currentEnrollments").isEmpty())
                .andExpect(jsonPath("$.data.instructor").doesNotExist());
    }

    @Test
    void instructorProfileExposesAssignedClassExtensionPoint() throws Exception {
        UserAccount instructor =
                createAccount("profile-instructor", UserRole.INSTRUCTOR, "Instructor Profile");

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + tokenFor(instructor, "wrong-token-name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.data.instructor.assignedClasses").isEmpty())
                .andExpect(jsonPath("$.data.student").doesNotExist());
    }

    @Test
    void targetUserIdInRequestCannotChangeWhoseProfileIsReturned() throws Exception {
        UserAccount authenticatedStudent =
                createAccount("profile-self", UserRole.STUDENT, "Self Profile");
        UserAccount otherStudent = createAccount("profile-other", UserRole.STUDENT, "Other Profile");

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .queryParam("userId", otherStudent.getId().toString())
                        .header("Authorization", "Bearer " + tokenFor(authenticatedStudent, "wrong-token-name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(authenticatedStudent.getId().toString()))
                .andExpect(jsonPath("$.data.username").value("profile-self"));
    }

    @Test
    void noProfileIsExposedWhenTheAuthenticatedAccountNoLongerExists() throws Exception {
        MvcResult result = mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + tokenFor(UUID.randomUUID(), "unknown-user")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.size()).isEqualTo(3);
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

    private UserAccount createAccount(String username, UserRole role, String fullName) {
        userAccountRepository.findByUsernameIgnoreCase(username).ifPresent(userAccountRepository::delete);
        return userAccountRepository.saveAndFlush(UserAccount.create(
                username,
                passwordEncoder.encode("Password@123"),
                fullName,
                role,
                AccountStatus.ACTIVE));
    }

    private String tokenFor(UserAccount account, String tokenUsername) {
        return tokenFor(account.getId(), tokenUsername);
    }

    private String tokenFor(UUID userId, String tokenUsername) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ai-planning-backend-test")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .subject(userId.toString())
                .claim("uid", userId.toString())
                .claim("preferred_username", tokenUsername)
                .claim("full_name", "Incorrect JWT Display Name")
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
