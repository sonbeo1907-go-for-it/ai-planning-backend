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
import com.codegym.aiplanning.controller.user.dto.CreateUserRequest;
import com.codegym.aiplanning.controller.user.dto.UpdateUserRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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
                passwordEncoder,
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

    private UserAccount createTestAccount(String username, String fullName, UserRole role, AccountStatus status) {
        UserAccount account = UserAccount.create(
                username, username + "@example.com", "encoded_pass", fullName, role, status);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "createdAt", Instant.now());
        ReflectionTestUtils.setField(account, "updatedAt", Instant.now());
        return account;
    }

    @Test
    void createUser_success_savesAccountAndLogsAudit() {
        CreateUserRequest request = new CreateUserRequest(
                "student_test",
                "Student_Test@Example.com",
                "Password@123",
                "Student Test",
                UserRole.STUDENT,
                AccountStatus.ACTIVE);

        when(userRepository.existsByUsernameIgnoreCase("student_test")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("encodedPassword");

        UserAccount savedAccount = createTestAccount("student_test", "Student Test", UserRole.STUDENT, AccountStatus.ACTIVE);
        when(userRepository.save(any(UserAccount.class))).thenReturn(savedAccount);

        UserResponse response = userService.createUser(request, actorJwt);

        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("student_test");
        assertThat(response.email()).isEqualTo("student_test@example.com");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);

        verify(auditLogService).logAction(
                eq(actorId),
                eq("admin_user"),
                eq(AuditEventAction.USER_CREATED),
                eq("USER"),
                eq(savedAccount.getId().toString()),
                any());
    }

    @Test
    void createUser_duplicateUsername_throwsConflictException() {
        CreateUserRequest request = new CreateUserRequest(
                "existing_user",
                "existing@example.com",
                "Password@123",
                "Existing User",
                UserRole.STUDENT,
                AccountStatus.ACTIVE);

        when(userRepository.existsByUsernameIgnoreCase("existing_user")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request, actorJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void createUser_duplicateEmail_throwsConflictException() {
        CreateUserRequest request = new CreateUserRequest(
                "new_user",
                "existing@example.com",
                "Password@123",
                "New User",
                UserRole.STUDENT,
                AccountStatus.ACTIVE);

        when(userRepository.existsByUsernameIgnoreCase("new_user")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request, actorJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void getUserById_found_returnsUserResponse() {
        UUID userId = UUID.randomUUID();
        UserAccount account = createTestAccount("user_found", "User Found", UserRole.INSTRUCTOR, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(account));

        UserResponse response = userService.getUserById(userId);

        assertThat(response).isNotNull();
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
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void updateUser_profileUpdated_logsUserUpdatedAudit() {
        UUID userId = UUID.randomUUID();
        UserAccount existing = createTestAccount("user_update", "Old Name", UserRole.STUDENT, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", userId);

        UpdateUserRequest updateRequest = new UpdateUserRequest(
                null, "New Name", UserRole.INSTRUCTOR, AccountStatus.ACTIVE, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(userId, updateRequest, actorJwt);

        assertThat(response.fullName()).isEqualTo("New Name");
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
    void updateUser_statusChanged_logsUserStatusChangedAudit() {
        UUID userId = UUID.randomUUID();
        UserAccount existing = createTestAccount("user_status", "User Name", UserRole.STUDENT, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", userId);

        UpdateUserRequest updateRequest = new UpdateUserRequest(
                null, "User Name", UserRole.STUDENT, AccountStatus.LOCKED, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(userId, updateRequest, actorJwt);

        assertThat(response.status()).isEqualTo(AccountStatus.LOCKED);

        verify(auditLogService).logAction(
                eq(actorId),
                eq("admin_user"),
                eq(AuditEventAction.USER_STATUS_CHANGED),
                eq("USER"),
                eq(userId.toString()),
                any());
    }

    @Test
    void deactivateUser_success_setsInactiveAndLogsUserDisabledAudit() {
        UUID userId = UUID.randomUUID();
        UserAccount existing = createTestAccount("user_disable", "Disable Name", UserRole.STUDENT, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", userId);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

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
    void deactivateUser_adminAccount_throwsProtectedAccountError() {
        UUID userId = UUID.randomUUID();
        UserAccount admin = createTestAccount(
                "protected_admin", "Protected Admin", UserRole.ADMIN, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(admin, "id", userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deactivateUser(userId, actorJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.ADMIN_ACCOUNT_PROTECTED);

        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verifyNoInteractions(
                userSessionRevocationService, accountSecurityNotifier, auditLogService);
    }

    @Test
    void updateUser_cannotBypassAdminDeactivationProtection() {
        UUID userId = UUID.randomUUID();
        UserAccount admin = createTestAccount(
                "protected_admin_update", "Protected Admin", UserRole.ADMIN, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(admin, "id", userId);
        UpdateUserRequest request = new UpdateUserRequest(
                null, "Protected Admin", UserRole.STUDENT, AccountStatus.INACTIVE, null);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateUser(userId, request, actorJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.ADMIN_ACCOUNT_PROTECTED);

        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verifyNoInteractions(
                userSessionRevocationService, accountSecurityNotifier, auditLogService);
    }

    @Test
    void getUsers_paginated_returnsPageResponse() {
        UserSearchParam param = new UserSearchParam("student", UserRole.STUDENT, AccountStatus.ACTIVE, 0, 10);

        UserAccount account = createTestAccount("student_found", "Student Found", UserRole.STUDENT, AccountStatus.ACTIVE);

        PageImpl<UserAccount> page = new PageImpl<>(List.of(account));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<UserResponse> result = userService.getUsers(param);

        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).username()).isEqualTo("student_found");
    }
}
