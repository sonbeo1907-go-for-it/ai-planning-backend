package com.codegym.aiplanning.service.admin.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
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
class AdminUserServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private UserAccount userAccount;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userAccount = UserAccount.create(
                "testuser", "oldHash", "Test User", UserRole.STUDENT, AccountStatus.ACTIVE);
    }

    @Test
    void resetPassword_WhenUserExists_ShouldUpdatePassword() {
        // Arrange
        String newPassword = "newPassword123";
        String newHash = "newHash123";

        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));
        when(passwordEncoder.encode(newPassword)).thenReturn(newHash);

        // Act
        adminUserService.resetPassword(userId, newPassword);

        // Assert
        assertEquals(newHash, userAccount.getPasswordHash());
        verify(userAccountRepository).save(userAccount);
        verify(passwordEncoder).encode(newPassword);
    }

    @Test
    void resetPassword_WhenUserNotFound_ShouldThrowException() {
        // Arrange
        when(userAccountRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adminUserService.resetPassword(userId, "newPassword123"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
        assertEquals("User not found", exception.getMessage());
        verify(userAccountRepository, never()).save(any());
    }
}
