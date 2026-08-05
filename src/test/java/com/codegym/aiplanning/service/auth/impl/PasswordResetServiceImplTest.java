package com.codegym.aiplanning.service.auth.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.PasswordResetToken;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.auth.PasswordResetTokenRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.auth.PasswordResetRateLimiter;
import com.codegym.aiplanning.service.auth.RefreshTokenCodec;
import com.codegym.aiplanning.service.auth.model.PasswordResetRequestedEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.codegym.aiplanning.common.validation.password.PasswordPolicyValidator;
import com.codegym.aiplanning.service.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordResetRateLimiter rateLimiter;

    @Mock
    private RefreshTokenCodec tokenCodec;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private AuthService authService;

    @Captor
    private ArgumentCaptor<PasswordResetToken> tokenCaptor;

    @Captor
    private ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor;

    private PasswordResetServiceImpl passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetServiceImpl(
                userAccountRepository,
                tokenRepository,
                rateLimiter,
                tokenCodec,
                eventPublisher,
                auditLogService,
                passwordEncoder,
                passwordPolicyValidator,
                authService);
    }

    @Test
    void requestPasswordReset_WhenIpRateLimited_ThrowsBusinessException() {
        // Arrange
        String email = "test@example.com";
        String ipAddress = "192.168.1.1";
        when(rateLimiter.isAllowedByIp(ipAddress)).thenReturn(false);

        // Act & Assert
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> passwordResetService.requestPasswordReset(email, ipAddress, "http://localhost:3000"));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.errorCode());
        verify(userAccountRepository, never()).findByEmailIgnoreCaseForUpdate(anyString());
    }

    @Test
    void requestPasswordReset_WhenEmailRateLimited_SimulatesConstantTimeAndReturns() {
        // Arrange
        String email = "test@example.com";
        String ipAddress = "192.168.1.1";
        when(rateLimiter.isAllowedByIp(ipAddress)).thenReturn(true);
        when(rateLimiter.isAllowedByEmail(email)).thenReturn(false);
        when(tokenCodec.generate()).thenReturn("dummy-token");

        // Act
        long startTime = System.currentTimeMillis();
        passwordResetService.requestPasswordReset(email, ipAddress, "http://localhost:3000");
        long duration = System.currentTimeMillis() - startTime;

        // Assert
        assertTrue(duration >= 50, "Should simulate constant time with Thread.sleep(50)");
        verify(tokenCodec).generate();
        verify(tokenCodec).hash("dummy-token");
        verify(userAccountRepository, never()).findByEmailIgnoreCaseForUpdate(anyString());
    }

    @Test
    void requestPasswordReset_WhenUserNotFound_SimulatesConstantTimeAndReturns() {
        // Arrange
        String email = "notfound@example.com";
        String ipAddress = "192.168.1.1";
        when(rateLimiter.isAllowedByIp(ipAddress)).thenReturn(true);
        when(rateLimiter.isAllowedByEmail(email)).thenReturn(true);
        when(userAccountRepository.findByEmailIgnoreCaseForUpdate(email)).thenReturn(Optional.empty());
        when(tokenCodec.generate()).thenReturn("dummy-token");

        // Act
        passwordResetService.requestPasswordReset(email, ipAddress, "http://localhost:3000");

        // Assert
        verify(tokenCodec).generate();
        verify(tokenCodec).hash("dummy-token");
        verify(tokenRepository, never()).invalidateOldTokens(any(), any());
    }

    @Test
    void requestPasswordReset_WhenValidRequest_CreatesTokenAndPublishesEvent() {
        // Arrange
        String email = "test@example.com";
        String ipAddress = "192.168.1.1";
        String resetUrlPrefix = "http://localhost:3000/reset";
        UUID userId = UUID.randomUUID();
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn(email);

        when(rateLimiter.isAllowedByIp(ipAddress)).thenReturn(true);
        when(rateLimiter.isAllowedByEmail(email)).thenReturn(true);
        when(userAccountRepository.findByEmailIgnoreCaseForUpdate(email)).thenReturn(Optional.of(user));
        
        String rawToken = "raw-random-token";
        String tokenHash = "hashed-token";
        when(tokenCodec.generate()).thenReturn(rawToken);
        when(tokenCodec.hash(rawToken)).thenReturn(tokenHash);

        // Act
        passwordResetService.requestPasswordReset(email, ipAddress, resetUrlPrefix);

        // Assert
        // 1. Invalidates old tokens
        verify(tokenRepository).invalidateOldTokens(eq(userId), any(Instant.class));

        // 2. Saves new token
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertEquals(tokenHash, savedToken.getTokenHash());
        assertNotNull(savedToken.getExpiresAt());

        // 3. Logs audit action
        verify(auditLogService).logAction(
                eq(userId),
                eq(email),
                eq(AuditEventAction.PASSWORD_RESET_REQUESTED),
                eq("PasswordResetToken"),
                anyString(),
                contains("tes***@example.com"));

        // 4. Publishes event
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordResetRequestedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(email, publishedEvent.email());
        assertEquals(resetUrlPrefix + "?token=" + rawToken, publishedEvent.resetLink());
        assertNotNull(publishedEvent.requestedAt());
    }
}
