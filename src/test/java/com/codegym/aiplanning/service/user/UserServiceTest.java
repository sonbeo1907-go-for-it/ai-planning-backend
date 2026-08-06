package com.codegym.aiplanning.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.api.PageResponse;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRoleRequest;
import com.codegym.aiplanning.controller.user.dto.UserResponse;
import com.codegym.aiplanning.controller.user.dto.UserSearchParam;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.auth.AccountSecurityNotifier;
import com.codegym.aiplanning.service.auth.UserSessionRevocationService;
import com.codegym.aiplanning.service.user.impl.UserServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserSessionRevocationService userSessionRevocationService;

    @Mock
    private AccountSecurityNotifier accountSecurityNotifier;

    private UserServiceImpl userService;
    private Jwt actorJwt;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository,
                auditLogService,
                userSessionRevocationService,
                accountSecurityNotifier);
        actorId = UUID.randomUUID();
        actorJwt = Jwt.withTokenValue("token_val")
                .header("alg", "none")
                .claim("uid", actorId.toString())
                .claim("preferred_username", "admin_user")
                .build();
    }

    @Test
    void getUserById_found_returnsUserResponse() {
        UUID userId = UUID.randomUUID();
        UserAccount account = createTestAccount(
                "user_found", "User Found", UserRole.INSTRUCTOR, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(account));

        UserResponse response = userService.getUserById(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("user_found");
        assertThat(response.role()).isEqualTo(UserRole.INSTRUCTOR);
    }

    @Test
    void getUserById_notFound_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void updateUserRole_success_updatesRoleAndLogsAudit() {
        UUID userId = UUID.randomUUID();
        UserAccount account = createTestAccount(
                "user_student", "Student Name", UserRole.STUDENT, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(account));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUserRole(
                userId, new UpdateUserRoleRequest(UserRole.INSTRUCTOR), actorJwt);

        assertThat(response.role()).isEqualTo(UserRole.INSTRUCTOR);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("admin_user"),
                eq(AuditEventAction.USER_UPDATED),
                eq("USER"),
                eq(userId.toString()),
                any());
    }

    @Test
    void updateUserRole_adminTarget_isRejected() {
        UUID userId = UUID.randomUUID();
        UserAccount admin = createTestAccount(
                "admin_target", "Admin Target", UserRole.ADMIN, AccountStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateUserRole(
                        userId, new UpdateUserRoleRequest(UserRole.INSTRUCTOR), actorJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void deactivateUser_revokesSessionsAndNotifiesAfterTheAccountIsDisabled() {
        UUID userId = UUID.randomUUID();
        UserAccount account = createTestAccount(
                "user_disable", "Disable Name", UserRole.STUDENT, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSessionRevocationService.revokeAllActiveSessions(
                userId, "ACCOUNT_DEACTIVATED")).thenReturn(2);

        UserResponse response = userService.deactivateUser(userId, actorJwt);

        assertThat(response.status()).isEqualTo(AccountStatus.INACTIVE);
        verify(userSessionRevocationService).revokeAllActiveSessions(
                userId, "ACCOUNT_DEACTIVATED");
        verify(accountSecurityNotifier).accountDeactivated(userId);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("admin_user"),
                eq(AuditEventAction.USER_DISABLED),
                eq("USER"),
                eq(userId.toString()),
                any());
    }

    @Test
    void deactivateUser_adminTarget_isProtected() {
        UUID userId = UUID.randomUUID();
        UserAccount admin = createTestAccount(
                "protected_admin", "Protected Admin", UserRole.ADMIN, AccountStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deactivateUser(userId, actorJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.ADMIN_ACCOUNT_PROTECTED);

        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verifyNoInteractions(userSessionRevocationService, accountSecurityNotifier, auditLogService);
    }

    @Test
    void activateUser_setsActiveAndClearsFailures() {
        UUID userId = UUID.randomUUID();
        UserAccount account = createTestAccount(
                "user_activate", "Activate Name", UserRole.STUDENT, AccountStatus.INACTIVE);
        ReflectionTestUtils.setField(account, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(account));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.activateUser(userId, actorJwt);

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        verify(auditLogService).logAction(
                eq(actorId),
                eq("admin_user"),
                eq(AuditEventAction.USER_STATUS_CHANGED),
                eq("USER"),
                eq(userId.toString()),
                any());
    }

    @Test
    void getUsers_paginated_returnsPageResponse() {
        UserSearchParam param = new UserSearchParam(
                "student", UserRole.STUDENT, AccountStatus.ACTIVE, 0, 10);
        UserAccount account = createTestAccount(
                "student_found", "Student Found", UserRole.STUDENT, AccountStatus.ACTIVE);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));

        PageResponse<UserResponse> result = userService.getUsers(param);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).username()).isEqualTo("student_found");
    }

    private UserAccount createTestAccount(
            String username, String fullName, UserRole role, AccountStatus status) {
        UserAccount account = UserAccount.create(
                username, username + "@example.com", "encoded_pass", fullName, role, status);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "createdAt", Instant.now());
        ReflectionTestUtils.setField(account, "updatedAt", Instant.now());
        return account;
    }
}
