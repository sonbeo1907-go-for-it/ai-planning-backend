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

            boolean active = authSessionRepository.existsByIdAndStatusAndExpiresAtAfter(
                    sessionId, AuthSessionStatus.ACTIVE, Instant.now());
            return active
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }
    }
}
