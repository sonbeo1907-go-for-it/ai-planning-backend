package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.common.exception.AccountDisabledException;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.AuthSessionProperties;
import com.codegym.aiplanning.config.JwtProperties;
import com.codegym.aiplanning.entity.auth.AuthSession;
import com.codegym.aiplanning.entity.auth.AuthIdentity;
import com.codegym.aiplanning.entity.auth.AuthProvider;
import com.codegym.aiplanning.entity.auth.RefreshToken;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.auth.AuthIdentityRepository;
import com.codegym.aiplanning.repository.auth.AuthSessionRepository;
import com.codegym.aiplanning.repository.auth.RefreshTokenRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.auth.AuthService;
import com.codegym.aiplanning.service.auth.GoogleIdTokenVerifier;
import com.codegym.aiplanning.service.auth.LoginAttemptService;
import com.codegym.aiplanning.service.auth.RefreshTokenCodec;
import com.codegym.aiplanning.service.auth.SessionRevocationStore;
import com.codegym.aiplanning.service.auth.model.AuthResult;
import com.codegym.aiplanning.service.auth.model.AuthToken;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userAccountRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptService loginAttemptService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RefreshTokenCodec refreshTokenCodec;
    private final SessionRevocationStore revocationStore;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;
    private final AuthSessionProperties sessionProperties;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserAccountRepository userAccountRepository,
            AuthIdentityRepository authIdentityRepository,
            AuthSessionRepository authSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            LoginAttemptService loginAttemptService,
            GoogleIdTokenVerifier googleIdTokenVerifier,
            RefreshTokenCodec refreshTokenCodec,
            SessionRevocationStore revocationStore,
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            JwtProperties jwtProperties,
            AuthSessionProperties sessionProperties,
            TransactionTemplate transactionTemplate,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userAccountRepository = userAccountRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptService = loginAttemptService;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.refreshTokenCodec = refreshTokenCodec;
        this.revocationStore = revocationStore;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
        this.sessionProperties = sessionProperties;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void register(String email, String password, String fullName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(password);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                    return;
                }

                UserAccount account = UserAccount.create(
                        generatedStudentUsername(),
                        normalizedEmail,
                        passwordHash,
                        fullName.trim(),
                        UserRole.STUDENT,
                        AccountStatus.ACTIVE);
                userAccountRepository.saveAndFlush(account);
            });
        } catch (DataIntegrityViolationException exception) {
            // A concurrent registration can win the unique-email race. Return the same
            // successful acknowledgement so that this endpoint cannot enumerate emails.
        }
    }

    @Transactional
    @Override
    public AuthResult login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    normalizedEmail, password));
        } catch (AuthenticationException exception) {
            if (exception instanceof org.springframework.security.authentication.DisabledException) {
                UserAccount account = userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password."));
                
                String reasonStr = buildDeactivationMessage(account);
                throw new AccountDisabledException(reasonStr, account.getDeactivationReasonCode());
            }
            loginAttemptService.recordFailedLogin(normalizedEmail);
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }

        UserAccount account = userAccountRepository
                .findByEmailIgnoreCaseForUpdate(normalizedEmail)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_CREDENTIALS, "Invalid email or password."));
        Instant now = Instant.now();
        if (account.getStatus() == AccountStatus.INACTIVE) {
            String reasonStr = buildDeactivationMessage(account);
            throw new AccountDisabledException(reasonStr, account.getDeactivationReasonCode());
        }
        if (!account.isActive() || account.isLocked() || account.isLoginBlocked(now)) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }
        account.clearLoginFailures();

        return createSession(account, now);
    }

    @Transactional
    @Override
    public AuthResult loginWithGoogle(String idToken) {
        GoogleIdTokenVerifier.GoogleIdentityClaims claims =
                googleIdTokenVerifier.verify(idToken);

        UserAccount account = authIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, claims.subject())
                .map(identity -> userAccountRepository
                        .findByIdForUpdate(identity.getUser().getId())
                        .orElseThrow(this::invalidGoogleCredential))
                .orElseGet(() -> createGoogleAccount(claims));

        if (!account.isActive() || account.isLocked()) {
            throw invalidGoogleCredential();
        }
        return createSession(account, Instant.now());
    }

    @Override
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidSession();
        }

        String tokenHash = refreshTokenCodec.hash(rawRefreshToken);
        String lockOwner = UUID.randomUUID().toString();
        SessionRevocationStore.LockState lockState = revocationStore.acquireRefreshLock(
                tokenHash, lockOwner, sessionProperties.refreshLockDuration());
        if (lockState == SessionRevocationStore.LockState.BUSY) {
            throw new BusinessException(
                    ErrorCode.REFRESH_IN_PROGRESS, "A refresh request is already in progress.");
        }

        try {
            RotationResult result = transactionTemplate.execute(status -> rotate(tokenHash));
            if (result == null) {
                throw new IllegalStateException("Refresh rotation produced no result");
            }
            if (result.revoked()) {
                markSessionRevoked(result.sessionId(), result.sessionExpiresAt(), Instant.now());
                throw invalidSession();
            }
            return new AuthResult(
                    issueAccessToken(
                            result.account(), result.sessionId(), result.accessIssuedAt()),
                    result.rawRefreshToken(),
                    positiveDurationBetween(
                                    result.accessIssuedAt(), result.sessionExpiresAt())
                            .toSeconds());
        } finally {
            if (lockState == SessionRevocationStore.LockState.ACQUIRED) {
                revocationStore.releaseRefreshLock(tokenHash, lockOwner);
            }
        }
    }

    @Override
    public void logout(String authorizationHeader, String rawRefreshToken) {
        AccessCredential accessCredential = decodeAccessCredential(authorizationHeader);
        String refreshHash = rawRefreshToken == null || rawRefreshToken.isBlank()
                ? null
                : refreshTokenCodec.hash(rawRefreshToken);

        LogoutResult result = transactionTemplate.execute(status -> {
            Set<UUID> sessionIds = new LinkedHashSet<>();
            if (refreshHash != null) {
                refreshTokenRepository
                        .findByTokenHash(refreshHash)
                        .map(RefreshToken::getSession)
                        .map(AuthSession::getId)
                        .ifPresent(sessionIds::add);
            }
            if (accessCredential != null) {
                sessionIds.add(accessCredential.sessionId());
            }

            Instant now = Instant.now();
            Set<RevokedSession> revokedSessions = new LinkedHashSet<>();
            for (UUID sessionId : sessionIds) {
                authSessionRepository.findByIdForUpdate(sessionId).ifPresent(session -> {
                    session.revoke(now, "LOGOUT");
                    refreshTokenRepository.revokeAllBySessionId(sessionId, now);
                    revokedSessions.add(
                            new RevokedSession(sessionId, session.getExpiresAt()));
                });
            }
            return new LogoutResult(revokedSessions, now);
        });

        if (result == null) {
            return;
        }
        result.sessions().forEach(session ->
                markSessionRevoked(session.id(), session.expiresAt(), result.revokedAt()));
        if (accessCredential != null) {
            markAccessTokenRevoked(accessCredential, result.revokedAt());
        }
    }

    @Override
    public void revokeOtherSessions(UUID userId, UUID retainedSessionId) {
        LogoutResult result = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Set<RevokedSession> revokedSessions = new LinkedHashSet<>();
            List<AuthSession> otherSessions = authSessionRepository.findActiveSessionsExcept(
                    userId, com.codegym.aiplanning.entity.auth.AuthSessionStatus.ACTIVE, retainedSessionId);

            for (AuthSession session : otherSessions) {
                authSessionRepository.findByIdForUpdate(session.getId()).ifPresent(lockedSession -> {
                    lockedSession.revoke(now, "PASSWORD_CHANGED");
                    refreshTokenRepository.revokeAllBySessionId(lockedSession.getId(), now);
                    revokedSessions.add(new RevokedSession(lockedSession.getId(), lockedSession.getExpiresAt()));
                });
            }
            return new LogoutResult(revokedSessions, now);
        });

        if (result != null) {
            result.sessions().forEach(session ->
                    markSessionRevoked(session.id(), session.expiresAt(), result.revokedAt()));
        }
    }

    private RotationResult rotate(String tokenHash) {
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidSession);
        AuthSession session = current.getSession();
        Instant now = Instant.now();

        if (!current.isUsable(now) || !session.isActive(now)) {
            session.revoke(now, "REFRESH_TOKEN_REUSE");
            refreshTokenRepository.revokeAllBySessionId(session.getId(), now);
            return RotationResult.revoked(session.getId(), session.getExpiresAt());
        }

        String nextRawToken = refreshTokenCodec.generate();
        RefreshToken next = refreshTokenRepository.saveAndFlush(RefreshToken.create(
                session, refreshTokenCodec.hash(nextRawToken), session.getExpiresAt()));
        current.consume(now, next.getId());
        return RotationResult.rotated(
                session.getId(), session.getExpiresAt(), session.getUser(), now, nextRawToken);
    }

    private UserAccount createGoogleAccount(
            GoogleIdTokenVerifier.GoogleIdentityClaims claims) {
        if (userAccountRepository.existsByEmailIgnoreCase(claims.email())) {
            throw new BusinessException(
                    ErrorCode.GOOGLE_ACCOUNT_LINK_REQUIRED,
                    "An account with this email already exists. Sign in locally to link Google.");
        }

        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                googleUsername(claims.subject()),
                claims.email(),
                null,
                limitLength(claims.fullName(), 150),
                UserRole.STUDENT,
                AccountStatus.ACTIVE));
        authIdentityRepository.save(AuthIdentity.google(
                account, claims.subject(), claims.email()));
        return account;
    }

    private AuthResult createSession(UserAccount account, Instant now) {
        AuthSession session = authSessionRepository.saveAndFlush(
                AuthSession.create(account, now.plus(sessionProperties.absoluteExpiration())));
        String rawRefreshToken = refreshTokenCodec.generate();
        refreshTokenRepository.save(RefreshToken.create(
                session, refreshTokenCodec.hash(rawRefreshToken), session.getExpiresAt()));

        return new AuthResult(
                issueAccessToken(account, session.getId(), now),
                rawRefreshToken,
                sessionProperties.absoluteExpiration().toSeconds());
    }

    private String googleUsername(String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8));
            return "google_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String limitLength(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private AuthToken issueAccessToken(UserAccount account, UUID sessionId, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(jwtProperties.expiration());
        String role = "ROLE_" + account.getRole().name();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .subject(account.getId().toString())
                .claim("typ", "access")
                .claim("sid", sessionId.toString())
                .claim("uid", account.getId().toString())
                .claim("email", account.getEmail())
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

    private AccessCredential decodeAccessCredential(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            Jwt jwt = jwtDecoder.decode(
                    authorizationHeader.substring(BEARER_PREFIX.length()).trim());
            return new AccessCredential(
                    UUID.fromString(jwt.getClaimAsString("sid")),
                    jwt.getId(),
                    jwt.getExpiresAt());
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    private void markSessionRevoked(UUID sessionId, Instant expiresAt, Instant now) {
        revocationStore.markSessionRevoked(
                sessionId, positiveDurationBetween(now, expiresAt));
    }

    private void markAccessTokenRevoked(AccessCredential credential, Instant now) {
        if (credential.tokenId() != null && credential.expiresAt() != null) {
            revocationStore.markAccessTokenRevoked(
                    credential.tokenId(),
                    positiveDurationBetween(now, credential.expiresAt()));
        }
    }

    private Duration positiveDurationBetween(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        return duration.isNegative() || duration.isZero()
                ? Duration.ofSeconds(1)
                : duration;
    }

    private BusinessException invalidSession() {
        return new BusinessException(
                ErrorCode.INVALID_SESSION, "The login session is invalid or expired.");
    }

    private String generatedStudentUsername() {
        return "student_" + UUID.randomUUID().toString().replace("-", "");
    }

    private BusinessException invalidGoogleCredential() {
        return new BusinessException(
                ErrorCode.INVALID_GOOGLE_CREDENTIAL,
                "The Google sign-in credential is invalid.");
    }

    private String buildDeactivationMessage(UserAccount account) {
        com.codegym.aiplanning.entity.auth.DeactivationReasonCode code = account.getDeactivationReasonCode();
        String publicReason = account.getDeactivationPublicReason();
        
        if (code == com.codegym.aiplanning.entity.auth.DeactivationReasonCode.OTHER && publicReason != null && !publicReason.isBlank()) {
            return "Tài khoản của bạn đã bị vô hiệu hóa. Lý do: " + publicReason;
        }
        
        String translated = code != null ? code.getDisplayName() : "Không xác định";
        return "Tài khoản của bạn đã bị vô hiệu hóa. Lý do: " + translated;
    }

    private record AccessCredential(UUID sessionId, String tokenId, Instant expiresAt) {}

    private record RevokedSession(UUID id, Instant expiresAt) {}

    private record LogoutResult(Set<RevokedSession> sessions, Instant revokedAt) {}

    private record RotationResult(
            boolean revoked,
            UUID sessionId,
            Instant sessionExpiresAt,
            UserAccount account,
            Instant accessIssuedAt,
            String rawRefreshToken) {

        static RotationResult revoked(UUID sessionId, Instant expiresAt) {
            return new RotationResult(true, sessionId, expiresAt, null, null, null);
        }

        static RotationResult rotated(
                UUID sessionId,
                Instant expiresAt,
                UserAccount account,
                Instant accessIssuedAt,
                String rawRefreshToken) {
            return new RotationResult(
                    false,
                    sessionId,
                    expiresAt,
                    account,
                    accessIssuedAt,
                    rawRefreshToken);
        }
    }
}
