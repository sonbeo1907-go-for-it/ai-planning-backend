package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.JwtProperties;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.auth.AuthService;
import com.codegym.aiplanning.service.auth.model.AuthToken;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userAccountRepository;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserAccountRepository userAccountRepository,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.userAccountRepository = userAccountRepository;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(readOnly = true)
    @Override
    public AuthToken login(String username, String password) {
        String normalizedUsername = username.trim();
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    normalizedUsername, password));
        } catch (AuthenticationException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS, "Invalid username or password.");
        }

        UserAccount account = userAccountRepository
                .findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_CREDENTIALS, "Invalid username or password."));

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.expiration());
        String role = "ROLE_" + account.getRole().name();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(account.getId().toString())
                .claim("uid", account.getId().toString())
                .claim("preferred_username", account.getUsername())
                .claim("full_name", account.getFullName())
                .claim("roles", List.of(role))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new AuthToken(token, "Bearer", jwtProperties.expiration().toSeconds());
    }
}
