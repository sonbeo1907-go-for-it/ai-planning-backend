package com.codegym.aiplanning.controller.profile;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
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

    @Test
    void studentProfileUsesCurrentDatabaseAccountAndExposesEnrollmentExtensionPoint()
            throws Exception {
        UserAccount student = createAccount("profile-student", UserRole.STUDENT, "Student Profile");
        String accessToken = login(student.getUsername());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(student.getId().toString()))
                .andExpect(jsonPath("$.data.username").value("profile-student"))
                .andExpect(jsonPath("$.data.email").value("profile-student@example.com"))
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
        String accessToken = login(instructor.getUsername());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken))
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
        String accessToken = login(authenticatedStudent.getUsername());

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .queryParam("userId", otherStudent.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(authenticatedStudent.getId().toString()))
                .andExpect(jsonPath("$.data.username").value("profile-self"))
                .andExpect(jsonPath("$.data.email").value("profile-self@example.com"));
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
                username + "@example.com",
                passwordEncoder.encode("Password@123"),
                fullName,
                role,
                AccountStatus.ACTIVE));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "email", username + "@example.com",
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
