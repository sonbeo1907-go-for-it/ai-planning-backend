package com.codegym.aiplanning.config;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "test"})
public class LocalAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminSeeder.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    public LocalAdminSeeder(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            BootstrapAdminProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()
                || repository.findByUsernameIgnoreCase(properties.username()).isPresent()) {
            return;
        }

        UserAccount admin = UserAccount.create(
                properties.username(),
                passwordEncoder.encode(properties.password()),
                properties.fullName(),
                UserRole.ADMIN,
                AccountStatus.ACTIVE);
        repository.save(admin);
        log.info("Created local bootstrap admin account '{}'", properties.username());
    }
}
