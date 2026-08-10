package com.codegym.aiplanning.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression("!environment.containsProperty('spring.mail.host')")
public class LocalEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LocalEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.debug("==========================================================================");
        log.debug("LOCAL EMAIL SIMULATION");
        log.debug("To: {}", toEmail);
        log.debug("Subject: Password Reset Request");
        log.debug("Link: {}", resetLink);
        log.debug("==========================================================================");
    }

    @Override
    public void sendAccountDeactivatedEmail(String toEmail, String fullName, String reason) {
        log.debug("==========================================================================");
        log.debug("LOCAL EMAIL SIMULATION");
        log.debug("To: {}", toEmail);
        log.debug("Subject: Your account has been disabled");
        log.debug("Body: Your account has been disabled by an administrator.");
        log.debug("Reason: {}", reason);
        log.debug("==========================================================================");
    }
}
