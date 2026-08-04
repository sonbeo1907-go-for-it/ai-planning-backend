package com.codegym.aiplanning.service.auth.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.common.validation.password.PasswordPolicyValidator;
import com.codegym.aiplanning.controller.profile.dto.ChangePasswordRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.auth.AuthService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private AuthService authService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PasswordServiceImpl passwordService;

    private UUID userId;
    private UUID currentSessionId;
    private UserAccount userAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        currentSessionId = UUID.randomUUID();
        userAccount = UserAccount.create("testuser", "test@example.com", "oldHash", "Test User", UserRole.STUDENT, AccountStatus.ACTIVE);
    }

    @Test
    void changePassword_WithMismatchedConfirmPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass123!", "DifferentPass123!");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> passwordService.changePassword(userId, currentSessionId, request));

        assertEquals(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH, exception.errorCode());
        verify(passwordPolicyValidator, never()).validate(anyString());
    }

    @Test
    void changePassword_WithInvalidPolicy_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "weak", "weak");

        doThrow(new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Error"))
                .when(passwordPolicyValidator).validate("weak");

        assertThrows(BusinessException.class, () -> passwordService.changePassword(userId, currentSessionId, request));
    }

    @Test
    void changePassword_WithIncorrectCurrentPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPass123!", "NewPass123!", "NewPass123!");

        when(userAccountRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(userAccount));
        when(passwordEncoder.matches("WrongPass123!", "oldHash")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> passwordService.changePassword(userId, currentSessionId, request));

        assertEquals(ErrorCode.CURRENT_PASSWORD_INCORRECT, exception.errorCode());
        verify(auditLogService).logAction(eq(userId), eq("testuser"), eq(AuditEventAction.PASSWORD_CHANGE_FAILED), eq("UserAccount"), eq(userId.toString()), anyString());
    }

    @Test
    void changePassword_WithSameNewPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "OldPass123!", "OldPass123!");

        when(userAccountRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(userAccount));
        when(passwordEncoder.matches("OldPass123!", "oldHash")).thenReturn(true);
        // second check for same password
        when(passwordEncoder.matches("OldPass123!", "oldHash")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> passwordService.changePassword(userId, currentSessionId, request));

        assertEquals(ErrorCode.NEW_PASSWORD_MUST_BE_DIFFERENT, exception.errorCode());
        verify(auditLogService).logAction(eq(userId), eq("testuser"), eq(AuditEventAction.PASSWORD_CHANGE_FAILED), eq("UserAccount"), eq(userId.toString()), anyString());
    }

    @Test
    void changePassword_WithValidRequest_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass123!", "NewPass123!");

        when(userAccountRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(userAccount));
        when(passwordEncoder.matches("OldPass123!", "oldHash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123!", "oldHash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("newHash");

        passwordService.changePassword(userId, currentSessionId, request);

        assertEquals("newHash", userAccount.getPasswordHash());
        verify(authService).revokeOtherSessions(userId, currentSessionId);
        verify(auditLogService).logAction(eq(userId), eq("testuser"), eq(AuditEventAction.PASSWORD_CHANGED), eq("UserAccount"), eq(userId.toString()), anyString());
    }
}
