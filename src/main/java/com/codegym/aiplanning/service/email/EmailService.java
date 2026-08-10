package com.codegym.aiplanning.service.email;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);

    void sendAccountDeactivatedEmail(String toEmail, String fullName, String reason);
}
