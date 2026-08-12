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
        // Reset links contain bearer credentials. Even the local fallback must not
        // copy them, or the destination email, into application logs.
        log.info("Local email delivery is disabled; password reset email was not sent.");
    }
}
