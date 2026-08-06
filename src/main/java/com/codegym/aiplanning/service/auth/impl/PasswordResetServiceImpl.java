package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.common.utils.StringMaskUtils;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.PasswordResetToken;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.auth.PasswordResetTokenRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.common.validation.password.PasswordPolicyValidator;
import com.codegym.aiplanning.service.auth.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.auth.PasswordResetRateLimiter;
import com.codegym.aiplanning.service.auth.PasswordResetService;
import com.codegym.aiplanning.service.auth.RefreshTokenCodec;
import com.codegym.aiplanning.service.auth.model.PasswordResetRequestedEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetRateLimiter rateLimiter;
    private final RefreshTokenCodec tokenCodec; // reused for generating/hashing 32 bytes SecureRandom
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuthService authService;

    public PasswordResetServiceImpl(
            UserAccountRepository userAccountRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetRateLimiter rateLimiter,
            RefreshTokenCodec tokenCodec,
            ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicyValidator,
            AuthService authService) {
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.rateLimiter = rateLimiter;
        this.tokenCodec = tokenCodec;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.authService = authService;
    }

    @Transactional
    @Override
    public void requestPasswordReset(String email, String ipAddress, String resetUrlPrefix) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // 1. Rate Limiting (IP & Email)
        if (!rateLimiter.isAllowedByIp(ipAddress)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau.");
        }
        if (!rateLimiter.isAllowedByEmail(normalizedEmail)) {
            simulateConstantTime();
            return;
        }

        // 2. Find User
        Optional<UserAccount> userOpt = userAccountRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail);
        if (userOpt.isEmpty()) {
            simulateConstantTime();
            return;
        }
        UserAccount user = userOpt.get();

        // 3. Invalidate Old Tokens
        Instant now = Instant.now();
        tokenRepository.invalidateOldTokens(user.getId(), now);

        // 4. Generate New Token
        String rawToken = tokenCodec.generate();
        String tokenHash = tokenCodec.hash(rawToken);
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);

        PasswordResetToken token = PasswordResetToken.create(user, tokenHash, expiresAt);
        tokenRepository.save(token);

        // 5. Audit Logging (masked email)
        auditLogService.logAction(
                user.getId(),
                user.getEmail(),
                AuditEventAction.PASSWORD_RESET_REQUESTED,
                "PasswordResetToken",
                token.getId().toString(),
                "Requested for: " + StringMaskUtils.maskEmail(normalizedEmail));

        // 6. Publish Event (handled async after commit)
        String resetLink = resetUrlPrefix + "?token=" + rawToken;
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(normalizedEmail, resetLink, now));
    }

    private void simulateConstantTime() {
        // Generate and hash a dummy token to consume roughly the same CPU time
        String dummy = tokenCodec.generate();
        tokenCodec.hash(dummy);
        // Add a small sleep to simulate DB/Network latency
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional
    @Override
    public void resetPassword(String rawToken, String newPassword) {
        // 1. Validate password policy
        passwordPolicyValidator.validate(newPassword);

        // 2. Hash token and find it
        String tokenHash = tokenCodec.hash(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Liên kết không hợp lệ hoặc không tồn tại."));

        Instant now = Instant.now();
        if (!token.isUsable(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Liên kết đã hết hạn hoặc đã được sử dụng.");
        }

        // 3. Update User Password
        UserAccount user = token.getUser();
        user.changePassword(passwordEncoder.encode(newPassword));

        // 4. Mark Token as Used
        token.markAsUsed(now);

        // 5. Revoke all sessions & refresh tokens
        authService.revokeOtherSessions(user.getId(), null);

        // 6. Audit Log
        auditLogService.logAction(
                user.getId(),
                user.getEmail(),
                AuditEventAction.PASSWORD_CHANGED,
                "UserAccount",
                user.getId().toString(),
                "Password reset via email token."
        );
    }
}
