package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.GoogleAuthProperties;
import com.codegym.aiplanning.service.auth.GoogleIdTokenVerifier;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class GoogleIdTokenVerifierImpl implements GoogleIdTokenVerifier {

    private static final OAuth2Error INVALID_ISSUER = new OAuth2Error(
            "invalid_token", "The Google ID token issuer is invalid.", null);
    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "The Google ID token audience is invalid.", null);

    private final GoogleAuthProperties properties;
    private final JwtDecoder jwtDecoder;

    @Autowired
    public GoogleIdTokenVerifierImpl(GoogleAuthProperties properties) {
        this(properties, createDecoder(properties));
    }

    GoogleIdTokenVerifierImpl(GoogleAuthProperties properties, JwtDecoder jwtDecoder) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public GoogleIdentityClaims verify(String idToken) {
        ensureConfigured();
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            String subject = requiredClaim(jwt.getSubject());
            String email = requiredClaim(jwt.getClaimAsString("email"))
                    .toLowerCase(Locale.ROOT);
            if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
                throw invalidCredential();
            }
            String fullName = jwt.getClaimAsString("name");
            if (fullName == null || fullName.isBlank()) {
                fullName = "Google User";
            }
            return new GoogleIdentityClaims(subject, email, fullName.trim());
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidCredential();
        }
    }

    private void ensureConfigured() {
        if (!properties.enabled()
                || properties.clientId() == null
                || properties.clientId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.GOOGLE_AUTH_UNAVAILABLE,
                    "Google authentication is not configured.");
        }
    }

    private String requiredClaim(String value) {
        if (value == null || value.isBlank()) {
            throw invalidCredential();
        }
        return value.trim();
    }

    private BusinessException invalidCredential() {
        return new BusinessException(
                ErrorCode.INVALID_GOOGLE_CREDENTIAL,
                "The Google sign-in credential is invalid.");
    }

    private static JwtDecoder createDecoder(GoogleAuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                issuerValidator(properties.issuer()),
                audienceValidator(properties.clientId())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> issuerValidator(String configuredIssuer) {
        return jwt -> {
            String issuer = jwt.getClaimAsString("iss");
            boolean valid = configuredIssuer.equals(issuer)
                    || ("https://accounts.google.com".equals(configuredIssuer)
                            && "accounts.google.com".equals(issuer));
            return valid
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID_ISSUER);
        };
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            return clientId != null && audience != null && audience.contains(clientId)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        };
    }
}
