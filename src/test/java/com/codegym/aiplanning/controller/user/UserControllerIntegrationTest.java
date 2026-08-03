package com.codegym.aiplanning.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.audit.AuditLog;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult loginResult = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@aiplanning.local",
                                  "password": "Admin@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        adminToken = body.path("data").path("accessToken").asText();
    }

    @Test
    void createUser_asAdmin_success_andAuditLogPersisted() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student_create_test",
                                  "email": "student_create_test@example.com",
                                  "password": "Password@123",
                                  "fullName": "Student Create Test",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("student_create_test"))
                .andExpect(jsonPath("$.data.email").value("student_create_test@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Student Create Test"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String createdUserId = responseJson.path("data").path("id").asText();

        // Verify Audit log for CREATE
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).anyMatch(log ->
                log.getAction() == AuditEventAction.USER_CREATED &&
                log.getTargetResource().equals("USER") &&
                log.getTargetId().equals(createdUserId) &&
                log.getActorUsername().equals("admin"));
    }

    @Test
    void createUser_missingRequiredFields_returnsValidationFailed() throws Exception {
        mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": "",
                                  "fullName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createUser_duplicateUsername_returnsConflict() throws Exception {
        mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "email": "duplicate-admin@example.com",
                                  "password": "Password@123",
                                  "fullName": "Duplicate Admin",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void createUser_duplicateEmail_returnsConflict() throws Exception {
        mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "different_admin_code",
                                  "email": "ADMIN@AIPLANNING.LOCAL",
                                  "password": "Password@123",
                                  "fullName": "Duplicate Admin Email",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void getUsers_asAdmin_returnsPaginatedList() throws Exception {
        mockMvc.perform(get(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    void updateUser_and_deactivateUser_asAdmin_success_andAuditLogsPersisted() throws Exception {
        // 1. Create user
        MvcResult createResult = mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "instructor_flow_test",
                                  "email": "instructor_flow_test@example.com",
                                  "password": "Password@123",
                                  "fullName": "Instructor Original",
                                  "role": "INSTRUCTOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String userId = createdJson.path("data").path("id").asText();

        // 2. Update user profile (UPDATE audit log)
        mockMvc.perform(put(ApiConstant.USERS + "/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "instructor_updated@example.com",
                                  "fullName": "Instructor Updated",
                                  "role": "INSTRUCTOR",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Instructor Updated"))
                .andExpect(jsonPath("$.data.email").value("instructor_updated@example.com"));

        // Verify Audit log for UPDATE
        List<AuditLog> auditLogsAfterUpdate = auditLogRepository.findAll();
        assertThat(auditLogsAfterUpdate).anyMatch(log ->
                log.getAction() == AuditEventAction.USER_UPDATED &&
                log.getTargetId().equals(userId));

        // 3. Update user status to LOCKED (STATUS_CHANGED audit log)
        mockMvc.perform(put(ApiConstant.USERS + "/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Instructor Updated",
                                  "role": "INSTRUCTOR",
                                  "status": "LOCKED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        // Verify Audit log for STATUS_CHANGED
        List<AuditLog> auditLogsAfterStatusChange = auditLogRepository.findAll();
        assertThat(auditLogsAfterStatusChange).anyMatch(log ->
                log.getAction() == AuditEventAction.USER_STATUS_CHANGED &&
                log.getTargetId().equals(userId));

        // 4. Deactivate user / Soft disable (USER_DISABLED audit log)
        mockMvc.perform(delete(ApiConstant.USERS + "/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // Verify Audit log for DISABLE
        List<AuditLog> auditLogsAfterDisable = auditLogRepository.findAll();
        assertThat(auditLogsAfterDisable).anyMatch(log ->
                log.getAction() == AuditEventAction.USER_DISABLED &&
                log.getTargetId().equals(userId));
    }

    @Test
    void rbac_nonAdminRoles_cannotAccessUserManagementEndpoints() throws Exception {
        // 1. Create Student & Instructor accounts as Admin
        mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student_rbac_user",
                                  "email": "student_rbac_user@example.com",
                                  "password": "Password@123",
                                  "fullName": "Student RBAC",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "instructor_rbac_user",
                                  "email": "instructor_rbac_user@example.com",
                                  "password": "Password@123",
                                  "fullName": "Instructor RBAC",
                                  "role": "INSTRUCTOR"
                                }
                                """))
                .andExpect(status().isCreated());

        // 2. Login as Student
        MvcResult studentLogin = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student_rbac_user@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String studentToken = objectMapper.readTree(studentLogin.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 3. Login as Instructor
        MvcResult instructorLogin = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "instructor_rbac_user@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String instructorToken = objectMapper.readTree(instructorLogin.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 4. Verify Student Token fails with 403 Forbidden on User Management APIs
        mockMvc.perform(get(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "unauthorized_user",
                                  "email": "unauthorized_user@example.com",
                                  "password": "Password@123",
                                  "fullName": "Unauthorized User",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isForbidden());

        // 5. Verify Instructor Token fails with 403 Forbidden on User Management APIs
        mockMvc.perform(get(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivateUser_revokesAccessAndRefreshSessionsImmediately() throws Exception {
        MvcResult createResult = mockMvc.perform(post(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "deactivation_session_user",
                                  "email": "deactivation_session_user@example.com",
                                  "password": "Password@123",
                                  "fullName": "Deactivation Session User",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        MvcResult loginResult = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "deactivation_session_user@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(delete(ApiConstant.USERS + "/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ApiConstant.AUTH_REFRESH).cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION"));
        mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "deactivation_session_user@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void deactivateAdminAccount_isRejectedAndKeepsCurrentSessionActive() throws Exception {
        String adminId = userAccountRepository
                .findByEmailIgnoreCase("admin@aiplanning.local")
                .orElseThrow()
                .getId()
                .toString();

        mockMvc.perform(delete(ApiConstant.USERS + "/" + adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_PROTECTED"));

        mockMvc.perform(get(ApiConstant.PROFILE)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void userEndpoints_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(ApiConstant.USERS))
                .andExpect(status().isUnauthorized());
    }
}
