package com.codegym.aiplanning.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.audit.AuditLog;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.audit.AuditLogRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void deactivateUser_asAdmin_success_andAuditLogPersisted() throws Exception {
        // 1. Prepare target user directly in repository
        UserAccount targetUser = UserAccount.create(
                "deactivate_target",
                "deactivate_target@example.com",
                passwordEncoder.encode("Password@123"),
                "Deactivate Target",
                UserRole.STUDENT,
                AccountStatus.ACTIVE
        );
        UserAccount savedUser = userRepository.save(targetUser);
        String userId = savedUser.getId().toString();

        // 2. Deactivate user / Soft disable (USER_DISABLED audit log)
        mockMvc.perform(delete(ApiConstant.USERS + "/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // 3. Verify Audit log for DISABLE
        List<AuditLog> auditLogsAfterDisable = auditLogRepository.findAll();
        assertThat(auditLogsAfterDisable).anyMatch(log ->
                log.getAction() == AuditEventAction.USER_DISABLED &&
                log.getTargetId().equals(userId));
    }

    @Test
    void activateUser_asAdmin_success_andAuditLogPersisted() throws Exception {
        UserAccount targetUser = UserAccount.create(
                "activate_target",
                "activate_target@example.com",
                passwordEncoder.encode("Password@123"),
                "Activate Target",
                UserRole.STUDENT,
                AccountStatus.INACTIVE
        );
        UserAccount savedUser = userRepository.save(targetUser);
        String userId = savedUser.getId().toString();

        mockMvc.perform(post(ApiConstant.USERS + "/" + userId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).anyMatch(log ->
                log.getAction() == AuditEventAction.USER_STATUS_CHANGED &&
                log.getTargetId().equals(userId));
    }

    @Test
    void rbac_nonAdminRoles_cannotAccessUserManagementEndpoints() throws Exception {
        // 1. Save Student account directly in DB
        UserAccount student = UserAccount.create(
                "student_rbac_user",
                "student_rbac_user@example.com",
                passwordEncoder.encode("Password@123"),
                "Student RBAC",
                UserRole.STUDENT,
                AccountStatus.ACTIVE
        );
        userRepository.save(student);

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

        // 3. Verify Student Token fails with 403 Forbidden on User Management APIs
        mockMvc.perform(get(ApiConstant.USERS)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(ApiConstant.USERS + "/" + student.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userEndpoints_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(ApiConstant.USERS))
                .andExpect(status().isUnauthorized());
    }
}
