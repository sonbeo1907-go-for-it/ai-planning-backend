package com.codegym.aiplanning.service.auth;

public interface PasswordResetService {
    void requestPasswordReset(String email, String ipAddress, String resetUrlPrefix);
}
