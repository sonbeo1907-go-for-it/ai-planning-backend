package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.repository.auth.PasswordResetTokenRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PasswordResetTokenCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupTask.class);
    private final PasswordResetTokenRepository tokenRepository;

    public PasswordResetTokenCleanupTask(PasswordResetTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    // Run every day at 2:00 AM server time
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired password reset tokens...");
        Instant now = Instant.now();
        try {
            tokenRepository.deleteAllExpiredSince(now);
            log.info("Completed cleanup of expired password reset tokens.");
        } catch (Exception e) {
            log.error("Failed to cleanup expired password reset tokens", e);
        }
    }
}
