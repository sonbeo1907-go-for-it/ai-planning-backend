package com.codegym.aiplanning.service.auth.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.GoogleAuthProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class GoogleIdTokenVerifierImplTest {

    private final GoogleAuthProperties properties = new GoogleAuthProperties(
            true,
            "google-client-id",
            "https://accounts.google.com",
            "https://www.googleapis.com/oauth2/v3/certs");
    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final GoogleIdTokenVerifierImpl verifier =
            new GoogleIdTokenVerifierImpl(properties, jwtDecoder);

    @Test
    void validVerifiedGoogleIdentityReturnsNormalizedClaims() {
        when(jwtDecoder.decode("valid-token")).thenReturn(googleJwt(true));

        var claims = verifier.verify("valid-token");

        assertThat(claims.subject()).isEqualTo("google-subject-123");
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.fullName()).isEqualTo("Google User");
    }

    @Test
    void unverifiedEmailIsRejected() {
        when(jwtDecoder.decode("unverified-token")).thenReturn(googleJwt(false));

        assertThatThrownBy(() -> verifier.verify("unverified-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_GOOGLE_CREDENTIAL);
    }

    @Test
    void decoderFailureIsReturnedAsGenericGoogleCredentialError() {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new JwtException("bad signature"));

        assertThatThrownBy(() -> verifier.verify("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The Google sign-in credential is invalid.");
    }

    @Test
    void disabledGoogleAuthenticationReturnsServiceUnavailableError() {
        GoogleIdTokenVerifierImpl disabledVerifier = new GoogleIdTokenVerifierImpl(
                new GoogleAuthProperties(
                        false,
                        "",
                        "https://accounts.google.com",
                        "https://www.googleapis.com/oauth2/v3/certs"),
                jwtDecoder);

        assertThatThrownBy(() -> disabledVerifier.verify("any-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.GOOGLE_AUTH_UNAVAILABLE);
    }

    private Jwt googleJwt(boolean emailVerified) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("google-id-token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject("google-subject-123")
                .audience(List.of("google-client-id"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .claim("email", "User@Example.com")
                .claim("email_verified", emailVerified)
                .claim("name", "Google User")
                .build();
    }
}
