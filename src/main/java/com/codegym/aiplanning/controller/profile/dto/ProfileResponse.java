package com.codegym.aiplanning.controller.profile.dto;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        UUID id,
        String email,
        UserRole role,
        AccountStatus status,
        ProfileDetails profile) {

    public static ProfileResponse from(UserAccount account, UserProfile profile) {
        return new ProfileResponse(
                account.getId(),
                account.getEmail(),
                account.getRole(),
                account.getStatus(),
                profile == null ? null : ProfileDetails.from(profile));
    }

    public record ProfileDetails(
            String displayName,
            String timeZone,
            String locale,
            int defaultDailyMinutes,
            boolean setupCompleted,
            Instant setupCompletedAt) {

        static ProfileDetails from(UserProfile profile) {
            return new ProfileDetails(
                    profile.getDisplayName(),
                    profile.getTimeZone(),
                    profile.getLocale(),
                    profile.getDefaultDailyMinutes(),
                    profile.isSetupCompleted(),
                    profile.getSetupCompletedAt());
        }
    }
}
