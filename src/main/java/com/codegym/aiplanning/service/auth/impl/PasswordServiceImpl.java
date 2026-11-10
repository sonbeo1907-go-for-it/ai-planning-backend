package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.common.validation.password.PasswordPolicyValidator;
import com.codegym.aiplanning.controller.profile.dto.ChangePasswordRequest;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.auth.AuthService;
import com.codegym.aiplanning.service.auth.PasswordService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordServiceImpl implements PasswordService {

    private final UserAccountRepository userAccountRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final com.codegym.aiplanning.service.audit.AuditLogService auditLogService;

    public PasswordServiceImpl(
            UserAccountRepository userAccountRepository,
            AuthService authService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicyValidator,
            com.codegym.aiplanning.service.audit.AuditLogService auditLogService) {
        this.userAccountRepository = userAccountRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(
                    ErrorCode.PASSWORD_CONFIRMATION_MISMATCH, "Password confirmation does not match");
        }

        passwordPolicyValidator.validate(request.newPassword());

        UserAccount account = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            auditLogService.logAction(
                    userId,
                    account.getEmail(),
                    com.codegym.aiplanning.entity.audit.AuditEventAction.PASSWORD_CHANGE_FAILED,
                    "UserAccount",
                    userId.toString());
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT, "Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            auditLogService.logAction(
                    userId,
                    account.getEmail(),
                    com.codegym.aiplanning.entity.audit.AuditEventAction.PASSWORD_CHANGE_FAILED,
                    "UserAccount",
                    userId.toString());
            throw new BusinessException(
                    ErrorCode.NEW_PASSWORD_MUST_BE_DIFFERENT, "New password must be different from current password");
        }

        account.changePassword(passwordEncoder.encode(request.newPassword()));

        authService.revokeOtherSessions(userId, currentSessionId);

        auditLogService.logAction(
                userId,
                account.getEmail(),
                com.codegym.aiplanning.entity.audit.AuditEventAction.PASSWORD_CHANGED,
                "UserAccount",
                userId.toString());
    }
}
