package com.codegym.aiplanning.service.admin.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.admin.AdminUserService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Logger auditLogger = LoggerFactory.getLogger("audit-log");

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(
            UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @Override
    public void resetPassword(UUID userId, String newPassword) {
        UserAccount account = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        account.changePassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(account);

        auditLogger.info("Admin reset password for user: {}", account.getUsername());
    }
}
