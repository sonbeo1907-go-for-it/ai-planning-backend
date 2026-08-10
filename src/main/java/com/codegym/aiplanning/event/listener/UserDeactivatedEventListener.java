package com.codegym.aiplanning.event.listener;

import com.codegym.aiplanning.entity.auth.DeactivationReasonCode;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.event.UserDeactivatedEvent;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.auth.UserSessionRevocationService;
import com.codegym.aiplanning.service.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserDeactivatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeactivatedEventListener.class);
    private static final String ACCOUNT_DEACTIVATED_REASON = "ACCOUNT_DEACTIVATED";

    private final UserAccountRepository userRepository;
    private final UserSessionRevocationService userSessionRevocationService;
    private final EmailService emailService;

    public UserDeactivatedEventListener(
            UserAccountRepository userRepository,
            UserSessionRevocationService userSessionRevocationService,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.userSessionRevocationService = userSessionRevocationService;
        this.emailService = emailService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserDeactivatedEvent(UserDeactivatedEvent event) {
        log.info("Handling UserDeactivatedEvent for userId: {}", event.userId());

        try {
            // 1. Revoke sessions (External Cache/Redis)
            int revokedCount = userSessionRevocationService.revokeAllActiveSessions(
                    event.userId(), ACCOUNT_DEACTIVATED_REASON);
            log.info("Revoked {} active sessions for user {}", revokedCount, event.userId());

            // 2. Fetch user to send email
            userRepository.findById(event.userId()).ifPresent(user -> {
                String translatedReason = translateReasonCode(event.reasonCode(), event.publicReason());
                emailService.sendAccountDeactivatedEmail(user.getEmail(), user.getFullName(), translatedReason);
                log.info("Sent deactivation email to {}", user.getEmail());
            });

        } catch (Exception e) {
            log.error("Failed to process post-deactivation tasks for user {}: {}", event.userId(), e.getMessage(), e);
            // We do not rethrow since this is async and we don't want to break anything, just log for retry/monitoring
        }
    }

    private String translateReasonCode(DeactivationReasonCode code, String publicReason) {
        if (code == DeactivationReasonCode.OTHER && publicReason != null && !publicReason.isBlank()) {
            return publicReason;
        }
        return code != null ? code.getDisplayName() : "Không xác định";
    }
}
