package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.config.LoginAttemptProperties;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.auth.LoginAttemptService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final UserAccountRepository userAccountRepository;
    private final LoginAttemptProperties properties;

    public LoginAttemptServiceImpl(
            UserAccountRepository userAccountRepository, LoginAttemptProperties properties) {
        this.userAccountRepository = userAccountRepository;
        this.properties = properties;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(String email) {
        userAccountRepository.findByEmailIgnoreCaseForUpdate(email).ifPresent(account -> {
            if (account.getPasswordHash() != null) {
                account.recordFailedLogin(
                        Instant.now(), properties.maxAttempts(), properties.blockDuration());
            }
        });
    }
}
