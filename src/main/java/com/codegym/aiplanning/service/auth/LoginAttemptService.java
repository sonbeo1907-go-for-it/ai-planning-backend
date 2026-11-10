package com.codegym.aiplanning.service.auth;

public interface LoginAttemptService {

    void recordFailedLogin(String email);
}
