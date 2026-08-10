package com.codegym.aiplanning.service.auth;

import com.codegym.aiplanning.entity.auth.AuthSessionStatus;
import com.codegym.aiplanning.repository.auth.AuthSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SessionJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SESSION =
            new OAuth2Error("invalid_token", "The login session is invalid or expired.", null);

    private static final OAuth2Error ACCOUNT_DISABLED =
            new OAuth2Error("account_disabled", "Your account has been disabled.", null);

    private final AuthSessionRepository authSessionRepository;
    private final SessionRevocationStore revocationStore;

    public SessionJwtValidator(
            AuthSessionRepository authSessionRepository,
            SessionRevocationStore revocationStore) {
        this.authSessionRepository = authSessionRepository;
        this.revocationStore = revocationStore;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            if (!"access".equals(jwt.getClaimAsString("typ"))) {
                return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
            }
            UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
            String tokenId = jwt.getId();
            if (tokenId == null
                    || revocationStore.isAccessTokenRevoked(tokenId)
                    || revocationStore.isSessionRevoked(sessionId)) {
                return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
            }

            java.util.Optional<com.codegym.aiplanning.entity.auth.AuthSession> sessionOpt = authSessionRepository.findByIdWithUser(sessionId);
            if (sessionOpt.isEmpty() || !sessionOpt.get().isActive(Instant.now())) {
                return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
            }
            if (sessionOpt.get().getUser().getStatus() == com.codegym.aiplanning.entity.auth.AccountStatus.INACTIVE) {
                return OAuth2TokenValidatorResult.failure(ACCOUNT_DISABLED);
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }
    }
}
