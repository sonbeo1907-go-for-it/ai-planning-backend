package com.codegym.aiplanning.service.profile.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.profile.dto.ProfileResponse;
import com.codegym.aiplanning.controller.profile.dto.CompleteProfileSetupRequest;
import com.codegym.aiplanning.controller.profile.dto.UpdateProfileRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.profile.ProfileService;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuditLogService auditLogService;

    public ProfileServiceImpl(
            UserAccountRepository userAccountRepository,
            UserProfileRepository userProfileRepository,
            AuditLogService auditLogService) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(UUID userId) {
        UserAccount account = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required."));
        UserProfile profile = account.getRole() == UserRole.USER ? requireProfile(userId) : null;
        return ProfileResponse.from(account, profile);
    }

    @Override
    @Transactional
    public ProfileResponse completeInitialSetup(
            UUID userId, CompleteProfileSetupRequest request) {
        UserAccount account = requireUserAccountForUpdate(userId);
        UserProfile profile = requireProfileForUpdate(userId);
        boolean wasCompleted = profile.isSetupCompleted();

        profile.completeSetup(
                normalizeDisplayName(request.displayName()),
                normalizeTimeZone(request.timeZone()),
                normalizeLocale(request.locale()),
                request.defaultDailyMinutes(),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        auditLogService.logAction(
                userId,
                account.getEmail(),
                wasCompleted
                        ? AuditEventAction.PROFILE_UPDATED
                        : AuditEventAction.PROFILE_SETUP_COMPLETED,
                "UserProfile",
                profile.getId().toString());
        return ProfileResponse.from(account, profile);
    }

    @Override
    @Transactional
    public ProfileResponse updateCurrentProfile(UUID userId, UpdateProfileRequest request) {
        UserAccount account = requireUserAccountForUpdate(userId);
        String timeZone = normalizeTimeZone(request.timeZone());
        String locale = normalizeLocale(request.locale());
        UserProfile profile = requireProfileForUpdate(userId);
        if (!profile.isSetupCompleted()) {
            throw new BusinessException(
                    ErrorCode.PROFILE_SETUP_REQUIRED,
                    "Complete the first-access profile setup before editing the profile.");
        }

        profile.update(
                normalizeDisplayName(request.displayName()),
                timeZone,
                locale,
                request.defaultDailyMinutes());
        auditLogService.logAction(
                userId,
                account.getEmail(),
                AuditEventAction.PROFILE_UPDATED,
                "UserProfile",
                profile.getId().toString());

        return ProfileResponse.from(account, profile);
    }

    private UserProfile requireProfile(UUID userId) {
        return userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "The user profile is not initialized."));
    }

    private UserAccount requireUserAccountForUpdate(UUID userId) {
        UserAccount account = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required."));
        if (account.getRole() != UserRole.USER) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Only personal USER accounts have a personal profile.");
        }
        return account;
    }

    private UserProfile requireProfileForUpdate(UUID userId) {
        return userProfileRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "The user profile is not initialized."));
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        if (displayName.isBlank()) {
            throw invalidProfileField("Display name must not be blank.");
        }
        return displayName.trim();
    }

    private String normalizeTimeZone(String timeZone) {
        if (timeZone == null) {
            return null;
        }
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (DateTimeException exception) {
            throw invalidProfileField("Time zone must be a valid IANA zone ID.");
        }
    }

    private String normalizeLocale(String locale) {
        if (locale == null) {
            return null;
        }
        try {
            Locale parsed = new Locale.Builder().setLanguageTag(locale.trim()).build();
            if (parsed.getLanguage().isBlank()) {
                throw invalidProfileField("Locale must be a valid BCP 47 language tag.");
            }
            return parsed.toLanguageTag();
        } catch (IllformedLocaleException exception) {
            throw invalidProfileField("Locale must be a valid BCP 47 language tag.");
        }
    }

    private BusinessException invalidProfileField(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
